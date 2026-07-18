import type { Chip } from "../api/types";

/**
 * Reconciles a freshly computed suggestion list with the chips already on screen. Suggestions are
 * *derived* from the current direct sectors: the result is exactly the suggested set, in ranked order,
 * each chip keeping its current selected flag if it is already on screen (an explicit deselection
 * survives a recompute) or arriving newly selected. A chip that is no longer suggested is dropped —
 * even if selected — because its source direct sector is gone; a suggestion still produced by another
 * selected direct simply reappears in the new set and survives.
 *
 * Labels are matched case-insensitively — they are verbatim taxonomy strings, never retyped.
 */
export function mergeSuggestions(current: Chip[], suggested: string[]): Chip[] {
  const byLabel = new Map(current.map((chip) => [chip.label.toLowerCase(), chip]));
  const used = new Set<string>();
  const merged: Chip[] = [];

  for (const label of suggested) {
    const key = label.toLowerCase();
    if (used.has(key)) continue;
    merged.push(byLabel.get(key) ?? { label, selected: true });
    used.add(key);
  }

  return merged;
}

/** Drops any chip whose label matches a direct sector — a direct pick never doubles as a suggestion. */
export function withoutDirectDupes(chips: Chip[], directLabels: string[]): Chip[] {
  const direct = new Set(directLabels.map((label) => label.toLowerCase()));
  return chips.filter((chip) => !direct.has(chip.label.toLowerCase()));
}

/**
 * Bounds a suggestion group to `max` chips. Selected chips are always kept (the user opted in); when
 * over the limit, the lowest-ranked deselected chips — those at the tail — are dropped first. If the
 * selected chips alone exceed `max`, they all stay: shrinking a user's own selection is worse than a
 * slightly long list, and the DTO ceiling sits above `max` to absorb it.
 */
export function capChips(chips: Chip[], max: number): Chip[] {
  if (chips.length <= max) return chips;
  let over = chips.length - max;
  const dropped = new Set<number>();
  for (let i = chips.length - 1; i >= 0 && over > 0; i--) {
    if (!chips[i].selected) {
      dropped.add(i);
      over--;
    }
  }
  return chips.filter((_, i) => !dropped.has(i));
}

/** Whether two chip lists are identical in label, selection and order — the effect's no-op guard. */
export function sameChips(a: Chip[], b: Chip[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((chip, i) => chip.label === b[i].label && chip.selected === b[i].selected);
}
