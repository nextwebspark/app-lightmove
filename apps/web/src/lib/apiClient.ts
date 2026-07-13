import type { ApiError } from "../features/auth/api/types";

/**
 * The only way this app talks to the API.
 *
 * It exists to hold three things in one place that would otherwise be scattered across every call
 * site and get one of them wrong:
 *
 *   1. The access token lives in a module variable — in memory, never in localStorage. A single
 *      compromised npm dependency can read localStorage; it cannot read a closure. The refresh token
 *      is in an httpOnly cookie script cannot touch at all.
 *   2. A 401 triggers exactly one refresh, no matter how many requests hit it at once, and the
 *      requests that were waiting are retried with the new token.
 *   3. The CSRF header is attached to the cookie-authenticated routes.
 */

const API = "/api/v1";

/** Not exported. The only way in or out is through the functions below. */
let accessToken: string | null = null;

/** Called on logout, and whenever a refresh fails for good. */
let onSessionLost: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function onSessionExpired(handler: () => void): void {
  onSessionLost = handler;
}

/** Thrown for any non-2xx. Carries the server's own ProblemDetail so a form can render field errors. */
export class ApiRequestError extends Error {
  // Declared, not a constructor parameter property: `erasableSyntaxOnly` requires that stripping the
  // types leaves valid JavaScript, and a parameter property is TypeScript that emits real code.
  readonly problem: ApiError;

  constructor(problem: ApiError) {
    super(problem.detail);
    this.name = "ApiRequestError";
    this.problem = problem;
  }

  get code(): string {
    return this.problem.code;
  }

  get fieldErrors(): Record<string, string> {
    return this.problem.fieldErrors ?? {};
  }
}

/**
 * The single in-flight refresh.
 *
 * Without this, a page that fires five requests on mount would, on an expired token, fire five
 * refreshes. Four of them would present a token the first had already rotated away — which the server
 * correctly reads as a stolen token being replayed, and it would revoke the entire session. The client
 * would log itself out by being enthusiastic. So: one refresh, everyone else waits for it.
 */
let refreshInFlight: Promise<string> | null = null;

/**
 * The same problem again, one level up: **other tabs**.
 *
 * `refreshInFlight` dedupes within one page. It cannot see a second tab, which is a second JS context
 * with its own module state — and both tabs share one cookie jar. Open the app twice and both boot,
 * both read the same refresh cookie, and both POST /auth/refresh before either response has rotated it.
 * One wins. The other presents a token that has just been rotated away, the server correctly reads that
 * as a replayed stolen token, and **revokes the whole family** — signing you out of both tabs, for
 * doing nothing but opening a second tab.
 *
 * The Web Locks API is cross-tab, which is exactly the scope needed. Whoever holds the lock refreshes;
 * the other tab waits, and by the time it runs, the rotated cookie is already in the shared jar — so
 * its own refresh presents the *new* token and rotates legitimately.
 *
 * Not a fallback worth agonising over: `navigator.locks` is in every browser we support. Where it is
 * missing we run unlocked, which is exactly today's behaviour.
 */
function withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
  return navigator.locks?.request
    ? navigator.locks.request("lm-refresh", work)
    : work();
}

async function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  refreshInFlight = withRefreshLock(async () => {
    try {
      // The refresh endpoint is CSRF-protected, because it authenticates with a cookie the browser
      // attaches automatically — including on a request another site provoked. The double-submit
      // token proves the request came from our own JavaScript, which can read the cookie; a cross-site
      // attacker can cause the cookie to be sent but cannot read it.
      const csrf = await ensureCsrfToken();

      const response = await fetch(`${API}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: csrf ? { "X-XSRF-TOKEN": csrf } : {},
      });

      if (!response.ok) {
        throw new ApiRequestError(await problemFrom(response));
      }

      const body = (await response.json()) as { accessToken: string };
      accessToken = body.accessToken;
      return body.accessToken;
    } catch (error) {
      accessToken = null;
      onSessionLost?.();
      throw error;
    }
  }).finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

/**
 * Restores a session on a cold page load.
 *
 * The access token was in memory and a page reload wiped it — but the refresh cookie survived, so we
 * can mint a new one. This is what lets a hard refresh keep you logged in without ever putting a
 * long-lived credential somewhere script can read.
 *
 * Returns null when there is no session, which is the ordinary case for a first-time visitor and not
 * an error.
 */
export async function restoreSession(): Promise<string | null> {
  try {
    return await refreshAccessToken();
  } catch {
    return null;
  }
}

/**
 * Spring writes the XSRF-TOKEN cookie as a side effect of handling a request against the auth chain.
 * If we do not have it yet, ask for it.
 */
async function ensureCsrfToken(): Promise<string | null> {
  const existing = readCookie("XSRF-TOKEN");
  if (existing) {
    return existing;
  }

  await fetch(`${API}/auth/csrf`, { credentials: "include" });
  return readCookie("XSRF-TOKEN");
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[2]) : null;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  /** Set for endpoints that must not attempt a refresh — login and signup have no session yet. */
  anonymous?: boolean;
  /** Set for the cookie-authenticated routes, which need the double-submit header. */
  withCsrf?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, anonymous = false, withCsrf = false } = options;

  const send = async (token: string | null): Promise<Response> => {
    const headers: Record<string, string> = {};
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    if (withCsrf) {
      const csrf = await ensureCsrfToken();
      if (csrf) {
        headers["X-XSRF-TOKEN"] = csrf;
      }
    }

    return fetch(`${API}${path}`, {
      method,
      headers,
      // Always: the refresh cookie must ride along on the auth routes, and sending it elsewhere is
      // harmless because the cookie is path-scoped and the browser will not attach it anyway.
      credentials: "include",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let response = await send(anonymous ? null : accessToken);

  // One retry, and only for an expired token. A 401 on an anonymous request means bad credentials,
  // not a stale session, and refreshing would be nonsense.
  if (response.status === 401 && !anonymous) {
    try {
      const fresh = await refreshAccessToken();
      response = await send(fresh);
    } catch {
      // The refresh failed: the session is genuinely over. Fall through and report the original 401
      // rather than the refresh's error, because the caller asked about *their* request.
    }
  }

  if (!response.ok) {
    throw new ApiRequestError(await problemFrom(response));
  }

  // 204 No Content — logout, reject. There is no body to parse, and parsing one would throw.
  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * Turns any failure into an ApiError, including the ones that are not ours.
 *
 * A gateway timeout or a proxy error is HTML, not a ProblemDetail. Trying to parse it as JSON throws,
 * and the user would see a JSON syntax error where they should see "something went wrong".
 */
async function problemFrom(response: Response): Promise<ApiError> {
  try {
    const parsed = (await response.json()) as Partial<ApiError>;
    if (parsed?.code) {
      return parsed as ApiError;
    }
  } catch {
    // Not JSON. Fall through.
  }

  return {
    code: response.status >= 500 ? "INTERNAL_ERROR" : "UNKNOWN_ERROR",
    detail:
      response.status >= 500
        ? "Something went wrong on our end. Please try again."
        : "That request could not be completed.",
    status: response.status,
    correlationId: response.headers.get("X-Correlation-Id") ?? "none",
  };
}
