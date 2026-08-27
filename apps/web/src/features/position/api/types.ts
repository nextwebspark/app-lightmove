/** The position-brief API contract, hand-mirrored from the records in the position dto package. */

export type MandateReason =
  | "NEW_ROLE"
  | "BACKFILL"
  | "SUCCESSION"
  | "RESTRUCTURING"
  | "GROWTH_EXPANSION";

export type CriterionMode = "REQUIRED" | "PREFERRED";

export type EmploymentType =
  | "FULL_TIME_PERMANENT"
  | "FIXED_TERM_CONTRACT"
  | "PART_TIME"
  | "INTERIM"
  | "RETAINED_ADVISORY";

export type PositionSeniority = "C_SUITE" | "N_MINUS_1" | "N_MINUS_2" | "N_MINUS_3";

export type HiringUrgency = "STANDARD" | "PRIORITY" | "URGENT";

export type StrategicPriority =
  | "CAPITAL_DISCIPLINE"
  | "PORTFOLIO_GROWTH"
  | "OPERATIONAL_EXCELLENCE"
  | "GOVERNANCE_AND_CONTROLS"
  | "TALENT_DEVELOPMENT";

export type NoticeUnit = "DAYS" | "WEEKS" | "MONTHS";

export type BaseSalaryMode = "ANNUAL" | "MONTHLY";

export type BonusBasis = "PERCENT_OF_BASE" | "PERCENT_OF_TOTAL_FIXED" | "MONTHS_OF_BASE";

export type IncentiveType = "LTIP_CASH" | "RSU" | "OPTIONS" | "PHANTOM_EQUITY";

export type BenefitFrequency = "MONTHLY" | "YEARLY";

export interface DirectReport {
  title: string | null;
  name: string | null;
}

export interface Benefit {
  name: string;
  /** Absent when the package names the allowance without quantifying it, which is common. */
  amount: number | null;
  frequency: BenefitFrequency;
}

export interface Criterion {
  text: string;
  mode: CriterionMode;
  /** Seeded from the brief (the template library today, an AI drafter later). */
  fromBrief: boolean;
}

export interface Competency {
  name: string;
  description: string | null;
  weight: number;
}

/** Step 1. `roleTitle` is the mandate's own title, edited here and stored on the project. */
export interface PositionDetails {
  roleTitle: string;
  department: string | null;
  location: string | null;
  employmentType: EmploymentType | null;
  seniority: PositionSeniority | null;
  responsibilities: string[];
  narrative: string | null;
}

/** Step 2. */
export interface MandateContext {
  mandateReason: MandateReason;
  businessDriver: string | null;
  strategicPriorities: StrategicPriority[];
  hiringUrgency: HiringUrgency;
  confidential: boolean;
  internalContext: string | null;
}

/** Step 3. `targetStart` is the mandate's single target date, sourced from the project. */
export interface ReportingStructure {
  reportsToName: string | null;
  reportsTo: string | null;
  directReports: DirectReport[];
  teamSize: string | null;
  targetStart: string | null;
  noticeValue: number | null;
  noticeUnit: NoticeUnit | null;
}

/** Step 4. Every figure travels with the unit it is quoted in. */
export interface Compensation {
  currency: string;
  salaryMin: number | null;
  salaryMax: number | null;
  baseSalaryMode: BaseSalaryMode;
  bonusValue: number | null;
  bonusBasis: BonusBasis | null;
  incentiveType: IncentiveType | null;
  incentiveAmount: number | null;
  incentiveVesting: string | null;
  benefits: Benefit[];
}

/** Step 5. Stored as one ordered list; the API splits the panels because the screen draws two. */
export interface Assessment {
  criteria: Criterion[];
  technical: Competency[];
  behavioural: Competency[];
}

/** Step 6. Publishing is a stamp, not a lock — a published brief stays editable. */
export interface Publication {
  publishedAt: string | null;
  publishedBy: string | null;
}

export interface PositionDocument {
  fileName: string;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
}

export interface Position {
  details: PositionDetails;
  context: MandateContext;
  reporting: ReportingStructure;
  compensation: Compensation;
  assessment: Assessment;
  publication: Publication;
  document: PositionDocument | null;
}
