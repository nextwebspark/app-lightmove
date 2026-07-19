/** The position-brief API contract, hand-mirrored from PositionDtos. */

export type MandateReason = "NEW_ROLE" | "BACKFILL" | "SUCCESSION" | "RESTRUCTURING";

export type CriterionMode = "REQUIRED" | "PREFERRED";

export type EmploymentType =
  | "FULL_TIME_PERMANENT"
  | "FIXED_TERM_CONTRACT"
  | "PART_TIME"
  | "INTERIM"
  | "RETAINED_ADVISORY";

export type NoticeUnit = "WEEKS" | "MONTHS";

export interface Criterion {
  text: string;
  mode: CriterionMode;
  /** Seeded from the brief (the template library today, an AI drafter later). */
  fromBrief: boolean;
}

export interface Competency {
  name: string;
  weight: number;
}

export interface Position {
  mandateReason: MandateReason;
  internalContext: string | null;
  narrative: string | null;
  reportsTo: string | null;
  directReports: number | null;
  teamSize: number | null;
  location: string | null;
  employmentType: EmploymentType | null;
  /** The mandate's single target date — sourced from the project, editable here or from the list. */
  startTarget: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string;
  noticeValue: number | null;
  noticeUnit: NoticeUnit | null;
  /** Target bonus as a percentage of base (0–100). */
  bonusTargetPct: number | null;
  ltip: string | null;
  benefits: string[];
  confidential: boolean;
  criteria: Criterion[];
  technical: Competency[];
  behavioural: Competency[];
  locked: boolean;
  lockedAt: string | null;
}

/** The scalar snapshot the autosave PUTs — Position minus the two lists and the lock state. */
export type PositionDetails = Omit<Position, "criteria" | "technical" | "behavioural" | "locked" | "lockedAt">;
