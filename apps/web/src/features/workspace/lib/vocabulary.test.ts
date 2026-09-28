import { describe, expect, it } from "vitest";
import { vocabularyFor } from "./vocabulary";

describe("vocabularyFor", () => {
  it("names an in-house workspace's clients as business units and their people as hiring managers", () => {
    const vocabulary = vocabularyFor("COMPANY");
    expect(vocabulary.units).toBe("Business units");
    expect(vocabulary.contactsLower).toBe("hiring managers");
  });

  it("names an agency's clients as clients and their people as client contacts", () => {
    const vocabulary = vocabularyFor("AGENCY");
    expect(vocabulary.units).toBe("Clients");
    expect(vocabulary.contactsLower).toBe("client contacts");
  });
});
