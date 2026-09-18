import { describe, expect, it } from "vitest";
import type { TemplateOverview } from "../api/types";
import { filterTemplates, reviserOf } from "./filtering";

const row = (overrides: Partial<TemplateOverview>): TemplateOverview => ({
  code: "chief-executive-officer",
  title: "Chief Executive Officer",
  discipline: "EXECUTIVE",
  seniority: "C_SUITE",
  summary: "Leads the group",
  origin: "LIBRARY",
  active: true,
  fallback: false,
  libraryChangedSinceCustomised: false,
  keywords: ["ceo", "managing director"],
  customisedByWorkspaces: null,
  revisedAt: "2026-09-02T10:00:00Z",
  revisedByName: null,
  ...overrides,
});

const codes = (rows: TemplateOverview[]) => rows.map((template) => template.code);

describe("filterTemplates", () => {
  const templates = [
    row({}),
    row({ code: "chief-financial-officer", title: "Chief Financial Officer", discipline: "FINANCE", keywords: ["cfo"] }),
    row({ code: "chief-risk-officer", title: "Chief Risk Officer", discipline: "GOVERNANCE", keywords: [] }),
  ];

  it("finds a template by a match keyword, not only by its title", () => {
    expect(codes(filterTemplates(templates, "Managing Director"))).toEqual(["chief-executive-officer"]);
  });

  it("finds a template by its discipline label", () => {
    expect(codes(filterTemplates(templates, "governance"))).toEqual(["chief-risk-officer"]);
  });

  it("keeps every template when the search is blank", () => {
    expect(filterTemplates(templates, "  ")).toHaveLength(3);
  });
});

describe("reviserOf", () => {
  it("names LightMove for a library template a firm is looking at, and nobody for an unrevised library row", () => {
    expect(reviserOf("workspace", row({ origin: "LIBRARY" }))).toBe("LightMove");
    expect(reviserOf("library", row({ origin: null }))).toBeNull();
    expect(reviserOf("workspace", row({ origin: "OWN", revisedByName: "Sara" }))).toBe("Sara");
  });
});
