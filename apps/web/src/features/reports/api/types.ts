import type { SeniorityToken } from "../../../lib/seniority";
import type { CandidateStatus } from "../../candidates/api/types";

/**
 * The talent mapping report as `GET /projects/{id}/report` answers it: a head and four chapters,
 * every figure aggregated live from the mandate's own rows. The screen derives its findings from
 * this in `lib/` and never invents a number the server did not state.
 */

export type SeniorityLevel = SeniorityToken;

export interface ReportHead {
  universeCount: number;
  executivesMapped: number;
  /** A row cap was hit: the chapters describe a sample, the totals above the whole. */
  truncated: boolean;
  generatedAt: string;
}

export interface WeeklyCount {
  /** ISO date the week ended on. */
  weekEnding: string;
  identified: number;
}

export interface ReportProgress {
  kickoff: string;
  targetDate: string | null;
  asOf: string;
  targetCompanies: number;
  /** Companies with at least one executive, cumulative, one entry per week from the kickoff week (index 0). */
  companiesCumulative: number[];
  weekly: WeeklyCount[];
  /** Executives identified per day from the kickoff day (index 0) to `asOf`. */
  daily: number[];
  daysSinceLastCompany: number | null;
}

export interface MarketCell {
  sector: string;
  level: SeniorityLevel;
  count: number;
}

export interface SliceExecutive {
  id: string;
  fullName: string;
  company: string | null;
  status: CandidateStatus;
}

export interface MarketSlice {
  sector: string;
  level: SeniorityLevel;
  companies: string[];
  executives: SliceExecutive[];
}

export interface LevelCount {
  level: SeniorityLevel;
  count: number;
}

/** Where a hub sits — always a country centroid, marking the market rather than an address. */
export interface MapPoint {
  latitude: number;
  longitude: number;
}

/** One country the mapped talent sits in. Not a city: most rows carry a country and nothing finer. */
export interface TalentHub {
  country: string;
  count: number;
  depth: LevelCount[];
  employers: string[];
  interested: number;
  /** Counts, not shares, and each against its own denominator — divide by what the server states. */
  gccNationals: number;
  female: number;
  /** The denominator for `female`: how many of this hub's executives have a gender on file at all. */
  recordedGender: number;
  /** The middle disclosed package here, in the report's currency, or null where nobody disclosed one. */
  medianPackage: number | null;
  /** Null until the geocoder has placed the country. */
  point: MapPoint | null;
}

export interface Breakdown {
  label: string;
  count: number;
}

export interface ReportMarket {
  sectors: string[];
  levels: SeniorityLevel[];
  cells: MarketCell[];
  withoutSector: number;
  withoutSeniority: number;
  slices: MarketSlice[];
  hubs: TalentHub[];
  elsewhere: number;
  unlocated: number;
  companiesBySector: Breakdown[];
}

export interface CompensationBand {
  low: number;
  high: number;
}

export interface Disclosure {
  id: string;
  fullName: string;
  company: string | null;
  title: string | null;
  country: string | null;
  nationality: string | null;
  status: CandidateStatus;
  /** Annual, whole units of the report's currency: base plus allowances. */
  fixed: number;
  /** Fixed plus bonus and long-term incentive. */
  totalPackage: number;
  note: string | null;
}

export interface ReportRemuneration {
  currency: string;
  fixedBand: CompensationBand | null;
  packageBand: CompensationBand | null;
  disclosures: Disclosure[];
  /** Packages on file in another currency — counted, never converted. */
  otherCurrency: number;
}

export interface NationalityRow {
  nationality: string;
  gcc: boolean;
  byLevel: LevelCount[];
  unclassified: number;
  total: number;
}

/**
 * One level's gender split, counted only from rows carrying one. The three add to the level's
 * *recorded* population, never to the level itself — everyone else is in `genderUnrecorded`.
 */
export interface GenderLevelRow {
  level: SeniorityLevel;
  female: number;
  male: number;
  other: number;
}

/** A gender split counted only from rows carrying one. */
export interface GenderSplit {
  female: number;
  male: number;
  other: number;
}

export interface ReportDiversity {
  levels: SeniorityLevel[];
  nationalities: NationalityRow[];
  unknownNationality: number;
  gccNationals: number;
  genderByLevel: GenderLevelRow[];
  /** Recorded genders of executives with no seniority on file, which no level's split can hold. */
  genderWithoutLevel: GenderSplit;
  /** Executives with no gender on file. Never folded into `other`, which somebody did record. */
  genderUnrecorded: number;
}

export interface Report {
  head: ReportHead;
  progress: ReportProgress;
  market: ReportMarket;
  remuneration: ReportRemuneration;
  diversity: ReportDiversity;
}
