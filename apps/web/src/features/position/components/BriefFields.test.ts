import { describe, expect, it } from "vitest";
import { figureText } from "./BriefFields";

describe("figureText", () => {
  it("keeps digits only for a whole figure", () => {
    expect(figureText("AED 1,250,000", 0)).toBe("1250000");
  });

  it("stops at fifteen digits, where a number stops holding what was typed", () => {
    expect(figureText("12345678901234567890", 0)).toBe("123456789012345");
  });

  it("keeps one point and the places allowed after it", () => {
    expect(figureText("7.5", 2)).toBe("7.5");
    expect(figureText("7.", 2)).toBe("7.");
    expect(figureText("7.5.5", 2)).toBe("7.55");
    expect(figureText("12.3456", 2)).toBe("12.34");
  });

  it("drops the point where decimals are not allowed", () => {
    expect(figureText("7.5", 0)).toBe("75");
  });
});
