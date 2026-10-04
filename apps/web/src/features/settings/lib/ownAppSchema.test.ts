import { describe, expect, it } from "vitest";
import { ownAppSchema } from "./ownAppSchema";

const VALID = { clientId: "acme-app", clientSecret: "s3cret", tenantId: "", secretExpiresOn: "" };

function errorsOf(rules: Parameters<typeof ownAppSchema>[0], values: typeof VALID) {
  const result = ownAppSchema(rules).safeParse(values);
  return result.success ? {} : Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
}

describe("ownAppSchema", () => {
  const google = { needsTenant: false, storedSecretClientId: null };

  it("accepts an app with its client ID and secret", () => {
    expect(errorsOf(google, VALID)).toEqual({});
  });

  it("asks for the secret of an app it has none for", () => {
    expect(errorsOf(google, { ...VALID, clientSecret: "  " })).toHaveProperty("clientSecret");
  });

  it("keeps the stored secret when the client ID is the one it was saved under", () => {
    const saved = { needsTenant: false, storedSecretClientId: "acme-app" };

    expect(errorsOf(saved, { ...VALID, clientSecret: "" })).toEqual({});
    expect(errorsOf(saved, { ...VALID, clientId: "another-app", clientSecret: "" })).toHaveProperty("clientSecret");
  });

  it("asks a Microsoft app for its tenant", () => {
    const microsoft = { needsTenant: true, storedSecretClientId: null };

    expect(errorsOf(microsoft, VALID)).toHaveProperty("tenantId");
    expect(errorsOf(microsoft, { ...VALID, tenantId: "contoso.onmicrosoft.com" })).toEqual({});
  });

  it("refuses a client ID that could not have come from a provider", () => {
    expect(errorsOf(google, { ...VALID, clientId: "acme app&x=1" })).toHaveProperty("clientId");
  });
});
