import { describe, expect, it } from "vitest";
import type { ConsentContext } from "../api/types";
import {
  clientLogoOf,
  clientSubtitle,
  consentOutcomeOf,
  grantedSummary,
  initialScopes,
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
    const seeded = { ...base, clientKind: "SEEDED" as const, clientUri: "https://chatgpt.com/apps" };
    expect(clientSubtitle(seeded)).toBe("chatgpt.com");
  });
});

describe("refusalMessage", () => {
  it("never repeats what the server described, only what its code means", () => {
    expect(refusalMessage("OAUTH_CLIENT_NOT_FOUND")).toMatch(/never registered/);
    expect(refusalMessage("anything else")).toMatch(/not one Uncava accepts/);
  });
});

describe("clientLogoOf", () => {
  const verifiedDocument = (clientHost: string, logoUri: string | null = null): ConsentContext => ({
    clientId: `https://${clientHost}/oauth/client.json`,
    clientName: "x",
    clientKind: "CIMD",
    clientHost,
    verified: true,
    clientUri: null,
    logoUri,
    redirectHost: clientHost,
    requestedScopes: [],
    workspaces: [],
  });

  it("draws Claude's and ChatGPT's own marks only for a document the server verified on their host", () => {
    expect(clientLogoOf(verifiedDocument("claude.ai"))).toEqual({ kind: "mark", mark: "claude" });
    expect(clientLogoOf(verifiedDocument("chatgpt.com"))).toEqual({ kind: "mark", mark: "chatgpt" });
    expect(clientLogoOf(verifiedDocument("cursor.com"))).toBeNull();
    expect(clientLogoOf({ ...verifiedDocument("claude.ai"), verified: false })).toBeNull();
  });

  it("takes another verified app's logo only over https", () => {
    expect(clientLogoOf(verifiedDocument("cursor.com", "https://cursor.com/logo.svg"))).toEqual({
      kind: "url",
      url: "https://cursor.com/logo.svg",
    });
    expect(clientLogoOf(verifiedDocument("cursor.com", "http://cursor.com/logo.svg"))).toBeNull();
    expect(clientLogoOf(verifiedDocument("cursor.com", "not a url"))).toBeNull();
  });

  it("never for a self-registered app, even one naming itself Claude with Claude's logo", () => {
    expect(
      clientLogoOf({
        ...verifiedDocument("claude.ai", "https://claude.ai/logo.svg"),
        clientKind: "DCR",
        clientHost: null,
        clientName: "Claude",
        verified: false,
      }),
    ).toBeNull();
  });
});

describe("consentOutcomeOf", () => {
  it("reads the server's answer against the button pressed", () => {
    expect(consentOutcomeOf("https://claude.ai/cb?code=c", "allow", "Al-Futtaim", ["projects:read"])).toEqual({
      kind: "allowed",
      workspaceName: "Al-Futtaim",
      scopes: ["projects:read"],
    });
    expect(consentOutcomeOf("https://claude.ai/cb?error=access_denied", "deny", "Al-Futtaim", [])).toEqual({
      kind: "denied",
    });
  });

  it("calls an access_denied after Allow the server's refusal, never the person's own no", () => {
    expect(consentOutcomeOf("https://claude.ai/cb?error=access_denied", "allow", "Al-Futtaim", ["projects:read"]))
      .toEqual({ kind: "notConnected" });
    expect(consentOutcomeOf("https://claude.ai/cb?error=invalid_scope", "allow", "Al-Futtaim", [])).toEqual({
      kind: "notConnected",
    });
  });
});
