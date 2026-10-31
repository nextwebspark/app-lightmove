import { describe, expect, it } from "vitest";
import type {
  Compensation,
  Competency,
  Criterion,
  MandateContext,
  OrgNode,
  PositionDetails,
  PositionExtraction,
  ProposedField,
  ReportingStructure,
  Responsibility,
  StrategicPriority,
} from "../api/types";
import {
  balancePanelWeights,
  fillBrief,
  isEmploymentType,
  isSeniority,
  undoListItem,
  undoScalar,
  undoStep,
  type PositionSnapshot,
  type StepReceipt,
} from "./documentFill";

const FILE_NAME = "JD_CFO.pdf";

const field = (
  fieldKey: string,
  value: string,
  overrides: Partial<ProposedField> = {},
): ProposedField => ({
  id: overrides.id ?? Math.random(),
  fieldKey,
  value,
  confidence: overrides.confidence ?? "high",
  snippet: overrides.snippet ?? `"${value}"`,
  origin: overrides.origin ?? "document",
});

const extraction = (fields: ProposedField[]): PositionExtraction => ({
  extractionSource: "model",
  fields,
  suggestedTemplate: null,
});

const details = (overrides: Partial<PositionDetails> = {}): PositionDetails => ({
  roleTitle: "Chief Financial Officer",
  department: null,
  locationCity: null,
  locationCountry: null,
  employmentType: null,
  seniority: null,
  responsibilities: [],
  narrative: null,
  fieldSources: {},
  ...overrides,
});

const context = (overrides: Partial<MandateContext> = {}): MandateContext => ({
  mandateReason: "NEW_ROLE",
  businessDriver: null,
  strategicPriorities: [],
  confidential: false,
  internalContext: null,
  fieldSources: {},
  ...overrides,
});

const mandateSeat: OrgNode = {
  nodeId: "role",
  parentNodeId: null,
  title: null,
  name: null,
  mandateSeat: true,
  canvasX: null,
  canvasY: null,
};

const reporting = (overrides: Partial<ReportingStructure> = {}): ReportingStructure => ({
  orgChart: [mandateSeat],
  teamSize: null,
  targetStart: null,
  noticeValue: null,
  noticeUnit: null,
  fieldSources: {},
  ...overrides,
});

const compensation = (overrides: Partial<Compensation> = {}): Compensation => ({
  currency: "AED",
  salaryMin: null,
  salaryMax: null,
  baseSalaryMode: "ANNUAL",
  bonusValue: 40,
  bonusBasis: "PERCENT_OF_BASE",
  incentiveType: null,
  incentiveAmount: null,
  incentiveVesting: null,
  benefits: [{ name: "Housing allowance", amount: null, frequency: "MONTHLY", source: "TEMPLATE" }],
  fieldSources: { currency: "TEMPLATE", bonusValue: "TEMPLATE", bonusBasis: "TEMPLATE", baseSalaryMode: "TEMPLATE" },
  ...overrides,
});

const snapshotOf = (overrides: Partial<PositionSnapshot> = {}): PositionSnapshot => ({
  details: details(),
  context: context(),
  reporting: reporting(),
  compensation: compensation(),
  criteria: [],
  technical: [],
  behavioural: [],
  technicalShare: 50,
  ...overrides,
});

describe("enum guards", () => {
  it("accepts only a known employment type or seniority tier", () => {
    expect(isEmploymentType("FULL_TIME_PERMANENT")).toBe(true);
    expect(isEmploymentType("TEMPORARY")).toBe(true);
    expect(isEmploymentType("made up")).toBe(false);
    expect(isSeniority("C_SUITE")).toBe(true);
    expect(isSeniority("made up")).toBe(false);
  });
});

describe("scalar fill — keep, replace, ignore", () => {
  it("keeps a MANUAL value and counts it skipped", () => {
    const snapshot = snapshotOf({
      details: details({ department: "Group Finance", fieldSources: { department: "MANUAL" } }),
    });
    const outcome = fillBrief(snapshot, { details: extraction([field("department", "Corporate Finance")]) }, FILE_NAME);
    expect(outcome.next.details.department).toBe("Group Finance");
    expect(outcome.changed.has("brief")).toBe(false);
    expect(outcome.skipped).toContainEqual({ step: "details", fieldKey: "department", reason: "manual" });
  });

  it("replaces a TEMPLATE value, a DOCUMENT value, and an absent one", () => {
    for (const currentSource of ["TEMPLATE", "DOCUMENT", undefined] as const) {
      const fieldSources: Record<string, "TEMPLATE" | "DOCUMENT" | "MANUAL"> = currentSource
        ? { department: currentSource }
        : {};
      const snapshot = snapshotOf({ details: details({ department: "Old", fieldSources }) });
      const outcome = fillBrief(
        snapshot,
        { details: extraction([field("department", "Group Finance")]) },
        FILE_NAME,
      );
      expect(outcome.next.details.department).toBe("Group Finance");
      expect(outcome.next.details.fieldSources.department).toBe("DOCUMENT");
      expect(outcome.changed.has("brief")).toBe(true);
    }
  });

  it("ignores a template-origin proposal", () => {
    const snapshot = snapshotOf({ details: details({ department: null }) });
    const outcome = fillBrief(
      snapshot,
      { details: extraction([field("department", "Group Finance", { origin: "template" })]) },
      FILE_NAME,
    );
    expect(outcome.next.details.department).toBeNull();
    expect(outcome.changed.has("brief")).toBe(false);
  });

  it("takes the document's role title over whatever the brief had", () => {
    const snapshot = snapshotOf({ details: details({ roleTitle: "Group CFO – Energy" }) });
    const outcome = fillBrief(
      snapshot,
      { details: extraction([field("roleTitle", "Chief Financial Officer")]) },
      FILE_NAME,
    );
    expect(outcome.next.details.roleTitle).toBe("Chief Financial Officer");
    expect(outcome.changed.has("brief")).toBe(true);
  });

  it("keeps the brief's title when the document states none", () => {
    const snapshot = snapshotOf({ details: details({ roleTitle: "Group CFO – Energy" }) });
    const outcome = fillBrief(snapshot, { details: extraction([field("roleTitle", "   ")]) }, FILE_NAME);
    expect(outcome.next.details.roleTitle).toBe("Group CFO – Energy");
  });

  it("fills each half of the location the server split, marking them apart", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      {
        details: extraction([
          field("locationCity", "Abu Dhabi"),
          field("locationCountry", "United Arab Emirates"),
        ]),
      },
      FILE_NAME,
    );
    expect(outcome.next.details.locationCity).toBe("Abu Dhabi");
    expect(outcome.next.details.locationCountry).toBe("United Arab Emirates");
    expect(outcome.next.details.fieldSources.locationCity).toBe("DOCUMENT");
    expect(outcome.next.details.fieldSources.locationCountry).toBe("DOCUMENT");
  });

  it("fills the country half alone when the document named no city, leaving a typed city alone", () => {
    const snapshot = snapshotOf({
      details: details({ locationCity: "Jubail", fieldSources: { locationCity: "MANUAL" } }),
    });
    const outcome = fillBrief(
      snapshot,
      { details: extraction([field("locationCountry", "Saudi Arabia")]) },
      FILE_NAME,
    );
    expect(outcome.next.details.locationCity).toBe("Jubail");
    expect(outcome.next.details.fieldSources.locationCity).toBe("MANUAL");
    expect(outcome.next.details.locationCountry).toBe("Saudi Arabia");
    expect(outcome.next.details.fieldSources.locationCountry).toBe("DOCUMENT");
  });

  it("drops an employment type or seniority the guard does not recognise", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      { details: extraction([field("employmentType", "GIG_WORK"), field("seniority", "N_MINUS_9")]) },
      FILE_NAME,
    );
    expect(outcome.next.details.employmentType).toBeNull();
    expect(outcome.next.details.seniority).toBeNull();
    expect(outcome.skipped).toContainEqual({ step: "details", fieldKey: "employmentType", reason: "invalid" });
    expect(outcome.skipped).toContainEqual({ step: "details", fieldKey: "seniority", reason: "invalid" });
  });

  it("records the previous value, source, confidence and snippet on the Role Brief's receipt", () => {
    const snapshot = snapshotOf({
      details: details({ department: "Old", fieldSources: { department: "TEMPLATE" } }),
    });
    const outcome = fillBrief(
      snapshot,
      {
        details: extraction([
          field("department", "Group Finance", { confidence: "medium", snippet: "Group Finance division" }),
        ]),
      },
      FILE_NAME,
    );
    expect(outcome.receipts.brief?.scalars.department).toEqual({
      previousValue: "Old",
      previousSource: "TEMPLATE",
      confidence: "medium",
      snippet: "Group Finance division",
    });
    expect(outcome.receipts.brief?.fileName).toBe(FILE_NAME);
  });
});

describe("list fill — kept, dropped, appended, de-duplicated, capped", () => {
  it("leaves a list untouched when nothing is proposed for it", () => {
    const existing: Responsibility[] = [{ text: "Own the P&L", source: "TEMPLATE" }];
    const snapshot = snapshotOf({ details: details({ responsibilities: existing }) });
    const outcome = fillBrief(snapshot, { details: extraction([]) }, FILE_NAME);
    expect(outcome.next.details.responsibilities).toBe(existing);
    expect(outcome.changed.has("brief")).toBe(false);
  });

  it("keeps typed rows, drops the rest, appends what is new and de-duplicates case-insensitively", () => {
    // Eight template responsibilities, two typed, and five proposed — one of which duplicates a typed
    // row. The result is the two typed rows followed by the five proposed (minus the duplicate).
    const templateRows: Responsibility[] = Array.from({ length: 8 }, (_, i) => ({
      text: `Template duty ${i}`,
      source: "TEMPLATE",
    }));
    const typedRows: Responsibility[] = [
      { text: "Own the audit committee relationship", source: "MANUAL" },
      { text: "Lead the finance transformation", source: "MANUAL" },
    ];
    const snapshot = snapshotOf({
      details: details({ responsibilities: [...templateRows, ...typedRows] }),
    });
    const proposed = [
      "Own the audit committee relationship", // case-identical duplicate of a typed row
      "Set the group's capital allocation policy",
      "Chair the investment committee",
      "Own treasury and banking relationships",
      "Sponsor the ERP transformation",
    ];
    const outcome = fillBrief(
      snapshot,
      { details: extraction(proposed.map((value) => field("responsibility", value))) },
      FILE_NAME,
    );
    expect(outcome.next.details.responsibilities).toEqual([
      ...typedRows,
      { text: "Set the group's capital allocation policy", source: "DOCUMENT" },
      { text: "Chair the investment committee", source: "DOCUMENT" },
      { text: "Own treasury and banking relationships", source: "DOCUMENT" },
      { text: "Sponsor the ERP transformation", source: "DOCUMENT" },
    ]);
    expect(outcome.receipts.brief?.lists.responsibilities?.dropped).toHaveLength(8);
  });

  it("counts kept rows first against the per-brief ceiling", () => {
    const kept: Responsibility[] = Array.from({ length: 20 }, (_, i) => ({
      text: `Typed duty ${i}`,
      source: "MANUAL",
    }));
    const snapshot = snapshotOf({ details: details({ responsibilities: kept }) });
    const outcome = fillBrief(
      snapshot,
      { details: extraction([field("responsibility", "One more duty")]) },
      FILE_NAME,
    );
    expect(outcome.next.details.responsibilities).toHaveLength(20);
    expect(outcome.next.details.responsibilities).toEqual(kept);
  });

  it("merges strategic priorities as selected DOCUMENT rows", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      { context: extraction([field("strategicPriority", "Cost discipline")]) },
      FILE_NAME,
    );
    const priorities = outcome.next.context.strategicPriorities as StrategicPriority[];
    expect(priorities).toEqual([{ name: "Cost discipline", selected: true, source: "DOCUMENT" }]);
    expect(outcome.changed.has("brief")).toBe(true);
  });

  it("merges criteria from both required and preferred field keys into one list", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      {
        assessment: extraction([
          field("requiredCriterion", "CPA or equivalent"),
          field("preferredCriterion", "Public markets experience"),
        ]),
      },
      FILE_NAME,
    );
    const criteria = outcome.next.criteria as Criterion[];
    expect(criteria).toEqual([
      { text: "CPA or equivalent", mode: "REQUIRED", source: "DOCUMENT" },
      { text: "Public markets experience", mode: "PREFERRED", source: "DOCUMENT" },
    ]);
    expect(outcome.changed.has("assessment")).toBe(true);
  });
});

describe("competency weights", () => {
  const documentRows = (weights: number[]): Competency[] =>
    weights.map((weight, i) => ({ name: `Competency ${i}`, description: null, weight, source: "DOCUMENT" }));

  it("splits 100 evenly across unstated rows", () => {
    const rows: Competency[] = Array.from({ length: 5 }, (_, i) => ({
      name: `Competency ${i}`,
      description: null,
      weight: 0,
      source: "DOCUMENT",
    }));
    expect(balancePanelWeights(rows).map((row) => row.weight)).toEqual([20, 20, 20, 20, 20]);
  });

  it("splits the remainder across unstated rows once one is stated", () => {
    const rows: Competency[] = [
      { name: "Stated", description: null, weight: 40, source: "MANUAL" },
      ...Array.from({ length: 4 }, (_, i) => ({
        name: `Competency ${i}`,
        description: null,
        weight: 0,
        source: "DOCUMENT" as const,
      })),
    ];
    const balanced = balancePanelWeights(rows);
    expect(balanced[0].weight).toBe(40);
    expect(balanced.slice(1).map((row) => row.weight)).toEqual([15, 15, 15, 15]);
  });

  it("leaves the panel untouched once stated rows already reach 100", () => {
    const rows: Competency[] = [
      { name: "A", description: null, weight: 100, source: "MANUAL" },
      { name: "B", description: null, weight: 0, source: "DOCUMENT" },
    ];
    expect(balancePanelWeights(rows)).toEqual(rows);
  });

  it("leaves the panel untouched when nothing is unstated", () => {
    const rows: Competency[] = [{ name: "A", description: null, weight: 60, source: "MANUAL" }];
    expect(balancePanelWeights(rows)).toBe(rows);
  });

  it("scales a reading's weights down to 100, keeping their order", () => {
    const rows = documentRows([90, 85, 80]);
    expect(balancePanelWeights(rows).map((row) => row.weight)).toEqual([35, 33, 32]);
  });

  it("scales a reading's weights up to 100", () => {
    const rows = documentRows([10, 10, 20]);
    expect(balancePanelWeights(rows).map((row) => row.weight)).toEqual([25, 25, 50]);
  });

  it("shares only what the typed rows leave", () => {
    const rows: Competency[] = [
      { name: "Typed", description: null, weight: 40, source: "MANUAL" },
      ...documentRows([90, 85, 80]),
    ];
    const weights = balancePanelWeights(rows).map((row) => row.weight);
    expect(weights[0]).toBe(40);
    expect(weights.slice(1)).toEqual([21, 20, 19]);
  });

  it("gives a row the reading left unweighted the average of the weighted ones", () => {
    const rows = documentRows([60, 0, 20]);
    expect(balancePanelWeights(rows).map((row) => row.weight)).toEqual([50, 33, 17]);
  });

  it("gives a reading's rows nothing once the typed rows already fill the panel", () => {
    const rows: Competency[] = [
      { name: "Typed A", description: null, weight: 60, source: "MANUAL" },
      { name: "Typed B", description: null, weight: 40, source: "MANUAL" },
      ...documentRows([50, 30, 20]),
    ];
    expect(balancePanelWeights(rows).map((row) => row.weight)).toEqual([60, 40, 0, 0, 0]);
  });

  it("gives a later reading its own emphasis back once a typed row makes room", () => {
    const zeroed = fillBrief(
      snapshotOf({
        technical: [
          { name: "Typed A", description: null, weight: 60, source: "MANUAL" },
          { name: "Typed B", description: null, weight: 40, source: "MANUAL" },
        ],
      }),
      { assessment: extraction([field("technicalCompetency", "Treasury — 50 — "), field("technicalCompetency", "Audit — 50 — ")]) },
      FILE_NAME,
    ).next.technical;
    expect(zeroed.map((row) => row.weight)).toEqual([60, 40, 0, 0]);

    const afterRemoval = zeroed.filter((row) => row.name !== "Typed B");
    const reread = fillBrief(
      snapshotOf({ technical: afterRemoval }),
      { assessment: extraction([field("technicalCompetency", "Treasury — 75 — "), field("technicalCompetency", "Audit — 25 — ")]) },
      FILE_NAME,
    ).next.technical;
    expect(reread.map((row) => [row.name, row.weight])).toEqual([
      ["Typed A", 60],
      ["Treasury", 30],
      ["Audit", 10],
    ]);
  });

  it("returns a reading that already totals 100 as it is", () => {
    const rows = documentRows([50, 30, 20]);
    expect(balancePanelWeights(rows)).toBe(rows);
  });

  it("fills a panel to 100 from a reading that scored every competency", () => {
    const outcome = fillBrief(
      snapshotOf({ behavioural: [{ name: "Template row", description: null, weight: 100, source: "TEMPLATE" }] }),
      {
        assessment: extraction([
          field("behaviouralCompetency", "Change Management — 90 — Lead through transformation"),
          field("behaviouralCompetency", "Stakeholder Influence — 85 — Manage working relationships"),
          field("behaviouralCompetency", "Resilience under Ambiguity — 80 — Handle pressure"),
          field("behaviouralCompetency", "Strategic Leadership — 75 — Provide direction"),
        ]),
      },
      FILE_NAME,
    );
    const weights = outcome.next.behavioural.map((row) => row.weight);
    expect(weights).toEqual([27, 26, 24, 23]);
    expect(weights.reduce((sum, weight) => sum + weight, 0)).toBe(100);
  });

  it("rebalances the panel a fill actually touches", () => {
    const snapshot = snapshotOf({
      technical: [{ name: "Existing", description: null, weight: 60, source: "MANUAL" }],
    });
    const outcome = fillBrief(
      snapshot,
      {
        assessment: extraction([
          field("technicalCompetency", "Capital markets — 0 — "),
          field("technicalCompetency", "Treasury — 0 — "),
        ]),
      },
      FILE_NAME,
    );
    const weights = outcome.next.technical.map((row) => row.weight);
    expect(weights).toEqual([60, 20, 20]);
    expect(outcome.changed.has("assessment")).toBe(true);
  });
});

describe("reporting: team size, notice period and the org chart", () => {
  it("fills teamSize under the Reporting screen", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(snapshot, { reporting: extraction([field("teamSize", "12")]) }, FILE_NAME);
    expect(outcome.next.reporting.teamSize).toBe("12");
    expect(outcome.changed.has("reporting")).toBe(true);
    expect(outcome.receipts.reporting?.scalars.teamSize).toBeDefined();
  });

  it("folds a noticePeriod label into noticeValue/noticeUnit, filed under the Role Brief screen", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      { reporting: extraction([field("noticePeriod", "3 months")]) },
      FILE_NAME,
    );
    expect(outcome.next.reporting.noticeValue).toBe(3);
    expect(outcome.next.reporting.noticeUnit).toBe("MONTHS");
    expect(outcome.next.reporting.fieldSources.noticeValue).toBe("DOCUMENT");
    expect(outcome.changed.has("brief")).toBe(true);
    expect(outcome.changed.has("reporting")).toBe(false);
    expect(outcome.receipts.brief?.scalars.noticePeriod).toBeDefined();
  });

  it("drops an unrecognised notice period rather than writing NaN", () => {
    const snapshot = snapshotOf({ reporting: reporting({ noticeValue: 2, noticeUnit: "MONTHS" }) });
    const outcome = fillBrief(
      snapshot,
      { reporting: extraction([field("noticePeriod", "banana")]) },
      FILE_NAME,
    );
    expect(outcome.next.reporting.noticeValue).toBe(2);
    expect(Number.isNaN(outcome.next.reporting.noticeValue)).toBe(false);
    expect(outcome.skipped).toContainEqual({ step: "reporting", fieldKey: "noticePeriod", reason: "invalid" });
  });

  it("keeps a MANUAL notice period", () => {
    const snapshot = snapshotOf({
      reporting: reporting({ noticeValue: 6, noticeUnit: "MONTHS", fieldSources: { noticeValue: "MANUAL" } }),
    });
    const outcome = fillBrief(
      snapshot,
      { reporting: extraction([field("noticePeriod", "1 month")]) },
      FILE_NAME,
    );
    expect(outcome.next.reporting.noticeValue).toBe(6);
    expect(outcome.changed.has("brief")).toBe(false);
  });

  it("merges reportsToTitle and directReportTitle into the org chart, under the Reporting screen", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      {
        reporting: extraction([
          field("reportsToTitle", "Group CEO"),
          field("directReportTitle", "Financial Controller"),
        ]),
      },
      FILE_NAME,
    );
    expect(outcome.next.reporting.orgChart.map((node) => node.title)).toEqual(
      expect.arrayContaining(["Group CEO", "Financial Controller"]),
    );
    expect(outcome.changed.has("reporting")).toBe(true);
    expect(outcome.changed.has("brief")).toBe(false);
  });

  it("threads a blocked org-chart merge into skipped, rather than reporting a full chart as a silent success", () => {
    // A chart already at the seat ceiling, so proposing a manager cannot mint one.
    const fullChart: OrgNode[] = [
      mandateSeat,
      ...Array.from({ length: 59 }, (_, i) => ({
        nodeId: `seat-${i}`,
        parentNodeId: mandateSeat.nodeId,
        title: `Seat ${i}`,
        name: null,
        mandateSeat: false,
        canvasX: null,
        canvasY: null,
      })),
    ];
    const snapshot = snapshotOf({ reporting: reporting({ orgChart: fullChart }) });
    const outcome = fillBrief(
      snapshot,
      { reporting: extraction([field("reportsToTitle", "Group CEO")]) },
      FILE_NAME,
    );
    expect(outcome.next.reporting.orgChart).toHaveLength(60);
    expect(outcome.skipped).toContainEqual({ step: "reporting", fieldKey: "orgChart", reason: "full" });
  });

  it("names both the Role Brief and the Reporting screens when a reading touches notice and the chart together", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      {
        reporting: extraction([field("noticePeriod", "2 months"), field("reportsToTitle", "Group CEO")]),
      },
      FILE_NAME,
    );
    expect(outcome.changed).toEqual(new Set(["brief", "reporting"]));
  });
});

describe("compensation: the package a document states is the package (#1049)", () => {
  // The CHRO specification #1049 was raised against, as PositionCompensationProposer packs it.
  const chroPackage = extraction([
    field("currency", "AED", { snippet: "AED90,000" }),
    field("salaryMin", "90000", { snippet: "AED90,000" }),
    field("salaryMax", "120000", { snippet: "AED120,000" }),
    field("bonusValue", "25", { snippet: "Bonus: 25% of total fixed" }),
    field("bonusBasis", "PERCENT_OF_TOTAL_FIXED", { snippet: "25% of total fixed" }),
    field("benefit", "Housing allowance — yearly — 100000", { snippet: "Housing: 100,000 per year" }),
    field("benefit", "Transport allowance — monthly — 400", { snippet: "Transport: 400 per month" }),
  ]);

  it("fills the band, the bonus and the allowances over what the template seeded", () => {
    const { next, changed, receipts } = fillBrief(snapshotOf(), { compensation: chroPackage }, FILE_NAME);

    expect(changed.has("compensation")).toBe(true);
    expect(next.compensation).toMatchObject({
      currency: "AED",
      salaryMin: 90_000,
      salaryMax: 120_000,
      baseSalaryMode: "ANNUAL",
      bonusValue: 25,
      bonusBasis: "PERCENT_OF_TOTAL_FIXED",
    });
    expect(next.compensation.benefits).toEqual([
      { name: "Housing allowance", amount: 100_000, frequency: "YEARLY", source: "DOCUMENT" },
      { name: "Transport allowance", amount: 400, frequency: "MONTHLY", source: "DOCUMENT" },
    ]);
    expect(next.compensation.fieldSources).toMatchObject({
      salaryMin: "DOCUMENT",
      bonusValue: "DOCUMENT",
      bonusBasis: "DOCUMENT",
      baseSalaryMode: "TEMPLATE",
    });
    expect(receipts.compensation?.scalars.salaryMin?.snippet).toBe("AED90,000");
    expect(Object.keys(receipts.compensation?.lists.benefits?.appended ?? {})).toEqual([
      "Housing allowance",
      "Transport allowance",
    ]);
  });

  it("never overwrites a figure a person typed", () => {
    const typed = snapshotOf({
      compensation: compensation({ salaryMin: 80_000, fieldSources: { salaryMin: "MANUAL" } }),
    });

    const { next, skipped } = fillBrief(typed, { compensation: chroPackage }, FILE_NAME);

    expect(next.compensation.salaryMin).toBe(80_000);
    expect(next.compensation.salaryMax).toBe(120_000);
    expect(skipped).toContainEqual({ step: "compensation", fieldKey: "salaryMin", reason: "manual" });
  });

  it("leaves a bonus unread when a kept basis could not hold it", () => {
    const kept = snapshotOf({
      compensation: compensation({ bonusBasis: "MONTHS_OF_BASE", fieldSources: { bonusBasis: "MANUAL" } }),
    });

    const { next, skipped } = fillBrief(
      kept,
      { compensation: extraction([field("bonusValue", "25"), field("bonusBasis", "PERCENT_OF_BASE")]) },
      FILE_NAME,
    );

    expect(next.compensation.bonusBasis).toBe("MONTHS_OF_BASE");
    expect(next.compensation.bonusValue).toBe(40);
    expect(skipped).toContainEqual({ step: "compensation", fieldKey: "bonusValue", reason: "invalid" });
  });

  it("reads an allowance with no stated period or amount by its name alone", () => {
    const { next } = fillBrief(
      snapshotOf(),
      { compensation: extraction([field("benefit", "Medical insurance")]) },
      FILE_NAME,
    );

    expect(next.compensation.benefits).toEqual([
      { name: "Medical insurance", amount: null, frequency: "MONTHLY", source: "DOCUMENT" },
    ]);
  });

  it("drops a currency the brief cannot offer rather than writing it", () => {
    const { next, skipped } = fillBrief(
      snapshotOf(),
      { compensation: extraction([field("currency", "JPY")]) },
      FILE_NAME,
    );

    expect(next.compensation.currency).toBe("AED");
    expect(skipped).toContainEqual({ step: "compensation", fieldKey: "currency", reason: "invalid" });
  });

  it("undoes a filled figure back to the template's", () => {
    const { next, receipts } = fillBrief(snapshotOf(), { compensation: chroPackage }, FILE_NAME);
    const receipt = receipts.compensation;
    if (!receipt) throw new Error("expected a compensation receipt");

    const restored = undoScalar(next.compensation, "bonusValue", receipt);

    expect(restored.bonusValue).toBe(40);
    expect(restored.fieldSources.bonusValue).toBe("TEMPLATE");
  });
});

describe("undo", () => {
  const receiptFor = (fieldKey: string): StepReceipt => ({
    fileName: FILE_NAME,
    scalars: {
      [fieldKey]: {
        previousValue: "Old value",
        previousSource: "TEMPLATE",
        confidence: "high",
        snippet: "snippet",
      },
    },
    lists: {},
  });

  it("restores a scalar still marked DOCUMENT", () => {
    const draft = details({ department: "Group Finance", fieldSources: { department: "DOCUMENT" } });
    const restored = undoScalar(draft, "department", receiptFor("department"));
    expect(restored.department).toBe("Old value");
    expect(restored.fieldSources.department).toBe("TEMPLATE");
  });

  it("is a no-op once an edit since has made the field MANUAL", () => {
    const draft = details({ department: "Somebody typed this", fieldSources: { department: "MANUAL" } });
    const restored = undoScalar(draft, "department", receiptFor("department"));
    expect(restored).toBe(draft);
  });

  it("undoes one half of the location without disturbing the other", () => {
    const draft = details({
      locationCity: "Dubai",
      locationCountry: "United Arab Emirates",
      fieldSources: { locationCity: "DOCUMENT", locationCountry: "DOCUMENT" },
    });
    const restored = undoScalar(draft, "locationCity", receiptFor("locationCity"));
    expect(restored.locationCity).toBe("Old value");
    expect(restored.fieldSources.locationCity).toBe("TEMPLATE");
    expect(restored.locationCountry).toBe("United Arab Emirates");
    expect(restored.fieldSources.locationCountry).toBe("DOCUMENT");
  });

  it("removes one DOCUMENT list item by its text", () => {
    const receipt: StepReceipt = {
      fileName: FILE_NAME,
      scalars: {},
      lists: {
        responsibilities: {
          dropped: [{ text: "Old duty", source: "TEMPLATE" }],
          appended: {
            "duty one": { confidence: "high", snippet: null },
            "duty two": { confidence: "high", snippet: null },
          },
        },
      },
    };
    const draft = details({
      responsibilities: [
        { text: "Duty one", source: "DOCUMENT" },
        { text: "Duty two", source: "DOCUMENT" },
      ],
    });
    const afterFirst = undoListItem(draft, "responsibilities", "Duty one", receipt);
    expect(afterFirst.responsibilities.map((row) => row.text)).toEqual(["Duty two"]);

    // Removing the last DOCUMENT row brings the dropped rows back.
    const afterSecond = undoListItem(afterFirst, "responsibilities", "Duty two", receipt);
    expect(afterSecond.responsibilities).toEqual([{ text: "Old duty", source: "TEMPLATE" }]);
  });

  it("undoStep reverses every scalar and list a single-object receipt recorded", () => {
    const receipt: StepReceipt = {
      fileName: FILE_NAME,
      scalars: {
        teamSize: {
          previousValue: "8",
          previousSource: "TEMPLATE",
          confidence: "high",
          snippet: null,
        },
      },
      lists: {},
    };
    const draft = reporting({ teamSize: "12", fieldSources: { teamSize: "DOCUMENT" } });
    const restored = undoStep(draft, receipt);
    expect(restored.teamSize).toBe("8");
    expect(restored.fieldSources.teamSize).toBe("TEMPLATE");
  });
});

describe("fillBrief overall shape", () => {
  it("only reports the screens it actually touched as changed", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(
      snapshot,
      { details: extraction([field("department", "Group Finance")]) },
      FILE_NAME,
    );
    expect(outcome.changed).toEqual(new Set(["brief"]));
    expect(outcome.receipts.reporting).toBeUndefined();
    expect(outcome.receipts.assessment).toBeUndefined();
  });

  it("touches nothing when a section was never read", () => {
    const snapshot = snapshotOf();
    const outcome = fillBrief(snapshot, {}, FILE_NAME);
    expect(outcome.changed.size).toBe(0);
    expect(outcome.next).toEqual(snapshot);
  });
});
