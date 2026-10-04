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
