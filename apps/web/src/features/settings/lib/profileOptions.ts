/**
 * What the Profile pickers offer, as the mockup lists them.
 *
 * Data, not markup, so the form stays a form. The timezone values are IANA zone ids because that is
 * what the server stores and what a date is later formatted in — the label is the mockup's wording,
 * and the two are deliberately not the same string.
 */

export interface SelectOption {
  value: string;
  label: string;
}

export const TIMEZONE_OPTIONS: SelectOption[] = [
  { value: "Asia/Dubai", label: "Gulf Standard Time (GMT+4)" },
  { value: "Asia/Riyadh", label: "Arabia Standard Time (GMT+3)" },
  { value: "Etc/GMT", label: "GMT" },
  { value: "Europe/Paris", label: "Central European Time" },
];

/**
 * Stored, not yet applied — nothing in the product is translated. Kept because the choice is the
 * user's to record, and the server refuses any tag outside this set.
 */
export const LANGUAGE_OPTIONS: SelectOption[] = [
  { value: "en", label: "English" },
  { value: "ar", label: "العربية" },
  { value: "fr", label: "Français" },
];
