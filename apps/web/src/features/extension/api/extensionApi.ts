import { request } from "../../../lib/apiClient";

/**
 * The one call the extension pairing page makes.
 *
 * `withCsrf`, like every other write on the auth chain: this route is bearer-authenticated so CSRF
 * could not reach it anyway, but it is not in `SecurityConfig`'s exemption list and sending the header
 * is what keeps it out of it — the two routes that *are* exempt are the extension's own, which carry
 * no cookie at all.
 */
export interface ExtensionSession {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  user: { id: string; fullName: string; email: string };
}

export function pairExtension(): Promise<ExtensionSession> {
  return request<ExtensionSession>("/auth/extension/tokens", { method: "POST", withCsrf: true });
}
