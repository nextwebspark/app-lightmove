import type { NoticeUnit } from "../../../lib/noticePeriod";
import type { SeniorityTier } from "../../../lib/seniority";

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
  | "TEMPORARY"
  | "INTERIM"
  | "RETAINED_ADVISORY";

/** The shared ladder — see lib/seniority.ts. Aliased so this feature's payloads read in one place. */
export type PositionSeniority = SeniorityTier;

/** Both halves of a mandate plan in one vocabulary — see lib/noticePeriod.ts. */
export type { NoticeUnit };

export type BaseSalaryMode = "ANNUAL" | "MONTHLY";

/** `FIXED_AMOUNT` is the one basis where the figure is money in the package's currency. */
export type BonusBasis = "PERCENT_OF_BASE" | "PERCENT_OF_TOTAL_FIXED" | "MONTHS_OF_BASE" | "FIXED_AMOUNT";

export type IncentiveType = "LTIP_CASH" | "RSU" | "OPTIONS" | "PHANTOM_EQUITY";

export type BenefitFrequency = "MONTHLY" | "YEARLY";

/**
 * One seat in the org chart. Exactly one node carries `mandateSeat` — the role being searched for —
 * and everything else reads off it: the manager is that seat's parent, the direct reports are its
 * children. `canvasX`/`canvasY` are where the box was dragged, absent until it has been.
 */
export interface OrgNode {
  nodeId: string;
  parentNodeId: string | null;
  title: string | null;
  name: string | null;
  mandateSeat: boolean;
  canvasX: number | null;
  canvasY: number | null;
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

/**
 * Step 1. `roleTitle` is the mandate's own title, edited here and stored on the project. The location
 * is two halves the server settles on their own — the country to the catalog's spelling, the city to
 * its casing — so a brief and the mandate's companies spell one place the same way.
 */
export interface PositionDetails {
  roleTitle: string;
  department: string | null;
  locationCity: string | null;
  locationCountry: string | null;
  employmentType: EmploymentType | null;
  seniority: PositionSeniority | null;
  responsibilities: string[];
  narrative: string | null;
}

export type PositionDiscipline =
  | "EXECUTIVE"
  | "FINANCE"
  | "OPERATIONS"
  | "TECHNOLOGY"
  | "PEOPLE"
  | "COMMERCIAL"
  | "GOVERNANCE"
  | "INVESTMENT";

/**
 * One option in the role-title type-ahead: a template the brief can be drafted from. `shared` is the
 * LightMove library; the rest are the workspace's own and lead the list.
 */
export interface PositionTemplate {
  id: string;
  code: string;
  title: string;
  discipline: PositionDiscipline;
  seniority: PositionSeniority;
  summary: string | null;
  shared: boolean;
}

/**
 * One strategic priority chip: a name somebody wrote, and whether the mandate is aligned to it. An
 * unselected chip is part of the palette rather than a choice — kept until somebody deletes it.
 */
export interface StrategicPriority {
  name: string;
  selected: boolean;
}

/** Step 2. The priorities are the mandate's own, in the order the brief lists them. */
export interface MandateContext {
  mandateReason: MandateReason;
  businessDriver: string | null;
  strategicPriorities: StrategicPriority[];
  confidential: boolean;
  internalContext: string | null;
}

/**
 * Step 3. `targetStart` is the mandate's single target date, sourced from the project.
 *
 * Who the role reports to and how many seats it leads are not fields: they are the parent and the
 * children of the chart's mandate seat, derived by `lib/orgChart.ts` rather than sent twice.
 */
export interface ReportingStructure {
  orgChart: OrgNode[];
  teamSize: string | null;
  /** Read-only here: the mandate owns its target date, and the project screen is where it is set. */
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

/**
 * Step 5. Stored as one ordered list; the API splits the panels because the screen draws two.
 * `technicalShare` is how much of the assessment the technical panel carries, 0–100; the behavioural
 * panel carries the rest.
 */
export interface Assessment {
  criteria: Criterion[];
  technical: Competency[];
  behavioural: Competency[];
  technicalShare: number;
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
