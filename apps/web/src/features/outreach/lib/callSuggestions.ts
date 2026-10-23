import { formatNumber } from "../../../lib/format";
import type {
  CallSuggestion,
  CallSuggestionKind,
  PackageField,
  SuggestedValue,
} from "../api/notetakerApi";

/** What each change is called on its card, and where in the record it lands. */
export const SUGGESTION_FIELD: Record<
  CallSuggestionKind,
  { label: string; lands: string }
> = {
  NOTICE_PERIOD: { label: "Notice period", lands: "Compensation" },
  CURRENT_PACKAGE: { label: "Current package", lands: "Compensation" },
  LOCATION: { label: "Location", lands: "Identity" },
  OPEN_TO_RELOCATE: { label: "Open to relocate", lands: "Tags" },
  MOTIVATION: {
    label: "Motivation",
    lands: "Assessment · added below what's written",
  },
  EXPECTED_PACKAGE: {
    label: "Expected package",
    lands: "Assessment · added below what's written",
  },
  TASK: { label: "Task for you", lands: "Next action" },
};

export const PACKAGE_LINES: { field: PackageField; label: string }[] = [
  { field: "baseSalary", label: "Base" },
  { field: "bonus", label: "Bonus" },
  { field: "allowances", label: "Allowances" },
];

/** One line for a value, or null where nothing is held — the card then says "Not recorded". */
export function valueTextOf(
  kind: CallSuggestionKind,
  value: SuggestedValue | null,
): string | null {
  if (!value) return null;
  switch (kind) {
    case "CURRENT_PACKAGE": {
      const parts = PACKAGE_LINES.flatMap(({ field, label }) => {
        const figure = value[field];
        return figure === null
          ? []
          : [`${label.toLowerCase()} ${formatNumber(figure)}`];
      });
      return parts.length === 0
        ? null
        : `${value.currency ?? ""} ${parts.join(" · ")}`.trim();
    }
    case "LOCATION":
      return [value.city, value.country].filter(Boolean).join(", ") || null;
    default:
      return value.text?.trim() ? value.text : null;
  }
}

/** Where the review should stand: the first change still waiting at or after `fromIndex`, else any still waiting. */
export function nextPendingIndex(
  suggestions: CallSuggestion[],
  fromIndex: number,
): number {
  const after = suggestions.findIndex(
    (suggestion, index) =>
      index >= fromIndex && suggestion.status === "PENDING",
  );
  if (after >= 0) return after;
  return suggestions.findIndex((suggestion) => suggestion.status === "PENDING");
}
