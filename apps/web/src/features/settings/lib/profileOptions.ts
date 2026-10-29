import { computerTimeZone, isOfferedTimeZone } from "../../../lib/datetime";

/** What the Profile pickers offer. Data, not markup, so the form stays a form. */

export interface SelectOption {
  value: string;
  label: string;
}

/** The zone this computer's clock is set to, where it is one the list offers. */
export function browserTimeZone(): string | null {
  const zone = computerTimeZone();
  return isOfferedTimeZone(zone) ? zone : null;
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
