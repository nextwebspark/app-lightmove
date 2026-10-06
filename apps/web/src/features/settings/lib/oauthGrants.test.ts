import { describe, expect, it } from "vitest";
import { formatInstantDate } from "../../../lib/format";
import type { OAuthGrant } from "../api/types";
import { grantHostOf, grantMetaOf, mcpServerUrl } from "./oauthGrants";

const grant = (overrides: Partial<OAuthGrant> = {}): OAuthGrant => ({
  id: "g1",
  clientId: "c1",
  clientName: "Claude",
  clientKind: "SEEDED",
  clientHost: null,
  verified: true,
  redirectHost: "claude.ai",
  logoUri: null,
  scopes: ["projects:read"],
  ownerUserId: "u1",
  ownerName: "Alok Kumar",
  connectedAt: "2026-09-12T09:00:00Z",
  lastUsedAt: null,
  expiresAt: null,
  ...overrides,
});

describe("grantHostOf", () => {
  it("names a document's host, a registration as self-made, and otherwise where it returns to", () => {
    expect(grantHostOf(grant({ clientKind: "CIMD", clientHost: "chatgpt.com" }))).toBe("chatgpt.com");
    expect(grantHostOf(grant({ clientKind: "DCR", redirectHost: "127.0.0.1" }))).toBe("registered itself · 127.0.0.1");
    expect(grantHostOf(grant())).toBe("claude.ai");
  });
});

describe("grantMetaOf", () => {
  const now = new Date("2026-10-06T12:00:00Z");
  const connected = formatInstantDate("2026-09-12T09:00:00Z");

  it("says when it connected and was last used, and whose it is only on the All view", () => {
    expect(grantMetaOf(grant({ lastUsedAt: "2026-10-06T10:00:00Z" }), false, now)).toBe(
      `connected ${connected} · last used 2 hours ago`,
    );
    expect(grantMetaOf(grant({ ownerName: null }), true, now)).toBe(
      `A former member · connected ${connected} · never used`,
    );
  });
});

describe("mcpServerUrl", () => {
  it("is the MCP endpoint on this deployment's own origin", () => {
    expect(mcpServerUrl("https://beta.uncava.com")).toBe("https://beta.uncava.com/api/v1/mcp");
  });
});
