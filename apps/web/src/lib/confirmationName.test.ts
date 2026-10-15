import { describe, expect, it } from "vitest";
import { confirmationNameMatches } from "./confirmationName";

describe("confirmationNameMatches", () => {
  it("forgives case, spacing and a hyphen typed for a dash", () => {
    expect(confirmationNameMatches("Group CFO – Energy Division", "  group cfo -  energy division ")).toBe(true);
  });

  it("refuses a prefix, another name and nothing at all", () => {
    expect(confirmationNameMatches("Chief Financial Officer", "Chief Financial")).toBe(false);
    expect(confirmationNameMatches("Chief Financial Officer", "Chief Operating Officer")).toBe(false);
    expect(confirmationNameMatches("Chief Financial Officer", "   ")).toBe(false);
  });
});
