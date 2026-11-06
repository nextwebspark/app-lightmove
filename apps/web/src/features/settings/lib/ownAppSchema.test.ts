import { describe, expect, it } from "vitest";
import { ownAppSchema, replaceSecretSchema, type OwnAppRules } from "./ownAppSchema";

const GOOGLE_APP = "acme-app.apps.googleusercontent.com";
const MICROSOFT_APP = "6a1c3e5f-7b9d-4f2a-8c4e-0d2b4f6a8c0e";
const VALID = { clientId: GOOGLE_APP, clientSecret: "GOCSPX-s3cret", tenantId: "", secretExpiresOn: "" };
const TODAY = "2026-11-01";

const google: OwnAppRules = { provider: "GOOGLE", today: TODAY };
const microsoft: OwnAppRules = { ...google, provider: "MICROSOFT" };
const zoom: OwnAppRules = { ...google, provider: "ZOOM" };
const MICROSOFT_VALID = {
  ...VALID,
  clientId: MICROSOFT_APP,
  clientSecret: "Q8~kT2v.Lm9",
  tenantId: "contoso.onmicrosoft.com",
  secretExpiresOn: "2027-09-01",
};

function errorsOf(rules: OwnAppRules, values: typeof VALID) {
  const result = ownAppSchema(rules).safeParse(values);
  return result.success ? {} : Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
}

function replaceErrorsOf(rules: OwnAppRules, values: { clientSecret: string; secretExpiresOn: string }) {
  const result = replaceSecretSchema(rules).safeParse(values);
  return result.success ? {} : Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
}

describe("ownAppSchema", () => {
  it("accepts an app with its client ID and secret", () => {
    expect(errorsOf(google, VALID)).toEqual({});
    expect(errorsOf(microsoft, MICROSOFT_VALID)).toEqual({});
    expect(errorsOf(zoom, { ...VALID, clientId: "Xr2k9QpLT4m" })).toEqual({});
  });

  it("always asks for the secret, since a new app has none held", () => {
    expect(errorsOf(google, { ...VALID, clientSecret: "" })).toHaveProperty("clientSecret");
    expect(errorsOf(google, { ...VALID, clientSecret: "  " })).toHaveProperty("clientSecret");
  });

  it("names the field the way the provider's console does when it is missing", () => {
    expect(errorsOf(google, { ...VALID, clientId: "" }).clientId).toBe("Enter your app's client ID");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, clientId: "" }).clientId).toBe(
      "Enter your app's application (client) ID",
    );
  });

  it.each(["GOCSPX-abc\n", " GOCSPX-abc", "GOCSPX-abc ", "GOCSPX abc", "GOCSPX\tabc", "GOCSPX abc", "GOCSPX​abc", "﻿GOCSPX-abc", "GOCSPX-abc⁠"])(
    "refuses a secret carrying whitespace or an invisible format character, never trimming it: %j",
    (secret) => {
      expect(errorsOf(google, { ...VALID, clientSecret: secret })).toHaveProperty("clientSecret");
    },
  );

  it("holds each provider's client ID to the shape it issues them in", () => {
    expect(errorsOf(google, { ...VALID, clientId: "1234-abc" })).toHaveProperty("clientId");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, clientId: "contoso-app" })).toHaveProperty("clientId");
    expect(errorsOf(google, { ...VALID, clientId: "acme app&x=1" })).toHaveProperty("clientId");
  });

  it("asks a Microsoft app for its own directory, never a multi-tenant alias", () => {
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, tenantId: "" })).toHaveProperty("tenantId");
    for (const tenant of ["common", "Organizations", "consumers", "..", ".contoso.com"]) {
      expect(errorsOf(microsoft, { ...MICROSOFT_VALID, tenantId: tenant })).toHaveProperty("tenantId");
    }
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, tenantId: "common" }).tenantId).toBe("Use your own directory, not common");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, tenantId: "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b" })).toEqual({});
  });

  it("refuses an Entra secret's ID where its value belongs", () => {
    expect(
      errorsOf(microsoft, { ...MICROSOFT_VALID, clientSecret: "8c1d2e3f-0a1b-4c2d-9e8f-7a6b5c4d3e2f" }),
    ).toHaveProperty("clientSecret");
  });

  it("asks a Microsoft secret's expiry and holds it to today through 24 months", () => {
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "" }).secretExpiresOn).toBe(
      "Enter when the secret expires",
    );
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "2026-10-31" })).toHaveProperty("secretExpiresOn");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "2028-11-02" })).toHaveProperty("secretExpiresOn");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "2028-11-01" })).toEqual({});
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: TODAY })).toEqual({});
  });

  it("asks no expiry of a secret that never expires", () => {
    expect(errorsOf(google, { ...VALID, secretExpiresOn: "" })).toEqual({});
    expect(errorsOf(zoom, { ...VALID, clientId: "Xr2k9QpLT4m", secretExpiresOn: "" })).toEqual({});
  });
});

describe("replaceSecretSchema", () => {
  it("asks for the new secret, held to the same rules as a first one", () => {
    expect(replaceErrorsOf(google, { clientSecret: "GOCSPX-n3w", secretExpiresOn: "" })).toEqual({});
    expect(replaceErrorsOf(google, { clientSecret: "", secretExpiresOn: "" })).toHaveProperty("clientSecret");
    expect(replaceErrorsOf(google, { clientSecret: "GOCSPX-n3w\n", secretExpiresOn: "" })).toHaveProperty("clientSecret");
    expect(
      replaceErrorsOf(microsoft, { clientSecret: "8c1d2e3f-0a1b-4c2d-9e8f-7a6b5c4d3e2f", secretExpiresOn: "2027-09-01" }),
    ).toHaveProperty("clientSecret");
  });

  it("asks a new Microsoft secret for its own expiry", () => {
    expect(replaceErrorsOf(microsoft, { clientSecret: "Q8~kT2v.Lm9", secretExpiresOn: "" })).toHaveProperty(
      "secretExpiresOn",
    );
    expect(replaceErrorsOf(microsoft, { clientSecret: "Q8~kT2v.Lm9", secretExpiresOn: "2026-10-20" })).toHaveProperty(
      "secretExpiresOn",
    );
    expect(replaceErrorsOf(microsoft, { clientSecret: "Q8~kT2v.Lm9", secretExpiresOn: "2027-09-01" })).toEqual({});
  });
});
