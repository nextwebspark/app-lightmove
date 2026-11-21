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

  it("lands on Positions for someone who chose it for that workspace, and on Home in another", () => {
    localStorage.setItem("lm-home:u1:w1", JSON.stringify({ landing: "positions" }));
    expect(homeFor({ ...base, workspace: { id: "w1", roles: ["MEMBER"] } })).toBe("/positions");
    expect(homeFor({ ...base, workspace: { id: "w2", roles: ["MEMBER"] } })).toBe("/");
    localStorage.clear();
  });

  it("holds an unverified user at the verify step before anything else", () => {
    expect(homeFor({ ...base, emailVerified: false, pendingInvitations: [{}] })).toBe("/signup/verify-email");
  });

  it("routes a verified, unplaced invitee to accept, and anyone else to the organisation step", () => {
    expect(homeFor({ ...base, pendingInvitations: [{}] })).toBe("/auth/accept-invite");
    expect(homeFor(base)).toBe("/signup/workspace");
  });
});
