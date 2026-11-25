import { describe, expect, it } from "vitest";
import { lineDiff } from "./lineDiff";

describe("lineDiff", () => {
  it("marks a changed line as one removed and one added, in reading order", () => {
    expect(lineDiff("Score each competency.\nBe brief.", "Score each competency.\nBe specific.")).toEqual([
      { kind: "same", text: "Score each competency." },
      { kind: "removed", text: "Be brief." },
      { kind: "added", text: "Be specific." },
    ]);
  });

  it("answers nothing for two texts that only differ in line endings", () => {
    expect(lineDiff("One.\r\nTwo.", "One.\nTwo.")).toEqual([]);
  });

  it("leaves out unchanged lines far from a change, with one gap where they were", () => {
    const before = ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"];
    const after = [...before];
    after[0] = "A";
    after[9] = "J";

    expect(lineDiff(before.join("\n"), after.join("\n")).map((line) => `${line.kind}:${line.text}`)).toEqual([
      "removed:a",
      "added:A",
      "same:b",
      "same:c",
      "gap:",
      "same:h",
      "same:i",
      "removed:j",
      "added:J",
    ]);
  });
});
