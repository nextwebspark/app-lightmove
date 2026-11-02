import { describe, expect, it } from "vitest";
import { erasedToast, lastActivityPhrase, linkedinProfileProblem, namesSentence, retentionNote } from "./dataRetention";

describe("dataRetention", () => {
  it("names one, two or many people as a sentence", () => {
    expect(namesSentence(["Dina"])).toBe("Dina");
    expect(namesSentence(["Dina", "Karim"])).toBe("Dina and Karim");
    expect(namesSentence(["Dina", "Karim", "Rami"])).toBe("Dina, Karim and Rami");
  });

  it("says who a delete left behind, and why", () => {
    expect(erasedToast({ erased: 2, noLongerDue: 0, sendInFlight: 0 })).toBe("2 candidates deleted permanently");
    expect(erasedToast({ erased: 0, noLongerDue: 1, sendInFlight: 2 })).toBe(
      "0 candidates deleted permanently · 1 person no longer due, 2 people still being emailed kept",
    );
  });

  it("phrases a period, never, and a last activity it does not know", () => {
    expect(retentionNote(24)).toContain("keeps them for another 24 months");
    expect(retentionNote(null)).toContain("Retention is off");
    expect(lastActivityPhrase(null)).toBe("Added");
    expect(lastActivityPhrase("ASSESSMENT_REVIEWED")).toBe("Record updated");
  });

  it("takes a member profile link with or without its scheme", () => {
    expect(linkedinProfileProblem("")).toBeNull();
    expect(linkedinProfileProblem("https://www.linkedin.com/in/rami-saleh/")).toBeNull();
    expect(linkedinProfileProblem("linkedin.com/in/rami-saleh")).toBeNull();
    expect(linkedinProfileProblem("linkedin.com/company/acme")).not.toBeNull();
  });
});
