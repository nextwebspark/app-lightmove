import { describe, expect, it } from "vitest";
import type { Chip } from "../api/types";
import { capChips, mergeSuggestions, sameChips, withoutDirectDupes } from "./mergeSuggestions";

describe("mergeSuggestions", () => {
  it("adds a brand-new suggestion pre-selected", () => {
    const merged = mergeSuggestions([], ["Wholesale"]);
    expect(merged).toEqual([{ label: "Wholesale", selected: true }]);
  });

  it("keeps an explicit deselection when the label is suggested again", () => {
    const current: Chip[] = [{ label: "Wholesale", selected: false }];
    const merged = mergeSuggestions(current, ["Wholesale"]);
    expect(merged).toEqual([{ label: "Wholesale", selected: false }]);
  });

  it("drops a chip that is no longer suggested, even if it was selected", () => {
    // Its source direct sector is gone; a suggestion still produced elsewhere would reappear instead.
    const current: Chip[] = [{ label: "Agribusiness", selected: true }];
    const merged = mergeSuggestions(current, ["Wholesale"]);
    expect(merged).toEqual([{ label: "Wholesale", selected: true }]);
  });

  it("drops a deselected chip that is no longer suggested", () => {
    const current: Chip[] = [{ label: "Cold Chain", selected: false }];
    const merged = mergeSuggestions(current, ["Wholesale"]);
    expect(merged).toEqual([{ label: "Wholesale", selected: true }]);
  });

  it("keeps a still-suggested chip regardless of order or selection", () => {
    const current: Chip[] = [
      { label: "Wholesale", selected: false },
      { label: "Agribusiness", selected: true },
    ];
    // Both remain suggested → both survive with their flags; a dropped one would be gone.
    const merged = mergeSuggestions(current, ["Agribusiness", "Wholesale"]);
    expect(merged).toEqual([
      { label: "Agribusiness", selected: true },
      { label: "Wholesale", selected: false },
    ]);
  });

  it("preserves suggestion order and matches labels case-insensitively", () => {
    const current: Chip[] = [{ label: "wholesale", selected: false }];
    const merged = mergeSuggestions(current, ["Consumer Goods", "Wholesale"]);
    expect(merged.map((c) => c.label)).toEqual(["Consumer Goods", "wholesale"]);
    expect(merged[1].selected).toBe(false);
  });
});

describe("withoutDirectDupes", () => {
  it("drops chips whose label matches a direct sector, case-insensitively", () => {
    const chips: Chip[] = [
      { label: "Retail", selected: true },
      { label: "Wholesale", selected: false },
    ];
    expect(withoutDirectDupes(chips, ["retail"])).toEqual([{ label: "Wholesale", selected: false }]);
  });
});

describe("capChips", () => {
  it("leaves a list at or under the cap untouched", () => {
    const chips: Chip[] = [{ label: "A", selected: true }];
    expect(capChips(chips, 3)).toEqual(chips);
  });

  it("drops trailing deselected chips first, keeping every selected chip", () => {
    const chips: Chip[] = [
      { label: "A", selected: true },
      { label: "B", selected: false },
      { label: "C", selected: true },
      { label: "D", selected: false },
    ];
    // Cap of 2: drop the two deselected (B, D) from the tail inward, keeping A and C.
    expect(capChips(chips, 2)).toEqual([
      { label: "A", selected: true },
      { label: "C", selected: true },
    ]);
  });

  it("never drops a selected chip even if that leaves the list over the cap", () => {
    const chips: Chip[] = [
      { label: "A", selected: true },
      { label: "B", selected: true },
      { label: "C", selected: true },
    ];
    expect(capChips(chips, 2)).toEqual(chips);
  });
});

describe("sameChips", () => {
  it("is true for identical lists and false when selection or order differs", () => {
    const a: Chip[] = [{ label: "X", selected: true }];
    expect(sameChips(a, [{ label: "X", selected: true }])).toBe(true);
    expect(sameChips(a, [{ label: "X", selected: false }])).toBe(false);
    expect(sameChips(a, [])).toBe(false);
  });
});
