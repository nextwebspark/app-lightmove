import { describe, expect, it } from "vitest";
import type { RecipientTokens } from "../api/sequenceApi";
import { replaceVariable } from "./sequenceTokens";
import { chipOf, piecesOf } from "./variableChips";

const TOKENS: RecipientTokens = {
  firstName: "Fatima",
  currentTitle: "CFO",
  currentCompany: "  ",
  positionTitle: "Group CFO",
  location: null,
  senderFirstName: "Yara",
  bookingLink: null,
};

describe("chipOf", () => {
  it("reads a value, a fallback standing in, and a blank by its label", () => {
    expect(chipOf("firstName", null, TOKENS, "Fatima")).toMatchObject({ kind: "value", text: "Fatima" });
    expect(chipOf("currentCompany", "your firm", TOKENS, "Fatima")).toMatchObject({ kind: "fallback", text: "your firm" });
    expect(chipOf("location", null, TOKENS, "Fatima")).toMatchObject({ kind: "blank", text: "Location" });
  });

  it("leaves a name no email fills as typed, so a misspelling shows", () => {
    expect(chipOf("firstNmae", null, TOKENS, "Fatima")).toBeNull();
    expect(chipOf("opener", null, TOKENS, "Fatima")).toBeNull();
  });

  it("draws the booking link as made for the sender, or as it is once it exists", () => {
    expect(chipOf("bookingLink", null, TOKENS, "Fatima")).toMatchObject({ kind: "value", text: "(your booking link)" });
    expect(chipOf("bookingLink", null, { ...TOKENS, bookingLink: "https://x.test/b/yara" }, "Fatima")).toMatchObject({
      text: "https://x.test/b/yara",
    });
  });
});

describe("piecesOf", () => {
  it("cuts a line into words and chips, keeping what no email fills as words", () => {
    const pieces = piecesOf("For {{firstName}}, {{opener}} {{location|there}}", TOKENS, "Fatima");
    expect(pieces.map((piece) => piece.text).join("")).toBe("For Fatima, {{opener}} there");
    expect(pieces.filter((piece) => "chip" in piece)).toHaveLength(2);
  });
});

describe("replaceVariable", () => {
  it("gives every use of one variable the words, a fallback and its pipe included, and leaves the rest", () => {
    expect(replaceVariable("In {{location}} or {{ location|the region }}, {{firstName}}", "location", "Dubai")).toBe(
      "In Dubai or Dubai, {{firstName}}",
    );
  });

  it("takes a variable out with an empty replacement", () => {
    expect(replaceVariable("Still in {{location}}?", "location", "")).toBe("Still in ?");
  });
});
