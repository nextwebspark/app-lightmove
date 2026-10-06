import type { OAuthClientKind } from "../../oauth/api/types";

/** Settings → Integrations' contract, hand-mirrored from the API like the other modules'. */

export type IntegrationProvider = "GOOGLE" | "MICROSOFT" | "ZOOM";

/** Whose OAuth app a provider is connected through: Uncava's shared one, or the workspace's own. */
export type CredentialMode = "SHARED" | "OWN";

/** One provider. The client secret is write-only: `secretSet` says one is held, and nothing carries it back. */
export interface WorkspaceIntegration {
  provider: IntegrationProvider;
  mode: CredentialMode;
  clientId: string | null;
  tenantId: string | null;
  /** yyyy-MM-dd, as read off the provider's console. */
  secretExpiresOn: string | null;
  secretSet: boolean;
  sharedOffered: boolean;
  redirectUri: string;
  scopes: string[];
  /** Microsoft's one-time approval link for Uncava's app; null elsewhere. */
  adminConsentUrl: string | null;
  ownAppGuideUrl: string | null;
  sharedAppGuideUrl: string | null;
  updatedAt: string | null;
  /** When an admin came back from Microsoft's admin-consent link having approved Uncava's app; Microsoft only. */
  adminConsentedAt: string | null;
  adminConsentTenantId: string | null;
}

export interface WorkspaceIntegrations {
  providers: WorkspaceIntegration[];
  /** False where the deployment has no key to encrypt a workspace's own secret with. */
  ownAppsOffered: boolean;
  /** False where the deployment has no Recall account, so calendars are read directly. */
  recallOffered: boolean;
}

/** On `OWN`, a blank `clientSecret` keeps the one already held for the same client ID. */
export interface UpdateIntegrationRequest {
  mode: CredentialMode;
  clientId?: string;
  clientSecret?: string;
  tenantId?: string;
  secretExpiresOn?: string | null;
}

/** Settings → API keys. A key is `PERSONAL` (its owner's reach) or `SERVICE` (the whole workspace's). */
export type ApiKeyKind = "PERSONAL" | "SERVICE";

/** As of the read; "expires soon" is the screen's own reading of `expiresAt`. */
export type ApiKeyStatus = "ACTIVE" | "EXPIRED" | "REVOKED";

export type ApiKeyScope =
  | "projects:read"
  | "companies:read"
  | "candidates:read"
  | "candidates.contacts:read"
  | "candidates.compensation:read"
  | "mcp:use";

/** Never the secret: `tokenHint` is its first and last characters, which cannot be used. */
export interface ApiKey {
  id: string;
  name: string;
  kind: ApiKeyKind;
  status: ApiKeyStatus;
  tokenHint: string;
  scopes: ApiKeyScope[];
  /** Null on a workspace key. */
  ownerUserId: string | null;
  ownerName: string | null;
  createdByName: string | null;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  lastUsedIp: string | null;
  revokedAt: string | null;
  revokedByName: string | null;
  revokedReason: string | null;
}

export interface CreateApiKeyRequest {
  name: string;
  kind: ApiKeyKind;
  scopes: ApiKeyScope[];
  expiresInDays: number;
}

/** The one answer that carries the secret; nothing returns it again. */
export interface CreatedApiKey {
  key: ApiKey;
  secret: string;
}

/** An AI app connected over MCP: one person, one workspace, the scopes they ticked. Carries no token. */
export interface OAuthGrant {
  id: string;
  clientId: string;
  clientName: string;
  clientKind: OAuthClientKind;
  /** For a metadata document, the host it was read from. */
  clientHost: string | null;
  verified: boolean;
  /** Where the app sends the browser back to — what the consent screen named. */
  redirectHost: string | null;
  logoUri: string | null;
  scopes: ApiKeyScope[];
  ownerUserId: string;
  ownerName: string | null;
  connectedAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
}
