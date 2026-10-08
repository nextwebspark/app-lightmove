import { describe, expect, it } from "vitest";
import type { AssistantQuestion } from "../api/types";
import { composeAnswers, MAX_OTHER_LENGTH, withoutRecommendation } from "./questionAnswers";

const REGION: AssistantQuestion = {
  question: "Which markets?",
  header: "Region",
  multiSelect: false,
  options: [
    { label: "GCC only (Recommended)", description: "The six Gulf states" },
    { label: "MENA", description: "Adds Egypt" },
  ],
};

describe("the answers a question card sends", () => {
  it("drops the model's recommendation mark, whatever its case", () => {
    expect(withoutRecommendation("GCC only (Recommended)")).toBe("GCC only");
    expect(withoutRecommendation("GCC only (recommended) ")).toBe("GCC only");
    expect(withoutRecommendation("Recommended partners")).toBe("Recommended partners");
  });

  it("is nothing until every question is answered", () => {
    expect(composeAnswers([REGION, REGION], { 0: ["MENA"] }, {})).toBe("");
  });

  it("cuts typed text to the box's limit", () => {
    const typed = "x".repeat(MAX_OTHER_LENGTH + 50);

    expect(composeAnswers([REGION], {}, { 0: typed })).toBe(`Region: ${"x".repeat(MAX_OTHER_LENGTH)}`);
  });
});
