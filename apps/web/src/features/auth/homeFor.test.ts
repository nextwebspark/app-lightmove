import { describe, expect, it } from "vitest";
import { homeFor } from "./homeFor";

/** Where a signed-in user belongs, from what is true of them — a workspace outranks everything. */
describe("homeFor", () => {
  const base = { id: "u1", emailVerified: true, workspace: null, pendingInvitations: [] as unknown[] };

  it("sends nobody to sign in", () => {
    expect(homeFor(null)).toBe("/login");
  });

  it("lands anyone in a workspace on Home, invitations or not", () => {
    expect(homeFor({ ...base, workspace: { id: "w1", roles: ["MEMBER"] }, pendingInvitations: [{}] })).toBe("/");
  });

  it("lands on the start page the session's workspace carries, and on Home when it is no longer theirs", () => {
    const workspace = { id: "w1", roles: ["MEMBER"] };
    const startPage = { kind: "POSITION", dashboardId: null, projectId: "p1", section: "OUTREACH", available: true } as const;
    expect(homeFor({ ...base, workspace, startPage })).toBe("/projects/p1/outreach");
    expect(homeFor({ ...base, workspace, startPage: { ...startPage, available: false } })).toBe("/");
  });

  it("holds an unverified user at the verify step before anything else", () => {
    expect(homeFor({ ...base, emailVerified: false, pendingInvitations: [{}] })).toBe("/signup/verify-email");
  });

  it("routes a verified, unplaced invitee to accept, and anyone else to the organisation step", () => {
    expect(homeFor({ ...base, pendingInvitations: [{}] })).toBe("/auth/accept-invite");
    expect(homeFor(base)).toBe("/signup/workspace");
  });
});
