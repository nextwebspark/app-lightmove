import { describe, expect, it } from "vitest";
import type { Position } from "../api/types";
import {
  POSITION_STEPS,
  completion,
  doneSteps,
  openingStepOf,
  placeLineOf,
  readinessOf,
  stepIndexOf,
  stepOf,
  type StepKey,
} from "./steps";

const blank: Position = {
  details: {
    roleTitle: "Chief Financial Officer",
    department: null,
    locationCity: null,
    locationCountry: null,
    employmentType: null,
    seniority: null,
    responsibilities: [],
    narrative: null,
    fieldSources: {},
  },
  context: {
    mandateReason: "NEW_ROLE",
    businessDriver: null,
    strategicPriorities: [],
    confidential: false,
    internalContext: null,
    fieldSources: {},
  },
  reporting: {
    orgChart: [
      { nodeId: "n-seat", parentNodeId: null, title: null, name: null, mandateSeat: true, canvasX: null, canvasY: null },
    ],
    teamSize: null,
    targetStart: null,
    noticeValue: null,
    noticeUnit: null,
    fieldSources: {},
  },
  compensation: {
    currency: "SAR",
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
  assessment: { criteria: [], technical: [], behavioural: [], technicalShare: 50 },
  publication: { publishedAt: null, publishedBy: null },
  document: null,
};

const charted: Position = {
  ...blank,
  reporting: {
    ...blank.reporting,
    orgChart: [
      { nodeId: "n-manager", parentNodeId: null, title: "Group CEO", name: "Mohammed Rashed", mandateSeat: false, canvasX: null, canvasY: null },
      { nodeId: "n-seat", parentNodeId: "n-manager", title: null, name: null, mandateSeat: true, canvasX: null, canvasY: null },
      { nodeId: "n-report", parentNodeId: "n-seat", title: "HR Analyst", name: null, mandateSeat: false, canvasX: null, canvasY: null },
    ],
  },
};

const step = (key: StepKey) => POSITION_STEPS[stepIndexOf(key)];

describe("the five steps", () => {
  it("counts the brief done once the role is titled and placed, city or country", () => {
    expect(step("brief").isDone(blank)).toBe(false);
    expect(step("brief").attention(blank)).toBe("No location yet — name the city or the country.");
    const placed = { ...blank, details: { ...blank.details, locationCountry: "Saudi Arabia" } };
    expect(step("brief").isDone(placed)).toBe(true);
    expect(step("brief").attention(placed)).toBeNull();
  });

  it("wants a named manager and at least one report before reporting is done", () => {
    expect(step("reporting").isDone(blank)).toBe(false);
    expect(step("reporting").attention(blank)).toBe("Nobody is named as the manager yet.");
    expect(step("reporting").isDone(charted)).toBe(true);
    expect(step("reporting").attention(charted)).toBeNull();
  });

  it("calls compensation done only with a band and every allowance quantified", () => {
    const banded = { ...blank, compensation: { ...blank.compensation, salaryMin: 32_000, salaryMax: 37_000, baseSalaryMode: "MONTHLY" as const } };
    expect(step("compensation").isDone(banded)).toBe(true);
    expect(step("compensation").attention(banded)).toBeNull();

    const unquantified = {
      ...banded,
      compensation: { ...banded.compensation, benefits: [{ name: "Housing allowance", amount: null, frequency: "YEARLY" as const }] },
    };
    expect(step("compensation").isDone(unquantified)).toBe(false);
    expect(step("compensation").attention(unquantified)).toBe(
      "Allowances not fully quantified — give every benefit a figure.",
    );
    expect(step("compensation").attention(blank)).toBe("No base salary band yet.");
  });

  it("is done when both panels total 100, and says which does not", () => {
    const weighted = {
      ...blank,
      assessment: {
        criteria: [],
        technical: [{ name: "Treasury", description: null, weight: 60 }, { name: "Controls", description: null, weight: 40 }],
        behavioural: [{ name: "Leadership", description: null, weight: 90 }],
        technicalShare: 60,
      },
    };
    expect(step("assessment").isDone(weighted)).toBe(false);
    expect(step("assessment").attention(weighted)).toBe("Behavioural weights total 90%, not 100%.");
  });

  it("reads publication as the review's own state", () => {
    expect(step("review").isDone(blank)).toBe(false);
    expect(step("review").attention(blank)).toBeNull();
    const published = { ...blank, publication: { publishedAt: "2026-09-09T10:00:00Z", publishedBy: "Alok Kumar" } };
    expect(step("review").isDone(published)).toBe(true);
  });
});

describe("where the brief opens", () => {
  it("opens on the step the URL names, else where the screen opened", () => {
    expect(stepOf("compensation", "brief").key).toBe("compensation");
    expect(stepOf(null, "brief").key).toBe("brief");
    expect(stepOf("nowhere", "review").key).toBe("review");
  });

  it("opens a published brief on its review, and anything else on the Role Brief", () => {
    expect(openingStepOf(blank)).toBe("brief");
    expect(openingStepOf({ ...blank, publication: { publishedAt: "2026-09-09T10:00:00Z", publishedBy: null } })).toBe(
      "review",
    );
  });
});

describe("completion", () => {
  it("counts done steps out of five", () => {
    expect(doneSteps(blank)).toEqual([false, false, false, false, false]);
    expect(completion(blank)).toBe(0);
    expect(completion(charted)).toBe(20);
  });

  it("reports the three publication checks without gating anything", () => {
    const checks = readinessOf(blank);
    expect(checks.map((check) => check.met)).toEqual([false, false, false]);
    expect(checks[1].label).toBe("Compensation package captured with allowances fully quantified");
  });

  it("joins the two halves of the place into one line", () => {
    expect(placeLineOf(blank)).toBeNull();
    expect(placeLineOf({ ...blank, details: { ...blank.details, locationCity: "Riyadh", locationCountry: "Saudi Arabia" } })).toBe(
      "Riyadh, Saudi Arabia",
    );
  });
});
