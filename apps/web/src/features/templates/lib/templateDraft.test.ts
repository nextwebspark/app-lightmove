import { describe, expect, it } from "vitest";
import type { TemplateCompetency, TemplateDetail } from "../api/types";
import { blankDraft, draftOf, draftProblems, requestOf } from "./templateDraft";

const cfo = (overrides: Partial<TemplateDetail> = {}): TemplateDetail => ({
  code: "chief-financial-officer",
  title: "Chief Financial Officer",
  discipline: "FINANCE",
  seniority: "C_SUITE",
  summary: "Group finance, the capital structure and the shareholder relationship.",
  origin: "LIBRARY",
  active: true,
  fallback: false,
  libraryChangedSinceCustomised: false,
  keywords: ["chief financial", "cfo"],
  customisedByWorkspaces: null,
  version: 7,
  revisedAt: "2026-09-02T10:00:00Z",
  revisedByName: null,
  body: {
    department: "Finance",
    employmentType: "FULL_TIME_PERMANENT",
    narrative: "Sits on the executive committee.",
    responsibilities: ["Group P&L stewardship"],
    reportsTo: "Group CEO",
    directReports: ["Head of Treasury"],
    strategicPriorities: ["Capital discipline"],
    noticeValue: 3,
    noticeUnit: "MONTHS",
    currency: "USD",
    baseSalaryMode: "ANNUAL",
    bonusValue: 40,
    bonusBasis: "PERCENT_OF_BASE",
    incentiveType: "LTIP_CASH",
    incentiveVesting: "Three-year cycle",
    benefits: [{ name: "Housing allowance", frequency: "MONTHLY" }],
    criteria: [{ text: "Board exposure", mode: "REQUIRED" }],
    competencies: [
      { panel: "TECHNICAL", name: "Reporting", description: "IFRS and controls", weight: 60 },
      { panel: "TECHNICAL", name: "Treasury", description: null, weight: 40 },
      { panel: "BEHAVIOURAL", name: "Leadership", description: "Sets direction", weight: 100 },
    ],
  },
  ...overrides,
});

describe("templateDraft — the editor's copy of a template", () => {
  it("starts a new template's package in AED", () => {
    expect(blankDraft().currency).toBe("AED");
  });

  it("sends back exactly what it was given when nothing is edited", () => {
    const detail = cfo();
    const { title, discipline, seniority, summary, keywords, body, version } = detail;

    expect(requestOf(draftOf(detail), version)).toEqual({
      title,
      discipline,
      seniority,
      summary,
      keywords,
      body,
      version,
    });
  });

  it("puts each competency back in its own panel, technical first", () => {
    const behavioural: TemplateCompetency = { panel: "BEHAVIOURAL", name: "Leadership", description: null, weight: 100 };
    const technical: TemplateCompetency = { panel: "TECHNICAL", name: "Reporting", description: null, weight: 100 };
    const detail = cfo({ body: { ...cfo().body, competencies: [behavioural, technical] } });

    expect(requestOf(draftOf(detail), 7).body.competencies).toEqual([technical, behavioural]);
  });

  it("sends a cleared text field as null rather than an empty string", () => {
    const draft = { ...draftOf(cfo()), department: "   ", narrative: "", summary: "" };

    const request = requestOf(draft, 7);

    expect(request.summary).toBeNull();
    expect(request.body.department).toBeNull();
    expect(request.body.narrative).toBeNull();
  });

  it("names what stops a save: no title, and a panel that does not total 100", () => {
    const draft = draftOf(cfo());
    expect(draftProblems(draft)).toEqual([]);

    const broken = { ...draft, title: " ", technical: draft.technical.map((row) => ({ ...row, weight: 10 })) };

    expect(draftProblems(broken)).toEqual([
      "Give the template a title.",
      "Technical competency weights must total 100.",
    ]);
  });

  it("lets a panel with no competencies in it save", () => {
    expect(draftProblems({ ...draftOf(cfo()), behavioural: [] })).toEqual([]);
  });
});
