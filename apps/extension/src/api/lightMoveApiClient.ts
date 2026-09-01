import type { ApiError } from "./types";

/**
 * The one place that speaks HTTP to the workspace, and deliberately free of every `chrome.*` API: it
 * is handed a token and a way to renew one, so it cannot reach storage and cannot break the rule that
 * only the worker holds the session.
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
  method?: "GET" | "POST" | "DELETE";
  body?: unknown;
}

export interface LightMoveApiClient {
  request<T>(path: string, options?: ApiRequestOptions): Promise<T>;
}

export function createLightMoveApiClient(options: ApiClientOptions): LightMoveApiClient {
  const send = async (path: string, request: ApiRequestOptions, token: string | null) => {
    const url = new URL(`${options.baseOrigin}/api/v1${path}`);

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
