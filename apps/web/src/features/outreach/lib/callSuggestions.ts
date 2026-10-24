import { formatNumber } from "../../../lib/format";
import type {
  AppliedCallSuggestion,
  CallSuggestion,
  CallSuggestionKind,
  PackageField,
  SuggestedValue,
} from "../api/notetakerApi";

/** What each change is called on its row, and where in the record it lands. */
export const SUGGESTION_FIELD: Record<CallSuggestionKind, { label: string; lands: string }> = {
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
  TASK: { label: "Task for you", lands: "Next action · due tomorrow" },
};

export const PACKAGE_LINES: { field: PackageField; label: string }[] = [
  { field: "baseSalary", label: "Base" },
  { field: "bonus", label: "Bonus" },
  { field: "allowances", label: "Allowances" },
];

/** One line for a value, or null where nothing is held — the row then says "Not recorded". */
export function valueTextOf(kind: CallSuggestionKind, value: SuggestedValue | null): string | null {
  if (!value) return null;
  switch (kind) {
    case "CURRENT_PACKAGE": {
      const parts = PACKAGE_LINES.flatMap(({ field, label }) => {
        const figure = value[field];
        return figure === null ? [] : [`${label.toLowerCase()} ${formatNumber(figure)}`];
      });
      return parts.length === 0 ? null : `${value.currency ?? ""} ${parts.join(" · ")}`.trim();
    }
    case "LOCATION":
      return [value.city, value.country].filter(Boolean).join(", ") || null;
    default:
      return value.text?.trim() ? value.text : null;
  }
}

/** What the review holds for one waiting change until it is saved: kept or not, an edit, a package's ticked figures. */
export interface SuggestionChoice {
  kept: boolean;
  /** Null while the change is taken as the call suggested it. */
  edit: SuggestionEdit | null;
  packageFields: PackageField[];
}

export interface SuggestionEdit {
  text: string;
  city: string;
  country: string;
}

/** Kept unless the record no longer lets it in, with every figure a package states ticked. */
export function defaultChoiceOf(suggestion: CallSuggestion): SuggestionChoice {
  return {
    kept: suggestion.available,
    edit: null,
    packageFields: PACKAGE_LINES.filter(({ field }) => suggestion.value[field] !== null).map(({ field }) => field),
  };
}

/** Ticking a package back on whose every figure was unticked takes every figure again. */
export function withKept(suggestion: CallSuggestion, choice: SuggestionChoice, kept: boolean): SuggestionChoice {
  if (!kept) return { ...choice, kept: false };
  const packageFields =
    choice.packageFields.length > 0 ? choice.packageFields : defaultChoiceOf(suggestion).packageFields;
  return { ...choice, kept: true, packageFields };
}

/** A package is chosen figure by figure and a relocation tag is all or nothing; everything else can be reworded. */
export function isEditable(suggestion: CallSuggestion): boolean {
  return suggestion.available && suggestion.kind !== "OPEN_TO_RELOCATE" && suggestion.kind !== "CURRENT_PACKAGE";
}

export function editOf(suggestion: CallSuggestion): SuggestionEdit {
  return {
    text: suggestion.value.text ?? "",
    city: suggestion.value.city ?? "",
    country: suggestion.value.country ?? "",
  };
}

/** The change as the review sends it, or null where it is to be skipped. */
export function appliedOf(suggestion: CallSuggestion, choice: SuggestionChoice): AppliedCallSuggestion | null {
  if (!choice.kept || !suggestion.available) return null;
  if (suggestion.kind === "CURRENT_PACKAGE") {
    return choice.packageFields.length === 0 ? null : { id: suggestion.id, packageFields: choice.packageFields };
  }
  if (!choice.edit) return { id: suggestion.id };
  if (suggestion.kind === "LOCATION") {
    return { id: suggestion.id, value: { city: choice.edit.city.trim() || null, country: choice.edit.country } };
  }
  return { id: suggestion.id, value: { text: choice.edit.text } };
}
