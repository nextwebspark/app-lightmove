import { describe, expect, it } from "vitest";
import { ownAppSchema, type OwnAppRules } from "./ownAppSchema";

const GOOGLE_APP = "acme-app.apps.googleusercontent.com";
const MICROSOFT_APP = "6a1c3e5f-7b9d-4f2a-8c4e-0d2b4f6a8c0e";
const VALID = { clientId: GOOGLE_APP, clientSecret: "GOCSPX-s3cret", tenantId: "", secretExpiresOn: "" };
const TODAY = "2026-11-01";

const google: OwnAppRules = { provider: "GOOGLE", storedSecretClientId: null, storedExpiresOn: null, today: TODAY };
const microsoft: OwnAppRules = { ...google, provider: "MICROSOFT" };
const zoom: OwnAppRules = { ...google, provider: "ZOOM" };
const MICROSOFT_VALID = { ...VALID, clientId: MICROSOFT_APP, clientSecret: "Q8~kT2v.Lm9", tenantId: "contoso.onmicrosoft.com" };

function errorsOf(rules: OwnAppRules, values: typeof VALID) {
  const result = ownAppSchema(rules).safeParse(values);
  return result.success ? {} : Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
}

describe("ownAppSchema", () => {
  it("accepts an app with its client ID and secret", () => {
    expect(errorsOf(google, VALID)).toEqual({});
    expect(errorsOf(microsoft, MICROSOFT_VALID)).toEqual({});
    expect(errorsOf(zoom, { ...VALID, clientId: "Xr2k9QpLT4m" })).toEqual({});
  });

  it("asks for the secret of an app it has none for", () => {
    expect(errorsOf(google, { ...VALID, clientSecret: "  " })).toHaveProperty("clientSecret");
  });

  it("keeps the stored secret when the client ID is the one it was saved under", () => {
    const saved = { ...google, storedSecretClientId: GOOGLE_APP };

    expect(errorsOf(saved, { ...VALID, clientSecret: "" })).toEqual({});
    expect(errorsOf(saved, { ...VALID, clientId: "another.apps.googleusercontent.com", clientSecret: "" })).toHaveProperty(
      "clientSecret",
    );
  });

  it.each(["GOCSPX-abc\n", " GOCSPX-abc", "GOCSPX-abc ", "GOCSPX abc", "GOCSPX\tabc", "GOCSPX\u00a0abc", "GOCSPX\u200babc", "\ufeffGOCSPX-abc", "GOCSPX-abc\u2060"])(
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
    for (const tenant of ["common", "organizations", "consumers", "..", ".contoso.com"]) {
      expect(errorsOf(microsoft, { ...MICROSOFT_VALID, tenantId: tenant })).toHaveProperty("tenantId");
    }
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, tenantId: "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b" })).toEqual({});
  });

  it("refuses an Entra secret's ID where its value belongs", () => {
    expect(
      errorsOf(microsoft, { ...MICROSOFT_VALID, clientSecret: "8c1d2e3f-0a1b-4c2d-9e8f-7a6b5c4d3e2f" }),
    ).toHaveProperty("clientSecret");
  });

  it("holds a Microsoft secret's expiry to today through 24 months, letting the date on record stand", () => {
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "2026-10-31" })).toHaveProperty("secretExpiresOn");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "2028-11-02" })).toHaveProperty("secretExpiresOn");
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: "2028-11-01" })).toEqual({});
    expect(errorsOf(microsoft, { ...MICROSOFT_VALID, secretExpiresOn: TODAY })).toEqual({});
    const sameAppKept = { ...microsoft, storedSecretClientId: MICROSOFT_APP, storedExpiresOn: "2026-10-20" };
    expect(errorsOf(sameAppKept, { ...MICROSOFT_VALID, clientSecret: "", secretExpiresOn: "2026-10-20" })).toEqual({});
  });

  it("checks a lapsed date on record again for another app or a new secret", () => {
    const sameAppKept = { ...microsoft, storedSecretClientId: MICROSOFT_APP, storedExpiresOn: "2026-10-20" };

    expect(
      errorsOf(sameAppKept, { ...MICROSOFT_VALID, clientId: "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e", secretExpiresOn: "2026-10-20" }),
    ).toHaveProperty("secretExpiresOn");
    expect(errorsOf(sameAppKept, { ...MICROSOFT_VALID, secretExpiresOn: "2026-10-20" })).toHaveProperty("secretExpiresOn");
  });
});
