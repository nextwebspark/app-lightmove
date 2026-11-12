import type { ApiError, AuthResponse, User } from "../features/auth/api/types";
import { readCookie } from "./cookies";
import { createSseParser, type SseEvent } from "./sse";

/**
 * The only way this app talks to the API: the access token in memory only (never localStorage), one
 * shared refresh per 401 with waiting requests retried, and the CSRF header with one re-fetch on refusal.
 */

const API = "/api/v1";

/** Not exported. The only way in or out is through the functions below. */
let accessToken: string | null = null;

/** Called on logout, and whenever a refresh fails for good. */
let onSessionLost: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function onSessionExpired(handler: () => void): void {
  onSessionLost = handler;
}

/** Called when a refresh answers a session in a different workspace from the one this tab was in. */
let onWorkspaceLeft: ((user: User) => void) | null = null;

export function onWorkspaceMoved(handler: (user: User) => void): void {
  onWorkspaceLeft = handler;
}

/** Read to notice that a refresh moved the session, never to authorise anything: the server decides. */
function workspaceClaimOf(token: string | null): string | null {
  const payload = token?.split(".")[1];
  if (!payload) return null;
  try {
    const claims = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/"))) as { wsId?: unknown };
    return typeof claims.wsId === "string" ? claims.wsId : null;
  } catch {
    return null;
  }
}

/**
 * Told of every refused request, before its caller is: how a refusal any screen can meet opens one sheet.
 * Every status, not only billing's, so this module stays ignorant of which codes some feature cares about.
 */
type RefusalListener = (error: ApiRequestError) => void;
const refusalListeners = new Set<RefusalListener>();

export function onRequestRefused(listener: RefusalListener): () => void {
  refusalListeners.add(listener);
  return () => refusalListeners.delete(listener);
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
 * The single in-flight refresh: concurrent refreshes would replay a rotated token, which the server
 * reads as theft and revokes the whole session.
 */
let refreshInFlight: Promise<string> | null = null;

/**
 * Cross-tab refresh lock (Web Locks): two tabs share one cookie jar, and racing refreshes replay a rotated
 * token, so the server revokes the whole family. Where `navigator.locks` is missing we run unlocked.
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

  refreshInFlight = withRefreshLock(rotateRefreshCookie).finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

/**
 * The refresh itself, for a caller already holding the lock — `navigator.locks` is not re-entrant, so
 * taking it again from inside would wait on itself forever.
 */
async function rotateRefreshCookie(): Promise<string> {
  try {
    // The refresh endpoint is CSRF-protected, because it authenticates with a cookie the browser
    // attaches automatically — including on a request another site provoked. The double-submit
    // token proves the request came from our own JavaScript, which can read the cookie; a cross-site
    // attacker can cause the cookie to be sent but cannot read it.
    const response = await sendWithCsrf((csrf) =>
      fetch(`${API}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: csrf ? { "X-XSRF-TOKEN": csrf } : {},
      }),
    );

    if (!response.ok) {
      throw new ApiRequestError(await problemFrom(response));
    }

    const body = (await response.json()) as AuthResponse;
    const left = workspaceClaimOf(accessToken);
    accessToken = body.accessToken;
    // A refresh moves the session only when the membership it was in has ended, and the cache and
    // routes on screen still belong to the workspace left.
    if (left && workspaceClaimOf(body.accessToken) !== left && body.user) {
      onWorkspaceLeft?.(body.user);
    }
    return body.accessToken;
  } catch (error) {
    accessToken = null;
    onSessionLost?.();
    throw error;
  }
}

/**
 * `POST /auth/switch-workspace`, under the refresh lock since it rotates the cookie too; the bearer goes
 * along because the server refuses to move a cookie that is not the bearer's. A refused switch changes nothing.
 */
export async function switchWorkspaceSession(workspaceId: string): Promise<AuthResponse> {
  return withRefreshLock(async () => {
    const send = () =>
      sendWithCsrf((csrf) =>
        fetch(`${API}/auth/switch-workspace`, {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
            ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
            ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
          },
          body: JSON.stringify({ workspaceId }),
        }),
      );

    let response = await send();
    if (response.status === 401) {
      await rotateRefreshCookie();
      response = await send();
    }

    if (!response.ok) {
      throw new ApiRequestError(await problemFrom(response));
    }

    const session = (await response.json()) as AuthResponse;
    accessToken = session.accessToken;
    return session;
  });
}

/**
 * Restores a session on a cold page load from the refresh cookie. Null when there is no session — the
 * ordinary case for a first visit, not an error.
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
  return readCookie("XSRF-TOKEN") ?? (await fetchCsrfToken());
}

/**
 * Asks for a token unconditionally, and answers null rather than throwing when the ask does not
 * land — a proxy hiccup or a transient 5xx on this one request must not become the session's
 * problem. The caller sends without the header and lets `sendWithCsrf` recover from the refusal.
 */
async function fetchCsrfToken(): Promise<string | null> {
  try {
    const response = await fetch(`${API}/auth/csrf`, { credentials: "include" });
    if (!response.ok) {
      return null;
    }
  } catch {
    return null;
  }
  return readCookie("XSRF-TOKEN");
}

/**
 * Sends a cookie-authenticated request, re-fetching the CSRF token and retrying once on `CSRF_TOKEN_INVALID`.
 * Without it one failed `GET /auth/csrf` made `/auth/refresh` 403 and signed the user out. Exactly one retry.
 */
async function sendWithCsrf(send: (csrf: string | null) => Promise<Response>): Promise<Response> {
  const response = await send(await ensureCsrfToken());
  if (response.status !== 403) {
    return response;
  }

  // A body can only be read once, so reading it here commits us: either this was the recoverable
  // failure, or the problem we just parsed is the one the caller has to be told about.
  const problem = await problemFrom(response);
  if (problem.code !== "CSRF_TOKEN_INVALID") {
    throw new ApiRequestError(problem);
  }

  return send(await fetchCsrfToken());
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /**
   * Serialised as JSON, unless it is a `FormData` or `URLSearchParams` — a file upload or a form is sent
   * as-is, and the browser writes its `Content-Type` itself. Setting that header by hand omits the boundary the
   * body was built with, and the server then reads the whole part as one unparseable blob.
   */
  body?: unknown;
  /** Set for endpoints that must not attempt a refresh — login and signup have no session yet. */
  anonymous?: boolean;
  /** Set for the cookie-authenticated routes, which need the double-submit header. */
  withCsrf?: boolean;
  signal?: AbortSignal;
}

/**
 * The shared transport: the bearer token, the one refresh retry, and the error shape. Returns the raw
 * response so both the JSON and the file readers below get the same authentication for free.
 */
async function sendWithAuth(path: string, options: RequestOptions): Promise<Response> {
  const { method = "GET", body, anonymous = false, withCsrf = false, signal } = options;

  const dispatch = (token: string | null, csrf: string | null): Promise<Response> => {
    const isRaw = body instanceof FormData || body instanceof URLSearchParams;
    const headers: Record<string, string> = {};
    if (body !== undefined && !isRaw) {
      headers["Content-Type"] = "application/json";
    }
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    if (csrf) {
      headers["X-XSRF-TOKEN"] = csrf;
    }

    return fetch(`${API}${path}`, {
      method,
      headers,
      // Always: the refresh cookie must ride along on the auth routes, and sending it elsewhere is
      // harmless because the cookie is path-scoped and the browser will not attach it anyway.
      credentials: "include",
      body: body === undefined ? undefined : isRaw ? body : JSON.stringify(body),
      signal,
    });
  };

  const send = (token: string | null): Promise<Response> =>
    withCsrf ? sendWithCsrf((csrf) => dispatch(token, csrf)) : dispatch(token, null);

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
    const refused = new ApiRequestError(await problemFrom(response));
    refusalListeners.forEach((listener) => {
      // A listener's own failure must never replace the refusal its caller is about to be told.
      try {
        listener(refused);
      } catch (failure) {
        console.error(failure);
      }
    });
    throw refused;
  }

  return response;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await sendWithAuth(path, options);

  // 204 No Content — logout, reject. There is no body to parse, and parsing one would throw.
  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * For endpoints that answer with a file: a download cannot be an `<a href>` (no bearer on a navigation,
 * and the refresh cookie is auth-path scoped), so fetch the bytes and hand the caller a Blob.
 */
export async function requestBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
  return (await sendWithAuth(path, options)).blob();
}

/**
 * An SSE stream read off fetch's body (EventSource cannot send Authorization), with the one-refresh retry.
 * Resolves on the server's scheduled close, rejects on abort or failure; the caller owns reconnecting.
 */
export async function streamEvents(
  path: string,
  onEvent: (event: SseEvent) => void,
  signal: AbortSignal | undefined,
  options: Pick<RequestOptions, "method" | "body"> = {},
): Promise<void> {
  const response = await sendWithAuth(path, { ...options, signal });
  const reader = response.body?.getReader();
  if (!reader) {
    return;
  }

  const decoder = new TextDecoder();
  const parse = createSseParser();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      return;
    }
    for (const event of parse(decoder.decode(value, { stream: true }))) {
      onEvent(event);
    }
  }
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
