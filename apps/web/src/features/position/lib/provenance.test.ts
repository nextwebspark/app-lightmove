import { describe, expect, it } from "vitest";
import type { Position } from "../api/types";
import { isUntouched } from "./provenance";

const templateBrief: Position = {
  details: {
    roleTitle: "Chief Financial Officer",
    department: "Finance",
    locationCity: null,
    locationCountry: "United Arab Emirates",
    employmentType: "FULL_TIME_PERMANENT",
    seniority: "C_SUITE",
    responsibilities: [{ text: "Group P&L stewardship", source: "TEMPLATE" }],
    narrative: "A hands-on CFO.",
    fieldSources: { department: "TEMPLATE", narrative: "TEMPLATE" },
  },
  context: {
    mandateReason: "NEW_ROLE",
    businessDriver: null,
    strategicPriorities: [{ name: "Capital discipline", selected: false, source: "TEMPLATE" }],
    confidential: false,
    internalContext: null,
    fieldSources: {},
  },
  reporting: {
    orgChart: [
      { nodeId: "m", parentNodeId: null, title: "Group CEO", name: null, mandateSeat: false, canvasX: null, canvasY: null, source: "TEMPLATE" },
      { nodeId: "s", parentNodeId: "m", title: null, name: null, mandateSeat: true, canvasX: null, canvasY: null, source: "TEMPLATE" },
    ],
    teamSize: null,
    targetStart: null,
    noticeValue: 3,
    noticeUnit: "MONTHS",
    fieldSources: { noticeValue: "TEMPLATE", noticeUnit: "TEMPLATE" },
  },
  compensation: {
    currency: "USD",
    salaryMin: null,
    salaryMax: null,
    baseSalaryMode: "ANNUAL",
    bonusValue: null,
    bonusBasis: null,
    incentiveType: null,
    incentiveAmount: null,
    incentiveVesting: null,
    benefits: [{ name: "Housing", amount: null, frequency: "MONTHLY", source: "TEMPLATE" }],
  },
  assessment: {
    criteria: [{ text: "Board reporting", mode: "REQUIRED", source: "TEMPLATE" }],
    technical: [{ name: "Treasury", description: null, weight: 100, source: "TEMPLATE" }],
    behavioural: [{ name: "Leadership", description: null, weight: 100, source: "TEMPLATE" }],
    technicalShare: 50,
  },
  publication: { publishedAt: null, publishedBy: null },
  document: null,
};

describe("isUntouched", () => {
  it("is true for a brief the template drafted and a reading filled", () => {
    expect(isUntouched(templateBrief)).toBe(true);
    expect(
      isUntouched({
        ...templateBrief,
        details: { ...templateBrief.details, fieldSources: { ...templateBrief.details.fieldSources, locationCity: "DOCUMENT" } },
      }),
    ).toBe(true);
  });

  it("treats a field nobody has claimed as untouched, not typed", () => {
    expect(isUntouched({ ...templateBrief, context: { ...templateBrief.context, fieldSources: {} } })).toBe(true);
  });

  it("is false once a person has typed a field", () => {
    expect(
      isUntouched({
        ...templateBrief,
        reporting: { ...templateBrief.reporting, fieldSources: { noticeValue: "MANUAL", noticeUnit: "MANUAL" } },
      }),
    ).toBe(false);
  });

  it("is false once a person has added a row to any list", () => {
    expect(
      isUntouched({
        ...templateBrief,
        compensation: {
          ...templateBrief.compensation,
          benefits: [...templateBrief.compensation.benefits, { name: "School fees", amount: null, frequency: "YEARLY", source: "MANUAL" }],
        },
      }),
    ).toBe(false);
  });

  it("reads a row with no recorded source as a person's, as the document fill does", () => {
    expect(
      isUntouched({
        ...templateBrief,
        assessment: {
          ...templateBrief.assessment,
          technical: [{ name: "Treasury", description: null, weight: 100 }],
        },
      }),
    ).toBe(false);
  });
});
