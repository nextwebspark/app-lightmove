import { describe, expect, it } from "vitest";
import { homeFor } from "./homeFor";

/** Where a signed-in user belongs, from what is true of them — a workspace outranks everything. */
describe("homeFor", () => {
  const base = { emailVerified: true, workspace: null, pendingInvitations: [] as unknown[] };

  it("sends nobody to sign in", () => {
    expect(homeFor(null)).toBe("/login");
  });

  it("lands anyone in a workspace on the projects list, invitations or not", () => {
    expect(homeFor({ ...base, workspace: { roles: ["MEMBER"] }, pendingInvitations: [{}] })).toBe("/");
  });

  it("holds an unverified user at the verify step before anything else", () => {
    expect(homeFor({ ...base, emailVerified: false, pendingInvitations: [{}] })).toBe("/signup/verify-email");
  });

  it("routes a verified, unplaced invitee to accept, and anyone else to the organisation step", () => {
    expect(homeFor({ ...base, pendingInvitations: [{}] })).toBe("/auth/accept-invite");
    expect(homeFor(base)).toBe("/signup/workspace");
  });
});
