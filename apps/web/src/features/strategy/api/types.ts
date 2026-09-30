import type { CandidateCareerEntry, CandidateEducationEntry } from "../../candidates/api/types";

/** One offerable value: what a filter stores, and what the control reading it says. */
export interface FacetOption {
  /** What a saved filter stores. A slug for bands, the value itself for industries and countries. */
  value: string;
  /** What the chip reads. Presentation only — never stored, so relabelling breaks nothing. */
  label: string;
}

/** A facet value counted over the universe — the count is what makes it worth clicking. */
export interface FacetCount extends FacetOption {
  count: number;
}

/**
 * One sector group with its industries. The universe's `industry` column is 148 flat labels; the
 * grouping is the API's, and clicking a group selects every leaf under it.
 */
export interface SectorGroup {
  name: string;
  industries: FacetCount[];
}

/**
 * Everything the filter sidebar counts, in one read. Counts are over the whole universe rather than
 * the current selection, so this is the same for every mandate and no chip click invalidates it.
 *
 * <p>Location is not here: its vocabulary is served by `/countries`, which also counts the markets
 * the universe actually holds — so the chips never offer a market the pipeline has not loaded, and
 * this read does not grow a GROUP BY over the whole universe to draw them.
 */
export interface Facets {
  sectorGroups: SectorGroup[];
  /**
   * Which industries sit beside which — the panel's suggestion chips. Advice rather than a facet, so
   * no counts: a name the taxonomy no longer holds renders no chip rather than one selecting nothing.
   */
  adjacentIndustries: Record<string, string[]>;
  /** Overlapping by design — a company can be B2B and SaaS at once. */
  marketSegments: FacetCount[];
  employeeBands: FacetCount[];
  revenueBands: FacetCount[];
}

/**
 * A Custom Range on one numeric axis. Either end may be null — "at least 500" is a legal thing to
 * ask for — and a range with neither end set is normalised away server-side.
 */
export interface NumericRange {
  min: number | null;
  max: number | null;
}

/**
 * The whole sidebar selection. Every list holds wire values, never labels, and never group names —
 * a group is expanded to its industries before it is stored.
 *
 * A non-null range *is* Custom Range mode for its axis, and overrides that axis's band list. There
 * is no separate mode flag, so the two can never disagree about which is in force.
 */
export interface StrategyFilter {
  industries: string[];
  keywords: string[];
  marketSegments: string[];
  countries: string[];
  employeeBands: string[];
  revenueBands: string[];
  employeeRange: NumericRange | null;
  revenueRange: NumericRange | null;
}

/** One entry on the off-limits list: its identity plus the snapshot taken when it was barred. */
export interface CompanyRef {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCity: string | null;
  companyCountry: string | null;
  logoUrl: string | null;
}

/** Which of Strategy's two questions a saved search keeps. */
export type SearchKind = "COMPANIES" | "PEOPLE";

/** Who a saved search is for: one person's scratch list, or the mandate's. */
export type SearchVisibility = "PRIVATE" | "SHARED";

/**
 * A named filter a mandate saved. Frozen at save time — editing the sidebar does not follow it, and
 * re-capturing the current filter onto it is an explicit act.
 *
 * A PRIVATE search never reaches anyone but its author, so `createdById` on a row in this list is
 * either the viewer or someone who chose to share.
 */
export interface SavedSearch {
  id: string;
  name: string;
  kind: SearchKind;
  filter: StrategyFilter;
  /** Set on a PEOPLE search only; its company `filter` is then empty. */
  peopleFilter: PeopleFilter | null;
  visibility: SearchVisibility;
  createdById: string;
  createdByName: string | null;
  createdAt: string;
  /** Moves when the search is renamed or re-captured; this is the date the row shows. */
  updatedAt: string;
}

/** Everything the screen needs before it draws. */
export interface Strategy {
  filter: StrategyFilter;
  peopleFilter: PeopleFilter;
  offLimits: CompanyRef[];
  searches: SavedSearch[];
}

/**
 * One row of the results table. `annualRevenue` is null on roughly nine rows in ten — that is the
 * data, and the cell says unknown rather than showing a zero. The funding fields are sparser still.
 *
 * Every field arrives whether or not the user has that column switched on: the visible set is a
 * local preference, and making the response depend on it would put UI state in the query key.
 */
export interface CompanyResult {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCountry: string | null;
  companyCity: string | null;
  numEmployees: number | null;
  annualRevenue: number | null;
  website: string | null;
  logoUrl: string | null;
  shortDescription: string | null;
  foundedYear: number | null;
  companyLinkedinUrl: string | null;
  facebookUrl: string | null;
  twitterUrl: string | null;
  companyPhone: string | null;
  companyState: string | null;
  companyAddress: string | null;
  parentCompany: string | null;
  totalFunding: number | null;
  latestFunding: string | null;
  latestFundingAmount: number | null;
  /** `YYYY-MM-DD`, not a timestamp. */
  lastRaisedAt: string | null;
  numberOfRetailLocations: number | null;
  keywords: string[];
  technologies: string[];
  sicCodes: string[];
  naicsCodes: string[];
}

export interface CompanyPage {
  companies: CompanyResult[];
  /** Over the whole filter, not the page — what the pagination bar states. */
  totalCount: number;
  page: number;
  size: number;
}

/** A company offered by a picker's typeahead. */
export interface CompanySuggestion {
  apolloAccountId: string;
  companyName: string;
  industry: string | null;
  companyCity: string | null;
  companyCountry: string | null;
  website: string | null;
  logoUrl: string | null;
  numEmployees: number | null;
}

/** The columns the results table can sort by. These tokens are the backend's allowlist. */
export type CompanySortField =
  | "name"
  | "sector"
  | "country"
  | "location"
  | "employees"
  | "revenue"
  | "founded";

export type SortDirection = "asc" | "desc";

export interface CompanySort {
  field: CompanySortField;
  direction: SortDirection;
}


/** Which roles the job titles are matched against: now, before, or either. */
export type TitleMatch = "current" | "past" | "both";

/** Which employers the companies are matched against. */
export type CompanyMatch = "current" | "both" | "past_only";

export type ContactDataType = "personal_email" | "work_email" | "phone";

export interface PeopleLanguage {
  language: string;
  proficiencies: string[];
}

/**
 * The People sidebar's whole selection — ContactOut's People Search filter, stored per mandate. The
 * vocabulary lists hold ContactOut's accepted values verbatim; everything else is free text. A null
 * match mode is the vendor's default of current roles only.
 */
export interface PeopleFilter {
  name: string | null;
  jobTitles: string[];
  titleMatch: TitleMatch | null;
  includeRelatedTitles: boolean;
  recentlyChangedJobs: boolean;
  excludedJobTitles: string[];
  seniorities: string[];
  jobFunctions: string[];
  skills: string[];
  yearsInCurrentRole: string[];
  yearsOfExperience: string[];
  locations: string[];
  /** Miles around the one city in `locations`; ignored otherwise. */
  locationRadius: number | null;
  companies: string[];
  domains: string[];
  companyMatch: CompanyMatch | null;
  excludedCompanies: string[];
  companySizes: string[];
  industries: string[];
  excludedIndustries: string[];
  languages: PeopleLanguage[];
  education: string[];
  keyword: string | null;
  contactTypes: ContactDataType[];
}

/** ContactOut's closed vocabularies for the People sidebar. */
export interface PeopleFacets {
  seniorities: FacetOption[];
  jobFunctions: FacetOption[];
  companySizes: FacetOption[];
  yearsOfExperience: FacetOption[];
  yearsInCurrentRole: FacetOption[];
  languageProficiencies: FacetOption[];
  industries: string[];
}

/** The stored people filter counted, free. The estimates are ContactOut's own, never a promise. */
export interface PeopleCount {
  offered: boolean;
  total: number;
  estimatedPersonalEmails: number;
  estimatedWorkEmails: number;
  estimatedPhones: number;
}

/**
 * One person on a page of results, read the way a filed candidate's profile is — the preview is what
 * Add to universe would file. `held`: this mandate already maps them.
 */
export interface PersonResult {
  linkedinSlug: string;
  fullName: string | null;
  title: string | null;
  companyName: string | null;
  companyLinkedinUrl: string | null;
  companyLogoUrl: string | null;
  location: string | null;
  countryCode: string | null;
  photoUrl: string | null;
  profileUrl: string | null;
  about: string | null;
  career: CandidateCareerEntry[];
  education: CandidateEducationEntry[];
  skills: string[];
  languages: string[];
  /** What ContactOut said beyond the profile; null for a record kept before it was stored whole. */
  details: PersonDetails | null;
  /** The executive this mandate filed them as, when it holds them. */
  candidateId: string | null;
  held: boolean;
}

export interface PersonLink {
  label: string;
  url: string;
}

/** A certification, publication, project or volunteering role, in whichever terms ContactOut gave it. */
export interface PersonProfileItem {
  title: string;
  subtitle: string | null;
  period: string | null;
  url: string | null;
  description: string | null;
}

export interface PersonDetails {
  headline: string | null;
  industry: string | null;
  jobFunction: string | null;
  seniority: string | null;
  workStatus: string | null;
  followers: number | null;
  updatedAt: string | null;
  links: PersonLink[];
  certifications: PersonProfileItem[];
  publications: PersonProfileItem[];
  projects: PersonProfileItem[];
  volunteering: PersonProfileItem[];
  /** Whether ContactOut holds each kind of contact — flags only, free; finding one is a paid lookup. */
  contactAvailability: { personalEmail: boolean; workEmail: boolean; phone: boolean } | null;
  company: {
    website: string | null;
    domain: string | null;
    industry: string | null;
    size: string | null;
    country: string | null;
    headquarter: string | null;
    foundedYear: number | null;
    revenue: string | null;
    overview: string | null;
    specialties: string[];
  } | null;
}

/** One page of the search; `billed` is the credits it spent, zero when it came from the cache. */
export interface PeopleSearchPage {
  people: PersonResult[];
  page: number;
  pageSize: number;
  total: number;
  billed: number;
  cached: number;
}

export interface PeopleSearchResults {
  pages: PeopleSearchPage[];
}

export interface AddPeopleResult {
  added: number;
  skipped: number;
  unavailable: number;
  /** Of the added, how many joined an employer the mandate already holds at another stage. */
  elsewhere: number;
  filed: { linkedinSlug: string; candidateId: string }[];
}

export type PlaceKind = "COUNTRY" | "AREA" | "CITY";

/** A place the Location box offers: `label` is shown, `value` is what the search sends. */
export interface PlaceSuggestion {
  label: string;
  value: string;
  kind: PlaceKind;
}
