import type { PositionTemplate } from "../api/types";

export const MAX_SUGGESTIONS = 8;

/**
 * What a role-title box offers: everything when it is empty — seventeen titles is a menu, not a
 * search — and the substring matches once somebody types, titles first so "chief" does not lead with
 * a summary that happens to mention it. Shared by the brief's own field and the New-project modal.
 */
export function suggestionsFor(templates: PositionTemplate[], typed: string): PositionTemplate[] {
  const needle = typed.trim().toLowerCase();
  if (!needle) return templates.slice(0, MAX_SUGGESTIONS);

  const byTitle = templates.filter((template) => template.title.toLowerCase().includes(needle));
  const bySummary = templates.filter(
    (template) =>
      !byTitle.includes(template) && (template.summary ?? "").toLowerCase().includes(needle),
  );
  return [...byTitle, ...bySummary].slice(0, MAX_SUGGESTIONS);
}
