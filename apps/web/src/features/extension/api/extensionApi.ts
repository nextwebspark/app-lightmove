import { request } from "../../../lib/apiClient";

export interface ExtensionSession {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  user: { id: string; fullName: string; email: string };
}

/** Mints the extension's own session. `withCsrf`, like every other write on the auth chain. */
export function pairExtension(): Promise<ExtensionSession> {
  return request<ExtensionSession>("/auth/extension/tokens", { method: "POST", withCsrf: true });
}
