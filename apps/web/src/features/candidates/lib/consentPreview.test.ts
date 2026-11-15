import { describe, expect, it } from "vitest";
import { consentRequestPreview, withArticle } from "./consentPreview";

describe("withArticle", () => {
  it.each([
    ["Chief Financial Officer", "a Chief Financial Officer"],
    ["Operations Director", "an Operations Director"],
    ["Interim CFO", "an Interim CFO"],
    ["HR Director", "an HR Director"],
    ["CFO", "a CFO"],
    ["MD Europe", "an MD Europe"],
    ["Unit Head", "a Unit Head"],
    ["European Sales Lead", "a European Sales Lead"],
    ["  Engineering Manager ", "an Engineering Manager"],
  ])("%s reads %s, as IndefiniteArticle does", (title, expected) => {
    expect(withArticle(title)).toBe(expected);
  });

  it("puts the article the title needs in the share email's subject and intro", () => {
    const preview = consentRequestPreview("Meridian", "LinkedIn", [], 24, { roleTitle: "Operations Director", clientName: null });
    expect(preview.subject).toBe("May we share your profile for an Operations Director role?");
    expect(preview.intro).toContain("is searching for an Operations Director for a confidential client");
  });
});
