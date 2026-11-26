/** Mirrors the API's `PlatformSubprocessorNoticeResponse`: one notice with what came back, as counts. */
export interface SubprocessorNotice {
  id: string;
  provider: string;
  receives: string;
  purpose: string;
  region: string;
  /** ISO yyyy-MM-dd, a calendar date in UTC. */
  effectiveOn: string;
  listUrl: string;
  inEffect: boolean;
  publishedAt: string;
  /** Null once that account is erased. */
  publishedBy: string | null;
  /** Workspace admins the email reached. */
  emailed: number;
  /** Admins still owed one, not given up: retried until the change takes effect. */
  owed: number;
  objections: number;
}

/** Mirrors `PlatformSubprocessorObjectionResponse`: one workspace's objection. */
export interface SubprocessorObjection {
  workspaceId: string;
  workspaceName: string;
  /** Null once that account is erased. */
  objectedBy: string | null;
  reason: string;
  objectedAt: string;
}

export interface PublishSubprocessorNotice {
  provider: string;
  receives: string;
  purpose: string;
  region: string;
  effectiveOn: string;
  listUrl: string;
}
