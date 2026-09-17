import { describe, expect, it } from "vitest";
import { memberOf, membersOf } from "./nationalityWording";

/** A Gulf group is a nationality and reads as one; an expat group is not, and must not be called one. */
describe("nationalityWording", () => {
  it("calls a Gulf group nationals and an expat group executives", () => {
    expect(membersOf("Saudi")).toBe("Saudi nationals");
    expect(membersOf("Western expat")).toBe("Western expat executives");
  });

  it("takes the article the group's first sound asks for", () => {
    expect(memberOf("Kuwaiti")).toBe("a Kuwaiti national");
    expect(memberOf("Emirati")).toBe("an Emirati national");
    expect(memberOf("Arab expat, non-GCC")).toBe("an Arab expat, non-GCC executive");
  });
});
