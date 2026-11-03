import { describe, expect, it } from "vitest";
import { placeholderChange, placeholdersIn } from "./placeholders";

describe("placeholders", () => {
  it("reads each {name} once, sorted, and ignores braces that are not a name", () => {
    expect(placeholdersIn("{hiring} then {brief}, {hiring} again, {not a name} and {}")).toEqual(["brief", "hiring"]);
  });

  it("names what a draft dropped and what it added", () => {
    expect(placeholderChange(["brief", "hiring"], "Only {hiring} and {tone}")).toEqual({
      missing: ["brief"],
      added: ["tone"],
    });
    expect(placeholderChange([], "No braces at all")).toEqual({ missing: [], added: [] });
  });
});
