import type { CustomFieldValues } from "../../customcolumns/api/types";

import type { SeniorityToken } from "../../../lib/seniority";
/**
 * Where a mandate's research on an executive has got to. Not a shortlist flag: talent mapping records
 * the market as it is, so someone ruled out is kept with the reason rather than deleted — the map is
 * worth less if it only shows the people still in play.
 */
export type CandidateStatus =
  | "identified"
  | "contacted"
  | "engaged"
  | "interested"
  | "notInterested"
  | "offLimits"
  | "outOfScope";

/**
 * Distance from the chief executive, the axis a search brief is written in. The same job title means
 * different things in a family holding and a listed multinational; this does not. The two tiers above
 * the executive line are named rather than numbered, because a board seat is a different kind of role
 * and not a distance from the CEO.
 */
/** The shared ladder's wire token — see lib/seniority.ts. This contract speaks the label. */
export type CandidateSeniority = SeniorityToken;

/**
 * Gender, for the report's diversity chapter. A researcher's own entry, or a captured profile's
 * AI-suggested value — see `aiInferredFields` — flagged until reviewed either way. `null` — nobody
 * recorded or confirmed one — is a different fact from `other`, which somebody did.
 */
export type CandidateGender = "female" | "male" | "other";

/** The four fields `aiInferredFields` can flag as an unreviewed AI suggestion. */
export type CandidateBackgroundField = "nationality" | "gender" | "yearsExperience" | "seniority";

/** Which door a profile came through. Only `manual` is reachable today. */
export type CandidateSource = "manual" | "csv" | "extension" | "ai_sourced" | "people_search";

/** One post in a career history. Free-text period, because that is the precision sources publish. */
export interface CandidateCareerEntry {
  company: string | null;
  title: string | null;
  period: string | null;
  /** Where the post was held, as research found it; no screen edits it, the drawer carries it back. */
  location?: string | null;
}

/** One school, shaped like a career post and written only by enrichment — no screen edits it yet. */
export interface CandidateEducationEntry {
  school: string | null;
  degree: string | null;
  period: string | null;
}

/**
 * A package as it was quoted, in the currency it was quoted in. Nothing converts it — a rate applied
 * at write time is wrong by the time anyone reads the row.
 */
export interface CandidateCompensation {
  currency: string | null;
  baseSalary: number | null;
  bonus: number | null;
  allowances: number | null;
  longTermIncentive: number | null;
  noticePeriod: string | null;
  /** The allowances itemised. When present they sum to `allowances`, which the server keeps agreeing. */
  allowanceLines: AllowanceLine[];
  /** What the long-term incentive is paid in; `none` is a recorded "no LTIP" and only ever alone. */
  longTermIncentiveTypes: LongTermIncentiveType[];
}

export interface AllowanceLine {
  label: string | null;
  amount: number | null;
}

export type LongTermIncentiveType = "options" | "rsus" | "cash" | "none";

/**
 * One executive mapped for a mandate.
 *
 * <p>`triageCompanyId` is the mandate's own company row, and it is null for someone whose employer is
 * not in the universe. `companyName` is carried either way: it is the employer snapshotted when the
 * row was written, so it renders identically after that company has been removed from the mandate.
 */
export interface Candidate {
  id: string;
  triageCompanyId: string | null;
  companyName: string | null;
  fullName: string;
  title: string | null;
  seniority: CandidateSeniority | null;
  status: CandidateStatus;
  linkedinUrl: string | null;
  locationCountry: string | null;
  locationCity: string | null;
  nationality: string | null;
  gender: CandidateGender | null;
  yearsExperience: number | null;
  /** Which of nationality/gender/yearsExperience hold a value AI proposed, not yet reviewed. */
  aiInferredFields: CandidateBackgroundField[];
  summary: string | null;
  compensation: CandidateCompensation;
  career: CandidateCareerEntry[];
  languages: string[];
  /** Enrichment's, not the drawer's: empty until research has run, and never part of a save. */
  education: CandidateEducationEntry[];
  skills: string[];
  source: CandidateSource;
  sourceUrl: string | null;
  /** This mandate's own extra columns for this person, keyed by each column's `fieldKey`. */
  customFields: CustomFieldValues;
  addedAt: string;
  /** When enrichment last filled this profile in; null while research is pending or off. */
  enrichedAt: string | null;
  contacts: CandidateContacts;
  /** The workspace's person this row maps — one id on every mandate that holds them. */
  personId: string;
  /** The plugin read this person off that page, so the server refuses a retyped URL. */
  linkedinUrlLocked: boolean;
}

/** Which door one contact value came through — the row's three doors plus the lookup provider. */
export type ContactSource = "manual" | "csv" | "extension" | "contactout";

/**
 * One address the mandate knows. `kind` and `verified` are only ever what the provider said — null
 * and false for anything a person typed, and for an address the provider listed without saying.
 */
export interface CandidateEmail {
  address: string;
  kind: "work" | "personal" | null;
  verified: boolean;
  status: string | null;
  source: ContactSource;
  foundAt: string;
}

/** One number the mandate knows, spelled as it arrived. `kind` only where a person tagged it. */
export interface CandidatePhone {
  number: string;
  kind: "work" | "personal" | null;
  verified: boolean;
  status: string | null;
  source: ContactSource;
  foundAt: string;
}

/** One email or phone as the Contact section or the Add form states it. */
export interface ContactEntryInput {
  value: string;
  kind: "work" | "personal" | null;
  verified: boolean;
}

/** The Contact section's save: both channels, replaced wholesale by what is listed. */
export interface SaveContactsPayload {
  emails: ContactEntryInput[];
  phones: ContactEntryInput[];
}

/**
 * Every email and phone known for the person, in the order the drawer lists them, and when each
 * channel was last looked up.
 *
 * <p>The timestamps decide what the Contact section offers, not the lists: null means the button is
 * still worth pressing, and a timestamp with no row from `source` means the provider had nothing and
 * asking again would only buy the same answer.
 */
export interface CandidateContacts {
  emails: CandidateEmail[];
  phones: CandidatePhone[];
  emailsLookedUpAt: string | null;
  phonesLookedUpAt: string | null;
  /** The provider that answered the lookups, for the "Found via" line. */
  source: string | null;
}

export interface CandidatesPage {
  candidates: Candidate[];
  totalCount: number;
  page: number;
  size: number;
}

/**
 * What the drawer submits, for both an add and an edit. The server replaces the whole profile with it,
 * so an omitted field is a cleared field — which is what the drawer means, because it holds every one.
 */
export interface SaveCandidatePayload {
  triageCompanyId?: string | null;
  fullName: string;
  title?: string;
  seniority?: CandidateSeniority;
  status?: CandidateStatus;
  /** Ignored by the server when `triageCompanyId` names one of the mandate's companies. */
  employerName?: string;
  /** The Add form's contacts. A profile edit never sends them: the Contact section has its own write. */
  emails?: ContactEntryInput[];
  phones?: ContactEntryInput[];
  linkedinUrl?: string;
  locationCountry?: string;
  locationCity?: string;
  nationality?: string;
  gender?: CandidateGender;
  yearsExperience?: number;
  summary?: string;
  /** Filed as a general note on the person about this position; never read back on the row. */
  note?: string;
  compensation?: Partial<CandidateCompensation>;
  career?: CandidateCareerEntry[];
  languages?: string[];
  /** Omitted leaves every custom column alone; a blank value clears that one column. */
  customFields?: CustomFieldValues;
  /** Sent only by the Background section's save: its AI-proposed values are now reviewed. */
  confirmBackground?: boolean;
}

/** One competency panel's AI reading: a 1–10 score (null when the model could not judge) and why. */
export interface CompetencyPanelAssessment {
  score: number | null;
  positives: string[];
  negatives: string[];
}

/** How strongly the classifier's evidence supports its category; only `high` fills the field. */
export type NationalityConfidence = "high" | "medium" | "low";

/**
 * The nationality classifier's last reading. `category` is one of the groups, or "Unknown" when the
 * evidence did not decide it. Staff-only: its evidence reasons about a person's origin.
 */
export interface NationalityReading {
  category: string;
  confidence: NationalityConfidence;
  evidenceFor: string[];
  evidenceAgainst: string[];
  rule: string;
  readAt: string;
}

/**
 * A candidate's last AI assessment, nationality reading and last failed run — staff-only, read on
 * its own and never carried on `Candidate`. The assessment fields are null until a run has succeeded.
 */
export interface CandidateAiAssessment {
  summary: string | null;
  technical: CompetencyPanelAssessment | null;
  behavioural: CompetencyPanelAssessment | null;
  assessedAt: string | null;
  nationalityReading: NationalityReading | null;
  failedAt: string | null;
}

/** What a note records, as the composer offers it. */
export type PersonNoteKind = "general" | "call" | "meeting" | "email";

/** A note on a workspace person, shared by every position that maps them. Staff-only. */
export interface PersonNote {
  id: string;
  kind: PersonNoteKind;
  body: string;
  pinned: boolean;
  /** The position the note is about, or null for a note about the person. */
  projectId: string | null;
  projectTitle: string | null;
  authorUserId: string;
  authorName: string | null;
  authorAvatarUrl: string | null;
  createdAt: string;
  editedAt: string | null;
  editedByName: string | null;
  /** Whether the viewer may change or remove it: its author, or a workspace admin. */
  editable: boolean;
}

export interface WritePersonNotePayload {
  kind: PersonNoteKind;
  body: string;
  /** Read on the workspace's routes only: the position the note is about, or none. */
  projectId?: string | null;
}

/** One mandate a person is mapped on, with that mandate's status and who filed them there. */
export interface PersonPosition {
  candidateId: string;
  projectId: string;
  positionTitle: string | null;
  status: CandidateStatus;
  addedByUserId: string;
  addedByName: string | null;
  addedAt: string;
  source: CandidateSource;
  /** Whether the viewer may move this mapping's status: they hold the position's WORK_EXECUTE. */
  workable: boolean;
}

/** The kinds of line a person's history holds; the server's `PersonActivityKind` names. */
export type PersonActivityKind =
  | "ADDED_TO_POOL"
  | "MAPPED"
  | "UNMAPPED"
  | "STATUS_CHANGED"
  | "PROFILE_EDITED"
  | "CONTACTS_EDITED"
  | "CONTACT_FOUND"
  | "RESEARCHED"
  | "AI_ASSESSED"
  | "NOTE_ADDED"
  | "NOTE_EDITED"
  | "NOTE_REMOVED"
  | "TAGGED"
  | "UNTAGGED"
  | "OWNER_CHANGED"
  | "DO_NOT_CONTACT_SET"
  | "DO_NOT_CONTACT_CLEARED";

/** The timeline's filter chips, as the server's `TimelineGroup` tokens. */
export type TimelineGroup = "positions" | "notes" | "contacts" | "profile" | "tags";

export interface PersonTimelineEntry {
  id: number;
  kind: PersonActivityKind;
  occurredAt: string;
  actorUserId: string | null;
  actorName: string | null;
  actorAvatarUrl: string | null;
  personId: string;
  personName: string | null;
  projectId: string | null;
  projectTitle: string | null;
  /** An allowlist: door, from/to, channel, found, vendor, noteId, kind, … — every value a string. */
  details: Record<string, string>;
  /** The note's opening words while it exists; null once removed. */
  noteExcerpt: string | null;
}

export interface PersonTimelinePage {
  entries: PersonTimelineEntry[];
  nextCursor: number | null;
}

/** The six swatches a tag is drawn in; palette roles, so the theme decides the shade. */
export type CandidateTagColour = "green" | "accent" | "neutral" | "violet" | "adjacent" | "inferred";

/** One of the workspace's own labels on its people. */
export interface CandidateTag {
  id: string;
  label: string;
  colour: CandidateTagColour;
  retired: boolean;
  /** How many of the workspace's people hold it. */
  holders: number;
}

export type PoolView = "all" | "mine" | "active" | "unplaced";
export type TagMatch = "any" | "all" | "none";
export type PoolSortField = "name" | "location" | "positions" | "activity";

/** What the Candidates page asks of the pool; an empty value is no filter. */
export interface PoolFilters {
  q: string;
  view: PoolView;
  tagIds: string[];
  tagMatch: TagMatch;
  position: string;
  status: CandidateStatus | "";
  /** A user id, "nobody" for people nobody owns, or empty for anyone. */
  owner: string;
  country: string;
  sort: PoolSortField;
  direction: "asc" | "desc";
}

export interface PoolRow {
  personId: string;
  fullName: string;
  title: string | null;
  companyName: string | null;
  locationCity: string | null;
  locationCountry: string | null;
  linkedinUrl: string | null;
  /** When research last landed; null for someone never researched, who has no photo. */
  enrichedAt: string | null;
  doNotContact: boolean;
  yearsExperience: number | null;
  /** Posts in their recorded career. */
  careerRoles: number;
  /** Which channels the contact ledger holds; the values stay in the drawer. */
  hasEmail: boolean;
  hasPhone: boolean;
  /** Most recently added first. */
  positions: PersonPosition[];
  tagIds: string[];
  ownerUserId: string | null;
  lastActivity: PersonTimelineEntry | null;
}

export interface PoolPage {
  people: PoolRow[];
  totalCount: number;
  viewCounts: Record<PoolView, number>;
  poolSize: number;
  countries: string[];
}

export interface DoNotContact {
  reason: string | null;
  setByUserId: string | null;
  setByName: string | null;
  setAt: string | null;
}

/** A workspace person as the Candidates drawer reads them. Staff-only. */
export interface PersonRecord {
  personId: string;
  fullName: string;
  title: string | null;
  companyName: string | null;
  seniority: CandidateSeniority | null;
  linkedinUrl: string | null;
  enrichedAt: string | null;
  locationCity: string | null;
  locationCountry: string | null;
  nationality: string | null;
  gender: CandidateGender | null;
  yearsExperience: number | null;
  summary: string | null;
  compensation: CandidateCompensation;
  career: CandidateCareerEntry[];
  contacts: CandidateContacts;
  positions: PersonPosition[];
  ownerUserId: string | null;
  doNotContact: DoNotContact | null;
  tagIds: string[];
  source: CandidateSource;
  addedAt: string;
  addedByUserId: string | null;
  addedByName: string | null;
}

export interface MapToPositionResult {
  added: number;
  alreadyIn: number;
}

