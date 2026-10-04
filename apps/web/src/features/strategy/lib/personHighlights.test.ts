import { describe, expect, it } from "vitest";
import type { PersonResult } from "../api/types";
import { currentTenure, previousRoles, signalsOf, totalYears } from "./personHighlights";

const NOW = new Date(2026, 8, 30);

const career = [
  { company: "Harbour Group", title: "Chief Financial Officer", period: "2021 – Present" },
  { company: "Farro Capital", title: "Finance Director", period: "2015 – 2021" },
  { company: "Big Four", title: "Senior Manager", period: "2008 – 2015" },
];

const person = (overrides: Partial<PersonResult>): PersonResult => ({
  linkedinSlug: "p",
  fullName: "P",
  title: null,
  companyName: null,
  companyLinkedinUrl: null,
  companyLogoUrl: null,
  location: null,
  countryCode: null,
  photoUrl: null,
  profileUrl: null,
  about: null,
  career: [],
  education: [],
  skills: [],
  languages: [],
  details: null,
  candidateId: null,
  held: false,
  ...overrides,
});

describe("personHighlights", () => {
  it("reads the seat held now and how long it has been held", () => {
    expect(currentTenure(career, NOW)).toEqual({ since: 2021, length: "5 yrs" });
  });

  it("has no current tenure when every post has ended", () => {
    expect(currentTenure([career[1]], NOW)).toBeNull();
  });

  it("counts a career from its earliest start", () => {
    expect(totalYears(career, NOW)).toBe(18);
    expect(totalYears([{ company: "X", title: "Y", period: null }], NOW)).toBeNull();
  });

  it("lists where they came from, leaving out the post held now", () => {
    expect(previousRoles(career, 2).map((post) => post.title)).toEqual(["Finance Director", "Senior Manager"]);
  });

  it("puts Arabic first among the chips, caps them at three and counts the rest", () => {
    const signals = signalsOf(
      person({
        education: [{ school: "Sample University", degree: "MBA", period: null }],
        languages: ["English", "French", "Arabic"],
      }),
    );
    expect(signals.chips).toEqual(["Sample University", "Arabic", "English"]);
    expect(signals.overflow).toBe(1);
  });
});
