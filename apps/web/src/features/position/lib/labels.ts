import type { EmploymentType } from "../api/types";

/** Display labels for the employment-type enum — one place so the card and hero read the same. */
export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME_PERMANENT: "Full-time, permanent",
  FIXED_TERM_CONTRACT: "Fixed-term contract",
  PART_TIME: "Part-time",
  INTERIM: "Interim / day-rate",
  RETAINED_ADVISORY: "Retained advisory",
};

export function employmentTypeLabel(value: EmploymentType | null): string | null {
  return value ? EMPLOYMENT_TYPE_LABELS[value] : null;
}
