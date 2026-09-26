import type { User, WorkspaceSummary } from "../../features/auth/api/types";

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
    emailDomain: "nextwebspark.com",
    roles: ["ADMIN"],
    joinedAt: "2026-03-14T09:00:00Z",
    company: null,
    companySize: null,
    primaryRegion: null,
    teamFocus: null,
    ...overrides,
  };
}

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
    locale: "en",
    platformActions: [],
    pendingInvitations: [],
    workspaces: workspace ? [workspace] : [],
    ...overrides,
    workspace,
  };
}
