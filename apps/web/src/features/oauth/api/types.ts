import type { ApiKeyScope } from "../../settings/api/types";

/** Mirrors the API's `OAuthClientSource`: a metadata document, a dynamic registration, or one we seeded. */
export type OAuthClientKind = "CIMD" | "DCR" | "SEEDED";

/** A workspace the consent screen offers; `eligible` is false where the caller is a client representative only. */
export interface ConsentWorkspace {
  id: string;
  name: string;
  eligible: boolean;
}

/** `GET /oauth/consent-context`: who is asking, for what, and into which workspace. */
export interface ConsentContext {
  clientId: string;
  clientName: string;
  clientKind: OAuthClientKind;
  /** For a metadata document, the host it was read from. */
  clientHost: string | null;
  verified: boolean;
  clientUri: string | null;
  logoUri: string | null;
  redirectHost: string;
  requestedScopes: ApiKeyScope[];
  workspaces: ConsentWorkspace[];
}

/** What draws an app's tile and trust pill, on the consent screen and in Settings → Connected AI apps alike. */
export type ClientIdentity = Pick<ConsentContext, "clientName" | "clientKind" | "clientHost" | "verified" | "logoUri">;

/** A stored request waiting for its consent: the `state` the consent is posted with. */
export interface PendingConsent {
  clientId: string;
  state: string;
  requestedScopes: ApiKeyScope[];
  workspaceId: string | null;
}

/** Where the authorization server sends the browser next — the client's redirect URI, a code or an error on it. */
export interface ConsentRedirect {
  redirectUri: string;
}
