/**
 * The talent mapping report, as the screen reads it. One document per mandate: a head, then the four
 * chapters. Every figure the page shows is either carried here or derived from it in `lib/` — the
 * page never invents a number.
 */

export type SeniorityLevel = "Board" | "C-Suite" | "N-1" | "N-2";

export const SENIORITY_LEVELS: readonly SeniorityLevel[] = ["Board", "C-Suite", "N-1", "N-2"];

export interface Breakdown {
  label: string;
  count: number;
}

export interface ReportHead {
  region: string;
  universeCount: number;
  executivesMapped: number;
  syncedAt: string;
}

export interface WeekPoint {
  /** ISO date the week ended on. */
  weekEnding: string;
  identified: number;
}

export interface ReportProgress {
  kickoff: string;
  targetDate: string;
  asOf: string;
  targetCompanies: number;
  /** Companies with at least one executive, cumulative, one entry per week from kickoff (index 0). */
  companiesCumulative: number[];
  weekly: WeekPoint[];
  /** Executives identified per day from the day after kickoff; seven entries per week. */
  dailyIdentified: number[];
  daysSinceLastCompany: number;
}

export interface MarketCell {
  sector: string;
  level: SeniorityLevel;
  count: number;
}

export type SliceExecutiveStatus = "interested" | "passive" | "verified" | "offlimits";

export interface SliceExecutive {
  name: string;
  company: string;
  status: SliceExecutiveStatus;
}

export interface SliceInsight {
  /** How many executives the pocket is thought to hold in total, mapped or not. */
  estimatedMarket: number;
  compFitPct: number;
  femalePct: number;
  gccNationalsPct: number;
}

export interface MarketSlice {
  sector: string;
  level: SeniorityLevel;
  companies: string[];
  executives: SliceExecutive[];
  /** Absent until research has sized the pocket; the drawer then shows the count alone. */
  insight?: SliceInsight;
}

export interface Hub {
  city: string;
  country: string;
  count: number;
  femalePct: number;
  openToMovePct: number;
  compensationLabel: string;
  compensationPct: number;
  nationalsPct: number;
  depth: { level: SeniorityLevel; count: number }[];
  employers: string[];
  note: string;
}

export interface ReportMarket {
  sectors: string[];
  cells: MarketCell[];
  slices: MarketSlice[];
  hubs: Hub[];
  companiesBySector: Breakdown[];
  relevance: Breakdown[];
}

export type DisclosureOutcome = "accepted" | "process" | "declined" | "withdrawn";

export interface Disclosure {
  id: string;
  name: string;
  company: string;
  title: string;
  country: string;
  nationality: string;
  /** USD thousands per year. */
  packageK: number;
  fixedK: number;
  outcome: DisclosureOutcome;
  note: string;
}

export interface CompensationBand {
  lowK: number;
  highK: number;
}

export interface ReportRemuneration {
  packageBand: CompensationBand;
  fixedBand: CompensationBand;
  disclosures: Disclosure[];
}

export interface NationalityRow {
  nationality: string;
  counts: Record<SeniorityLevel, number>;
}

export interface ReportDiversity {
  /** Female headcount per level — aggregate only, never a person. */
  femaleByLevel: Record<SeniorityLevel, number>;
  nationalities: NationalityRow[];
  gccNationalities: string[];
}

export interface Report {
  head: ReportHead;
  progress: ReportProgress;
  market: ReportMarket;
  remuneration: ReportRemuneration;
  diversity: ReportDiversity;
}
