/**
 * The company-size band catalog — a client-side mirror of the backend EmployeeBand / RevenueBand enums.
 * Kept here so the Company Size pills paint instantly on mount rather than waiting for the strategy GET,
 * which carries only the *selected* values. `value` must match the backend enum's value() verbatim (it is
 * what a PUT sends and what the response echoes); `label` is display-only. companySizeBands.test.ts guards
 * the two catalogs against drift.
 */

export interface BandDef {
  value: string;
  label: string;
}

export const EMPLOYEE_BANDS: BandDef[] = [
  { value: "1-10", label: "1–10" },
  { value: "11-50", label: "11–50" },
  { value: "51-200", label: "51–200" },
  { value: "201-500", label: "201–500" },
  { value: "501-1000", label: "501–1,000" },
  { value: "1001-5000", label: "1,001–5,000" },
  { value: "5001-10000", label: "5,001–10,000" },
  { value: "10000+", label: "10,000+" },
];

export const REVENUE_BANDS: BandDef[] = [
  { value: "<5M", label: "<$5M" },
  { value: "5M-25M", label: "$5M–25M" },
  { value: "25M-100M", label: "$25M–100M" },
  { value: "100M-500M", label: "$100M–500M" },
  { value: "500M-1B", label: "$500M–1B" },
  { value: "1B-5B", label: "$1B–5B" },
  { value: "5B+", label: "$5B+" },
];
