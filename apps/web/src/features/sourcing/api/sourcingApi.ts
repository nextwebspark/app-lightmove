import { request } from "../../../lib/apiClient";
import type { PersonResult } from "../../strategy/api/types";

/** The bands a search may take, highest first, as the server names them. */
export type SearchLevel = "C_LEVEL" | "SENIOR_VP" | "VP_HEAD_DIRECTOR" | "MANAGER" | "SPECIALIST";

/** A position's search as the panel edits it. */
export interface SearchCriteria {
  levels: SearchLevel[];
  functionTerms: string[];
  standaloneTitles: string[];
  localLevelWords: string[];
  excludedWords: string[];
  suggestions: string[];
  roleSummary: string | null;
  countryCodes: string[];
  perCompany: number;
}

export interface SavedCriteria {
  criteria: SearchCriteria;
  source: "DEFAULT" | "AI" | "EDITED";
  /** The brief changed since the AI drafted the search, so Reset would draft it differently. */
  briefChanged: boolean;
  updatedAt: string;
}

export interface SourcingConfig {
  enabled: boolean;
  maxCompaniesPerRun: number;
  perCompanyChoices: number[];
  defaultPerCompany: number;
  lostAfterSeconds: number;
}

export interface CompanyCount {
  triageCompanyId: string;
  companyName: string;
  searchable: boolean;
  match: number;
  onFile: number;
  toBuy: number;
}

export interface SourcingCounts {
  counted: boolean;
  companies: CompanyCount[];
  levels: { level: SearchLevel; count: number }[];
  words: { word: string; count: number }[];
  match: number;
  onFile: number;
  toBuy: number;
}

export type SourcingRunStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "STOPPED";

export type SourcingOutcome =
  | "FOUND"
  | "NOTHING_FIT"
  | "NO_HITS"
  | "NO_LINKEDIN_PAGE"
  | "FAILED"
  | "NOT_REACHED"
  | "STOPPED"
  | "FILED"
  | "ALL_ALREADY_MAPPED";

export interface CompanyResult {
  triageCompanyId: string;
  companyName: string;
  outcome: SourcingOutcome;
  found: number;
  fits: number;
  noTitle: number;
  notFit: number;
  bought: number;
  cached: number;
  matched: number | null;
  indexFound: number;
  titleChecks: number;
}

export interface SourcingRun {
  id: string;
  status: SourcingRunStatus;
  companies: { triageCompanyId: string; companyName: string; searchable: boolean }[];
  companiesTotal: number;
  companiesDone: number;
  suggested: number;
  profilesBought: number;
  profilesCached: number;
  stopRequested: boolean;
  searched: SearchCriteria | null;
  outcomes: CompanyResult[];
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
}

export type SuggestionVerdict = "FITS" | "NO_TITLE" | "NOT_FIT";
export type SuggestionBand = "STRONG" | "GOOD" | "POSSIBLE";

export interface SuggestedPerson {
  linkedinSlug: string;
  verdict: SuggestionVerdict;
  score: number | null;
  band: SuggestionBand | null;
  rank: number;
  /** Our codes, "CODE" or "CODE:value" — see lib/suggestionReasons. */
  reasons: string[];
  foundBy: "LIVE" | "INDEX" | "BOTH";
  /** The executive this person already is on the position. */
  candidateId: string | null;
  person: PersonResult;
}

export interface CompanySuggestions {
  triageCompanyId: string;
  companyName: string;
  runId: string;
  searchedAt: string;
  result: CompanyResult;
  /** The people cache let them go: only a new search shows them again. */
  expired: boolean;
  people: SuggestedPerson[];
}

export interface AddedPeople {
  added: number;
  alreadyOnPosition: number;
  expired: number;
  notFound: number;
  filed: { linkedinSlug: string; triageCompanyId: string; candidateId: string }[];
}

export const SOURCING_CONFIG_KEY = ["executiveSourcing", "config"] as const;
export const sourcingKeys = {
  all: (projectId: string) => ["executiveSourcing", projectId] as const,
  latest: (projectId: string) => ["executiveSourcing", projectId, "latest"] as const,
  criteria: (projectId: string) => ["executiveSourcing", projectId, "criteria"] as const,
  suggestions: (projectId: string) => ["executiveSourcing", projectId, "suggestions"] as const,
  counts: (projectId: string, companyIds: readonly string[], criteria: SearchCriteria) =>
    ["executiveSourcing", projectId, "counts", companyIds, criteria] as const,
};

const base = (projectId: string) => `/projects/${projectId}/executive-sourcing`;

export function getSourcingConfig(signal?: AbortSignal): Promise<SourcingConfig> {
  return request<SourcingConfig>("/executive-sourcing/config", { signal });
}

/** Null until the position has a saved search. */
export async function getCriteria(projectId: string, signal?: AbortSignal): Promise<SavedCriteria | null> {
  return (await request<SavedCriteria | undefined>(`${base(projectId)}/criteria`, { signal })) ?? null;
}

export function saveCriteria(projectId: string, criteria: SearchCriteria): Promise<SavedCriteria> {
  return request<SavedCriteria>(`${base(projectId)}/criteria`, { method: "PUT", body: criteria });
}

/** The AI drafts the search from the brief and the companies' countries. */
export function suggestCriteria(projectId: string, triageCompanyIds: string[]): Promise<SavedCriteria> {
  return request<SavedCriteria>(`${base(projectId)}/criteria/suggest`, {
    method: "POST",
    body: { triageCompanyIds },
  });
}

export function countSearch(
  projectId: string,
  triageCompanyIds: readonly string[],
  criteria: SearchCriteria,
  signal?: AbortSignal,
): Promise<SourcingCounts> {
  return request<SourcingCounts>(`${base(projectId)}/counts`, {
    method: "POST",
    body: { triageCompanyIds, criteria },
    signal,
  });
}

/** Empty ids: the server takes the first companies with nobody mapped. Answers 202 with the run. */
export function startSearch(projectId: string, triageCompanyIds: readonly string[]): Promise<SourcingRun> {
  return request<SourcingRun>(base(projectId), { method: "POST", body: { triageCompanyIds } });
}

export function stopSearch(projectId: string, runId: string): Promise<SourcingRun> {
  return request<SourcingRun>(`${base(projectId)}/${runId}/stop`, { method: "POST" });
}

/** Null until the position has been searched. */
export async function getLatestRun(projectId: string, signal?: AbortSignal): Promise<SourcingRun | null> {
  return (await request<SourcingRun | undefined>(`${base(projectId)}/latest`, { signal })) ?? null;
}

export function getSuggestions(projectId: string, signal?: AbortSignal): Promise<{ companies: CompanySuggestions[] }> {
  return request<{ companies: CompanySuggestions[] }>(`${base(projectId)}/suggestions`, { signal });
}

export function addSuggested(
  projectId: string,
  people: { triageCompanyId: string; linkedinSlug: string }[],
): Promise<AddedPeople> {
  return request<AddedPeople>(`${base(projectId)}/suggestions/add`, { method: "POST", body: { people } });
}

export function isInProgress(run: SourcingRun | null | undefined): boolean {
  return run?.status === "QUEUED" || run?.status === "RUNNING";
}
