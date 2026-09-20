import { describe, expect, it } from "vitest";
import type { PositionTemplate } from "../api/types";
import { MAX_SUGGESTIONS, suggestionsFor } from "./roleTitleSuggestions";

const template = (title: string, summary: string | null = null): PositionTemplate => ({
  id: title.toLowerCase().replace(/\s+/g, "-"),
  code: title.toLowerCase().replace(/\s+/g, "-"),
  title,
  discipline: "FINANCE",
  seniority: "C_SUITE",
  summary,
  shared: true,
});

const catalog = [
  template("Chief Financial Officer", "Group finance and the capital structure."),
  template("Chief Compliance Officer", "The compliance programme."),
  template("Head of Treasury", "Cash, debt and the chief lender relationships."),
];

describe("role-title suggestions", () => {
  it("offers the whole catalog while the box is empty — seventeen titles is a menu, not a search", () => {
    expect(suggestionsFor(catalog, "   ")).toEqual(catalog);
  });

  it("matches titles before summaries, so 'chief' does not lead with a summary that mentions it", () => {
    expect(suggestionsFor(catalog, "chief").map((match) => match.title)).toEqual([
      "Chief Financial Officer",
      "Chief Compliance Officer",
      "Head of Treasury",
    ]);
  });

  it("never offers more than the list can show", () => {
    const many = Array.from({ length: 12 }, (_, index) => template(`Role ${index}`));
    expect(suggestionsFor(many, "role")).toHaveLength(MAX_SUGGESTIONS);
  });
});
