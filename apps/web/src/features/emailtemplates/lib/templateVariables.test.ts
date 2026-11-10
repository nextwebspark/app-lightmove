import { describe, expect, it } from "vitest";
import { templateVocabulary, variableProblemOf } from "./templateVariables";

describe("template variables", () => {
  const vocabulary = templateVocabulary(true);

  it("offers the sequence variables in the sequence bar's order, never the opener", () => {
    expect(vocabulary).toEqual([
      "firstName",
      "currentTitle",
      "currentCompany",
      "positionTitle",
      "location",
      "senderFirstName",
      "bookingLink",
    ]);
    expect(templateVocabulary(false)).not.toContain("bookingLink");
  });

  it("accepts every variable, with a fallback", () => {
    expect(variableProblemOf("Hi {{firstName|there}}, {{ currentTitle }} {{bookingLink}}", vocabulary)).toBeNull();
  });

  it("says what is wrong with a variable no email can fill", () => {
    expect(variableProblemOf("Bye {{signature}}", vocabulary)).toMatch(/Include signature/);
    expect(variableProblemOf("{{opener}}", vocabulary)).toMatch(/sequence/);
    expect(variableProblemOf("Hi {{fristName}}", vocabulary)).toMatch(/\{\{fristName\}\}/);
    expect(variableProblemOf("{{bookingLink|here}}", vocabulary)).toMatch(/fallback/);
    expect(variableProblemOf("{{bookingLink}}", templateVocabulary(false))).toMatch(/Insert bar/);
  });
});
