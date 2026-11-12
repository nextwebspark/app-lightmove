/** What the Profile pickers offer. Data, not markup, so the form stays a form. */

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * Stored, not yet applied — nothing in the product is translated. Kept because the choice is the
 * user's to record, and the server refuses any tag outside this set.
 */
export const LANGUAGE_OPTIONS: SelectOption[] = [
  { value: "en", label: "English" },
  { value: "ar", label: "العربية" },
  { value: "fr", label: "Français" },
];
