import { describe, expect, it } from "vitest";
import type { Position } from "../api/types";
import { POSITION_STEPS, completion, panelTotal, stepIndexOf } from "./steps";

const blank: Position = {
  details: {
    roleTitle: "Chief Financial Officer",
    department: null,
    location: null,
    employmentType: null,
    seniority: null,
    responsibilities: [],
    narrative: null,
  },
  context: {
    mandateReason: "NEW_ROLE",
    businessDriver: null,
    strategicPriorities: [],
    hiringUrgency: "STANDARD",
    confidential: false,
    internalContext: null,
  },
  reporting: {
    reportsToName: null,
    reportsTo: null,
    directReports: [],
    teamSize: null,
    targetStart: null,
    noticeValue: null,
    noticeUnit: null,
  },
  compensation: {
    currency: "AED",
    salaryMin: null,
    salaryMax: null,
    baseSalaryMode: "ANNUAL",
    bonusValue: null,
    bonusBasis: null,
    incentiveType: null,
    incentiveAmount: null,
    incentiveVesting: null,
    benefits: [],
  },
  assessment: { criteria: [], technical: [], behavioural: [] },
  publication: { publishedAt: null, publishedBy: null },
  document: null,
};

const stepNamed = (key: string) => POSITION_STEPS[stepIndexOf(key as never)];

describe("step completion", () => {
  it("counts step one done only once the role is placed as well as titled", () => {
    const details = stepNamed("details");
    expect(details.isDone(blank)).toBe(false);
    expect(
      details.isDone({
        ...blank,
        details: { ...blank.details, department: "Group Finance", location: "Abu Dhabi" },
      }),
    ).toBe(true);
  });

  it("counts the reporting step done only when the line and at least one seat are named", () => {
    const reporting = stepNamed("reporting");
    const withLine = {
      ...blank,
      reporting: { ...blank.reporting, reportsTo: "Group CEO" },
    };
    expect(reporting.isDone(withLine)).toBe(false);
    expect(
      reporting.isDone({
        ...withLine,
        reporting: { ...withLine.reporting, directReports: [{ title: "Controller", name: null }] },
      }),
    ).toBe(true);
  });

  it("counts the assessment step done only when both panels total exactly 100", () => {
    const assessment = stepNamed("assessment");
    const ninety = {
      ...blank,
      assessment: {
        criteria: [],
        technical: [{ name: "T", description: null, weight: 90 }],
        behavioural: [{ name: "B", description: null, weight: 100 }],
      },
    };
    expect(assessment.isDone(ninety)).toBe(false);
    expect(panelTotal(ninety, "technical")).toBe(90);
    expect(
      assessment.isDone({
        ...ninety,
        assessment: {
          ...ninety.assessment,
          technical: [{ name: "T", description: null, weight: 100 }],
        },
      }),
    ).toBe(true);
  });

  it("counts the last step done only once the brief is published", () => {
    const review = stepNamed("review");
    expect(review.isDone(blank)).toBe(false);
    expect(
      review.isDone({
        ...blank,
        publication: { publishedAt: "2026-08-27T10:00:00Z", publishedBy: "Alok Kumar" },
      }),
    ).toBe(true);
  });
});

describe("completion", () => {
  it("is done steps out of six", () => {
    expect(completion(blank)).toBe(0);
    expect(
      completion({
        ...blank,
        details: { ...blank.details, department: "Group Finance", location: "Abu Dhabi" },
      }),
    ).toBe(17);
  });
});

describe("rail readings", () => {
  it("says what step four holds, or that it is still waiting", () => {
    const compensation = stepNamed("compensation");
    expect(compensation.summary(blank)).toBe("Awaiting package input");
    expect(
      compensation.summary({
        ...blank,
        compensation: { ...blank.compensation, salaryMin: 1_200_000, salaryMax: 1_500_000 },
      }),
    ).toBe("AED 1,200K – AED 1,500K");
  });

  it("names an untitled role rather than rendering an empty line", () => {
    expect(stepNamed("details").summary({ ...blank, details: { ...blank.details, roleTitle: " " } }))
      .toBe("Untitled role");
  });
});
