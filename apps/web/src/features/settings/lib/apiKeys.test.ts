import { describe, expect, it } from "vitest";
import type { ApiKey } from "../api/types";
import { readsSomething, scopeChipClass, statusOf } from "./apiKeys";

const NOW = new Date("2026-10-05T12:00:00Z");

function keyExpiring(expiresAt: string, status: ApiKey["status"] = "ACTIVE"): ApiKey {
  return {
    id: "k1", name: "Script", kind: "PERSONAL", status, tokenHint: "uncava_pat_…", scopes: ["projects:read"],
    ownerUserId: "u1", ownerName: null, createdByName: null, createdAt: "2026-09-01T00:00:00Z", expiresAt,
    lastUsedAt: null, lastUsedIp: null, revokedAt: null, revokedByName: null, revokedReason: null,
  };
}

/** A key's status as the row reads it: the server's, sharpened by how close its expiry is. */
describe("statusOf", () => {
  it("reads active, then a countdown from two weeks out", () => {
    expect(statusOf(keyExpiring("2026-12-01T12:00:00Z"), NOW)).toEqual({ label: "Active", tone: "active" });
    expect(statusOf(keyExpiring("2026-10-06T12:00:00Z"), NOW)).toEqual({ label: "Expires in 1 day", tone: "soon" });
  });

  it("reads a key past its expiry as expired even while the server still says active", () => {
    expect(statusOf(keyExpiring("2026-10-05T11:00:00Z"), NOW)).toEqual({ label: "Expired", tone: "expired" });
  });

  it("keeps the server's revoked and expired", () => {
    expect(statusOf(keyExpiring("2026-12-01T12:00:00Z", "REVOKED"), NOW).tone).toBe("revoked");
    expect(statusOf(keyExpiring("2026-12-01T12:00:00Z", "EXPIRED"), NOW).tone).toBe("expired");
  });
});

describe("mcp:use", () => {
  it("reads nothing by itself, so a key needs another scope beside it", () => {
    expect(readsSomething(["mcp:use"])).toBe(false);
    expect(readsSomething(["mcp:use", "projects:read"])).toBe(true);
  });

  it("is drawn in the accent, personal data in the off-limits tone, the rest plain", () => {
    expect(scopeChipClass("mcp:use")).toContain("u-accent");
    expect(scopeChipClass("candidates.contacts:read")).toContain("u-offlimits");
    expect(scopeChipClass("projects:read")).toContain("u-text2");
  });
});
