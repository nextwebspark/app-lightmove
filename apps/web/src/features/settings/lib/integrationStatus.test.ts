import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { WorkspaceIntegration } from "../api/types";
import { integrationStatus, shortClientId } from "./integrationStatus";

const MICROSOFT_APP = "3f2a9c71-5b0e-4d8a-9e61-2c7d4b1a8f05";

const shared: WorkspaceIntegration = {
  provider: "MICROSOFT",
  mode: "SHARED",
  clientId: null,
  tenantId: null,
  secretExpiresOn: null,
  secretSet: false,
  secretHint: null,
  secretSavedAt: null,
  secretSavedBy: null,
  keysCheckedAt: null,
  keysRefusedWith: null,
  sharedOffered: true,
  redirectUri: "https://beta.uncava.com/api/v1/outreach/mailbox/callback",
  scopes: ["Mail.Send"],
  adminConsentUrl: null,
  ownAppGuideUrl: null,
  sharedAppGuideUrl: null,
  updatedAt: null,
  adminConsentedAt: null,
  adminConsentTenantId: null,
  connectionsThroughApp: 0,
};

const ownApp = (overrides: Partial<WorkspaceIntegration> = {}): WorkspaceIntegration => ({
  ...shared,
  mode: "OWN",
  clientId: MICROSOFT_APP,
  tenantId: "meridiansearch.onmicrosoft.com",
  secretSet: true,
  secretHint: "Jx4q",
  secretExpiresOn: "2027-09-01",
  keysCheckedAt: "2026-10-05T09:58:00Z",
  ...overrides,
});

describe("integrationStatus", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"], now: new Date("2026-10-05T10:00:00") });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("reads a provider on Uncava's app as shared, or as not set up where there is none", () => {
    expect(integrationStatus(shared)).toEqual({ pill: "Shared app", tone: "neutral", summary: "Uncava's shared app" });
    expect(integrationStatus({ ...shared, sharedOffered: false })).toEqual({
      pill: "Not set up",
      tone: "muted",
      summary: "Not connected yet. Uncava's shared app isn't available here, so use your own.",
    });
  });

  it("says an own app opened but not yet saved is not saved", () => {
    expect(integrationStatus(shared, { openedOwn: true })).toMatchObject({
      pill: "Not saved yet",
      summary: "Your own app, not saved yet",
    });
  });

  it("calls a checked own app verified and sums it up by its client ID and the secret's last four", () => {
    expect(integrationStatus(ownApp())).toEqual({
      pill: "Own app · Verified",
      tone: "ok",
      summary: "Your own app · 3f2a9c71…8f05 · secret ••••Jx4q",
    });
  });

  it("calls an own app nobody has checked yet just an own app", () => {
    expect(integrationStatus(ownApp({ keysCheckedAt: null, secretHint: null }))).toEqual({
      pill: "Own app",
      tone: "neutral",
      summary: "Your own app · 3f2a9c71…8f05",
    });
  });

  it("counts down the last 30 days of a secret, and says when it has expired", () => {
    expect(integrationStatus(ownApp({ secretExpiresOn: "2026-11-05" })).pill).toBe("Own app · Verified");
    expect(integrationStatus(ownApp({ secretExpiresOn: "2026-11-04" }))).toMatchObject({
      pill: "Expires in 30 days",
      tone: "warning",
    });
    expect(integrationStatus(ownApp({ secretExpiresOn: "2026-10-06" })).pill).toBe("Expires in 1 day");
    expect(integrationStatus(ownApp({ secretExpiresOn: "2026-10-05" })).pill).toBe("Expires today");
    expect(integrationStatus(ownApp({ secretExpiresOn: "2026-10-04" }))).toMatchObject({
      pill: "Secret expired",
      tone: "failing",
    });
  });

  it("puts a refusal above everything else, whether a check or a mailbox found it", () => {
    expect(
      integrationStatus(ownApp({ keysRefusedWith: "AADSTS7000215", secretExpiresOn: "2026-10-04" })),
    ).toMatchObject({ pill: "Keys refused", tone: "failing" });
  });

  it("leaves a short client ID whole", () => {
    expect(shortClientId("Xr2k9QpLT4mVb7")).toBe("Xr2k9QpLT4mVb7");
  });
});
