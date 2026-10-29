import type { User, UserDateTime, WorkspaceSummary } from "../../features/auth/api/types";
import type { WorkspaceDateTime } from "../../features/workspace/api/types";

/**
 * A signed-in user for tests, so a new field on `User` is added here once rather than in every
 * fixture that builds one by hand. `workspace` is also listed in `workspaces` unless overridden —
 * the server never answers a session workspace the user is not a member of.
 */
export function aWorkspace(overrides: Partial<WorkspaceSummary> = {}): WorkspaceSummary {
  return {
    id: "w1",
    name: "NextWebSpark Search",
    slug: "nextwebspark-search",
    logoMark: "N",
    mode: "COMPANY",
    emailDomain: "nextwebspark.com",
    roles: ["ADMIN"],
    joinedAt: "2026-03-14T09:00:00Z",
    company: null,
    ...overrides,
  };
}

/** A person who chose nothing of their own, in a workspace on the standard Dubai clock. */
export const FOLLOWING_DATE_TIME: UserDateTime = {
  effective: { timeZone: "Asia/Dubai", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" },
  own: { timeZone: null, dateFormat: null, timeFormat: null },
  workspace: { timeZone: "Asia/Dubai", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" },
};

/** Settings → General's Date & time where the admin chose nothing, in a workspace with no country. */
export const WORKSPACE_DATE_TIME: WorkspaceDateTime = {
  timeZone: null,
  dateFormat: null,
  timeFormat: null,
  suggestedTimeZone: "Asia/Dubai",
  suggestedFrom: null,
  membersFollowing: 1,
};

export function aUser(overrides: Partial<User> = {}): User {
  const workspace = "workspace" in overrides ? (overrides.workspace ?? null) : aWorkspace();
  return {
    id: "u1",
    email: "alok@nextwebspark.com",
    fullName: "Alok Kumar",
    title: null,
    avatarUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    dateTime: FOLLOWING_DATE_TIME,
    locale: "en",
    platformActions: [],
    pendingInvitations: [],
    workspaces: workspace ? [workspace] : [],
    ...overrides,
    workspace,
  };
}
