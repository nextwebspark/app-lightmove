import { describe, expect, it } from "vitest";
import type { CandidatePhone } from "../../candidates/api/types";
import { addedCallingCodeOf, dialHrefOf, whatsappHrefOf } from "./phoneLinks";

const phone = (patch: Partial<CandidatePhone>): CandidatePhone => ({
  number: "050 441 2087",
  kind: null,
  verified: false,
  status: null,
  source: "manual",
  foundAt: "2026-09-16T17:27:24Z",
  ...patch,
});

describe("phone links", () => {
  it("dials the international number where the server read one", () => {
    const local = phone({ international: "+971 50 441 2087", countrySource: "location", lineType: "mobile" });
    expect(dialHrefOf(local)).toBe("tel:+971504412087");
    expect(whatsappHrefOf(local)).toBe("https://wa.me/971504412087");
    expect(addedCallingCodeOf(local)).toBe("+971");
  });

  it("falls back to the written digits, a 00 read as +, an extension dropped", () => {
    expect(dialHrefOf(phone({}))).toBe("tel:0504412087");
    expect(dialHrefOf(phone({ number: "00971 50 441 2087" }))).toBe("tel:+971504412087");
    expect(dialHrefOf(phone({ number: "+974 5512 8870 ext 12" }))).toBe("tel:+97455128870");
    expect(dialHrefOf(phone({ number: "ask the office" }))).toBeNull();
  });

  it("opens no chat without a calling code, nor for a landline", () => {
    expect(whatsappHrefOf(phone({}))).toBeNull();
    expect(whatsappHrefOf(phone({ international: "+971 4 330 0000", countrySource: "location", lineType: "landline" }))).toBeNull();
  });

  it("names an added calling code only, never one the number was written with", () => {
    expect(addedCallingCodeOf(phone({ international: "+971 50 441 2087", countrySource: "given" }))).toBeNull();
    expect(addedCallingCodeOf(phone({ international: "+44 7400 123456", countrySource: "workspace" }))).toBe("+44");
    expect(addedCallingCodeOf(phone({}))).toBeNull();
  });
});
