import { request } from "../../../lib/apiClient";
import type { ApiKeyScope } from "../../settings/api/types";
import type { ConsentContext, ConsentRedirect, PendingConsent } from "./types";

const AUTHORIZE = "/oauth/authorize";

export function getConsentContext(clientId: string, redirectUri: string | null, scope: string | null) {
  const query = new URLSearchParams({ client_id: clientId });
  if (redirectUri) query.set("redirect_uri", redirectUri);
  if (scope) query.set("scope", scope);
  return request<ConsentContext>(`/oauth/consent-context?${query}`);
}

/**
 * The AI client's own request, sent again with the session's bearer and the chosen workspace. The server stores it
 * and redirects to the pending consent, which the fetch follows; a request it refuses outright comes back as the
 * client's redirect with the error on it.
 */
export function storeAuthorizationRequest(
  clientRequest: URLSearchParams,
  workspaceId: string,
): Promise<PendingConsent | ConsentRedirect> {
  const body = new URLSearchParams(clientRequest);
  body.set("workspace_id", workspaceId);
  return request<PendingConsent | ConsentRedirect>(AUTHORIZE, { method: "POST", body });
}

/** The consent itself; no scope at all is a denial. */
export function answerConsent(clientId: string, state: string, scopes: readonly ApiKeyScope[]) {
  const body = new URLSearchParams({ client_id: clientId, state });
  scopes.forEach((scope) => body.append("scope", scope));
  return request<ConsentRedirect>(AUTHORIZE, { method: "POST", body });
}
