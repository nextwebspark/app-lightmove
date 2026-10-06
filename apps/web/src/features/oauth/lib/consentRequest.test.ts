import { describe, expect, it } from "vitest";
import type { ConsentContext } from "../api/types";
import {
  clientSubtitle,
  grantedSummary,
  initialScopes,
  knownClientMarkOf,
  outcomeOf,
  readClientRequest,
  refusalMessage,
} from "./consentRequest";

describe("readClientRequest", () => {
  it("keeps the client's request as it came, less any workspace it tried to choose", () => {
    const read = readClientRequest(
      "?response_type=code&client_id=claude&redirect_uri=https%3A%2F%2Fclaude.ai%2Fcb&scope=projects%3Aread&state=s1" +
        "&code_challenge=abc&code_challenge_method=S256&resource=https%3A%2F%2Fbeta.uncava.com%2Fapi%2Fv1%2Fmcp" +
        "&workspace_id=someone-elses",
    );

    expect(read.kind).toBe("request");
    if (read.kind !== "request") return;
    expect(read.clientId).toBe("claude");
    expect(read.redirectUri).toBe("https://claude.ai/cb");
    expect(read.scope).toBe("projects:read");
    expect(read.params.get("code_challenge")).toBe("abc");
    expect(read.params.get("resource")).toBe("https://beta.uncava.com/api/v1/mcp");
    expect(read.params.has("workspace_id")).toBe(false);
  });

  it("reads the authorization server's error, and a request naming no client as one", () => {
    expect(readClientRequest("?error=invalid_client")).toEqual({ kind: "error", code: "invalid_client" });
    expect(readClientRequest("?scope=projects%3Aread")).toEqual({ kind: "error", code: "invalid_request" });
  });
});

describe("initialScopes", () => {
  it("ticks what was asked for except personal data, which the person opts into", () => {
    expect(
      initialScopes(["candidates.contacts:read", "projects:read", "candidates:read", "candidates.compensation:read"]),
    ).toEqual(["projects:read", "candidates:read"]);
  });
});

describe("outcomeOf", () => {
  it("tells a code from the person's no and from the server's refusal", () => {
    expect(outcomeOf("https://claude.ai/cb?code=c1&state=s&iss=x")).toBe("granted");
    expect(outcomeOf("http://127.0.0.1:33418/cb?error=access_denied&state=s")).toBe("denied");
    expect(outcomeOf("https://claude.ai/cb?error=invalid_scope&state=s")).toBe("failed");
  });
});

describe("grantedSummary", () => {
  it("lists what was granted in the scopes' own order", () => {
    expect(grantedSummary(["candidates:read", "projects:read", "companies:read"])).toBe(
      "positions, companies and executives",
    );
    expect(grantedSummary(["projects:read"])).toBe("positions");
  });
});

describe("clientSubtitle", () => {
  const base: ConsentContext = {
    clientId: "x",
    clientName: "x",
    clientKind: "DCR",
    clientHost: null,
    verified: false,
    clientUri: null,
    logoUri: null,
    redirectHost: "127.0.0.1:33418",
    requestedScopes: [],
    workspaces: [],
  };

  it("names a document's host, says a registration is self-made, and falls back to the app's site", () => {
    expect(clientSubtitle({ ...base, clientKind: "CIMD", clientHost: "claude.ai" })).toBe("claude.ai");
    expect(clientSubtitle(base)).toBe("Registered itself");
    expect(clientSubtitle({ ...base, clientKind: "SEEDED", clientUri: "https://chatgpt.com/apps" })).toBe("chatgpt.com");
  });
});

describe("refusalMessage", () => {
  it("never repeats what the server described, only what its code means", () => {
    expect(refusalMessage("OAUTH_CLIENT_NOT_FOUND")).toMatch(/never registered/);
    expect(refusalMessage("anything else")).toMatch(/not one Uncava accepts/);
  });
});

describe("knownClientMarkOf", () => {
  const verifiedDocument = (clientHost: string): ConsentContext => ({
    clientId: `https://${clientHost}/oauth/client.json`,
    clientName: "x",
    clientKind: "CIMD",
    clientHost,
    verified: true,
    clientUri: null,
    logoUri: null,
    redirectHost: clientHost,
    requestedScopes: [],
    workspaces: [],
  });

  it("draws Claude's and ChatGPT's own marks only for a document the server verified on their host", () => {
    expect(knownClientMarkOf(verifiedDocument("claude.ai"))).toBe("claude");
    expect(knownClientMarkOf(verifiedDocument("chatgpt.com"))).toBe("chatgpt");
    expect(knownClientMarkOf(verifiedDocument("cursor.com"))).toBeNull();
    expect(knownClientMarkOf({ ...verifiedDocument("claude.ai"), verified: false })).toBeNull();
  });

  it("never for a self-registered app that names itself Claude", () => {
    expect(
      knownClientMarkOf({ ...verifiedDocument("claude.ai"), clientKind: "DCR", clientHost: null, clientName: "Claude" }),
    ).toBeNull();
  });
});
