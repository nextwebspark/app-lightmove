import { describe, expect, it } from "vitest";
import { CURRENCIES, currencyGroupsOf, currencyOptionLabel } from "./currencies";

const SERVED = [
  { code: "AED", name: "United Arab Emirates Dirham" },
  { code: "INR", name: "Indian Rupee" },
  { code: "SAR", name: "Saudi Riyal" },
  { code: "ZAR", name: "South African Rand" },
];

describe("currencyGroupsOf", () => {
  it("lists the held codes first, then the suggested nine, each once", () => {
    const { suggested } = currencyGroupsOf(["INR", null, "SAR"], SERVED);

    expect(suggested[0]).toBe("INR");
    expect(suggested).toHaveLength(CURRENCIES.length + 1);
    expect(new Set(suggested).size).toBe(suggested.length);
  });

  it("puts every other served currency below, never repeating a suggested one", () => {
    const { others } = currencyGroupsOf(["INR"], SERVED);

    expect(others.map((currency) => currency.code)).toEqual(["ZAR"]);
  });

  it("keeps a held code the server no longer offers", () => {
    expect(currencyGroupsOf(["ATS"], SERVED).suggested).toContain("ATS");
  });
});

describe("currencyOptionLabel", () => {
  it("prefers the short name, then the served one, then the bare code", () => {
    expect(currencyOptionLabel("AED", "United Arab Emirates Dirham")).toBe("AED - UAE Dirham");
    expect(currencyOptionLabel("INR", "Indian Rupee")).toBe("INR - Indian Rupee");
    expect(currencyOptionLabel("XYZ")).toBe("XYZ");
  });
});
