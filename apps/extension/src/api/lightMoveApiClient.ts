import type { ApiError } from "./types";

/**
 * The one place that speaks HTTP to the workspace.
 *
 * Deliberately free of every `chrome.*` API: it is handed an access token and a way to get a fresh
 * one, and knows nothing about where either comes from. That keeps it unit-testable without a browser
 * and keeps the rule that only the service worker touches the session in one place — this file cannot
 * break it because it cannot reach storage.
 */

/** Thrown for any non-2xx, carrying the server's own problem so the popup can switch on `code`. */
export class ApiRequestError extends Error {
  readonly problem: ApiError;

  constructor(problem: ApiError) {
    super(problem.detail);
    this.name = "ApiRequestError";
    this.problem = problem;
  }

  get code(): string {
    return this.problem.code;
  }
}

export interface ApiClientOptions {
  /** Where the workspace lives, without a trailing slash. */
  baseOrigin: string;
  /** The token to send now. */
  currentAccessToken: () => Promise<string | null>;
  /** Called once on a 401, to get a token to retry with. Null gives up. */
  renewAccessToken: () => Promise<string | null>;
}

export interface ApiRequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Record<string, string | null | undefined>;
}

export interface LightMoveApiClient {
  request<T>(path: string, options?: ApiRequestOptions): Promise<T>;
}

export function createLightMoveApiClient(options: ApiClientOptions): LightMoveApiClient {
  const send = async (path: string, request: ApiRequestOptions, token: string | null) => {
    const url = new URL(`${options.baseOrigin}/api/v1${path}`);
    for (const [key, value] of Object.entries(request.query ?? {})) {
      if (value !== null && value !== undefined && value !== "") {
        url.searchParams.set(key, value);
      }
    }

    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    if (request.body !== undefined) {
      headers["Content-Type"] = "application/json";
    }

    return fetch(url, {
      method: request.method ?? "GET",
      headers,
      // Never "include". The extension has no cookie on this origin and wants none: its credential is
      // the bearer token, and asking the browser to attach cookies would only reintroduce the
      // cross-origin cookie problem the pairing flow exists to avoid.
      credentials: "omit",
      body: request.body === undefined ? undefined : JSON.stringify(request.body),
    });
  };

  return {
    async request<T>(path: string, request: ApiRequestOptions = {}): Promise<T> {
      let response = await send(path, request, await options.currentAccessToken());

      // One retry, for an expired token only. A second 401 means the session is genuinely over and
      // retrying again would just spend refresh tokens against a server that has already said no.
      if (response.status === 401) {
        const renewed = await options.renewAccessToken();
        if (renewed) {
          response = await send(path, request, renewed);
        }
      }

      if (!response.ok) {
        throw new ApiRequestError(await toProblem(response));
      }
      return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
    },
  };
}

/**
 * A non-2xx that is not a ProblemDetail still has to become one — a proxy's HTML error page or a
 * dropped connection would otherwise surface in the popup as "Unexpected token < in JSON".
 */
async function toProblem(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiError>;
    if (body && typeof body.code === "string") {
      return { ...body, status: response.status } as ApiError;
    }
  } catch {
    // Falls through to the generic problem below.
  }
  return {
    code: "UNEXPECTED_RESPONSE",
    detail: `The workspace answered ${response.status}.`,
    status: response.status,
  };
}
