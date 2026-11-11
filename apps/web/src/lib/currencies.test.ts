import { describe, expect, it } from "vitest";
import { CURRENCIES, currencyChoicesOf, currencyNameOf, currencyOptionLabel } from "./currencies";

const SERVED = [
  { code: "AED", name: "United Arab Emirates Dirham" },
  { code: "INR", name: "Indian Rupee" },
  { code: "SAR", name: "Saudi Riyal" },
  { code: "ZAR", name: "South African Rand" },
];

const codesOf = (groups: ReturnType<typeof currencyChoicesOf>) =>
  groups.map((group) => [group.label, group.currencies.map((currency) => currency.code)]);

describe("currencyChoicesOf", () => {
  it("lists the held codes first, then the suggested nine, then every other served one, each once", () => {
    const [suggested, others] = currencyChoicesOf(["INR", null, "SAR"], SERVED);

    expect(suggested.label).toBe("Suggested");
    expect(suggested.currencies[0].code).toBe("INR");
    expect(suggested.currencies).toHaveLength(CURRENCIES.length + 1);
    expect(others.currencies.map((currency) => currency.code)).toEqual(["ZAR"]);
  });

  it("keeps a held code the server no longer offers", () => {
    expect(currencyChoicesOf(["ATS"], SERVED)[0].currencies[0]).toEqual({ code: "ATS", name: "" });
  });

  it("finds a currency by code first, then by name, in either group", () => {
    expect(codesOf(currencyChoicesOf([], SERVED, "sa"))).toEqual([["Suggested", ["SAR"]]]);
    expect(codesOf(currencyChoicesOf([], SERVED, "rand"))).toEqual([["All currencies", ["ZAR"]]]);
    expect(codesOf(currencyChoicesOf([], SERVED, "rupee"))).toEqual([["All currencies", ["INR"]]]);
    expect(codesOf(currencyChoicesOf([], SERVED, "emirates"))).toEqual([["Suggested", ["AED"]]]);
    expect(currencyChoicesOf([], SERVED, "zzz")).toEqual([]);
  });
});

describe("currency names", () => {
  it("prefers the short name, then the served one, then none", () => {
    expect(currencyNameOf("AED", SERVED)).toBe("UAE Dirham");
    expect(currencyNameOf("INR", SERVED)).toBe("Indian Rupee");
    expect(currencyNameOf("XYZ", SERVED)).toBeNull();
    expect(currencyOptionLabel("AED")).toBe("AED - UAE Dirham");
    expect(currencyOptionLabel("XYZ")).toBe("XYZ");
  });
});
