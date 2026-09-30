import { request } from "../../../lib/apiClient";
import { TRIAGE_KEY_PREFIX } from "./triageApi";

/** Find executives: the POST answers 202 with the run, which then moves on its own. */

/** Whether this deployment offers the run, and the numbers the confirm dialog states. Read once. */
export const SOURCING_CONFIG_KEY = ["executiveSourcing", "config"] as const;

/** Under the triage prefix, so a write that refreshes the grid re-reads the run beside it. */
export const SOURCING_LATEST_KEY = (projectId: string) =>
  [...TRIAGE_KEY_PREFIX(projectId), "executive-sourcing", "latest"] as const;

export interface SourcingConfig {
  enabled: boolean;
  maxCompaniesPerRun: number;
  hitsPerCompany: number;
  picksPerCompany: number;
  /** A run still in progress this long after it started was lost with its server. */
  lostAfterSeconds: number;
}

export type SourcingRunStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";

export type SourcingOutcome =
  | "FILED"
  | "NO_LINKEDIN_PAGE"
  | "NO_HITS"
  | "ALL_ALREADY_MAPPED"
  | "NOTHING_FIT"
  | "FAILED"
  | "NOT_REACHED";

export interface SourcingPick {
  name: string;
  /** Null since a rerank stopped choosing the picks. */
  score: number | null;
  reason: string | null;
  /** Null when filing the pick was refused — someone of that name was already mapped there. */
  candidateId: string | null;
}

export interface SourcingCompanyOutcome {
  triageCompanyId: string;
  companyName: string;
  outcome: SourcingOutcome;
  /** People the search answered with, bought or reused. */
  seen: number;
  /** People who fitted in all; more than `seen` means the per-company cap cut the search short. Null when unknown. */
  matched: number | null;
  filed: number;
  skipped: number;
  picks: SourcingPick[];
}

export interface SourcingSearch {
  seniorityWords: string[];
  functionWords: string[];
  excludedWords: string[];
}

export interface SourcingRun {
  id: string;
  status: SourcingRunStatus;
  companyNames: string[];
  companiesTotal: number;
  companiesDone: number;
  executivesFiled: number;
  /** Profiles bought from the vendor on this run. */
  vendorHits: number;
  /** Profiles the people cache answered free — someone already bought, by any mandate. */
  cachedHits: number;
  /** The title words the run searched by; null until the run has proposed them. */
  searchedFor: SourcingSearch | null;
  outcomes: SourcingCompanyOutcome[];
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
}

export function getSourcingConfig(signal?: AbortSignal): Promise<SourcingConfig> {
  return request<SourcingConfig>("/executive-sourcing/config", { signal });
}

/** Empty ids: the server takes the first few In-universe companies with nobody mapped. */
export function startSourcing(projectId: string, triageCompanyIds: string[]): Promise<SourcingRun> {
  return request<SourcingRun>(`/projects/${projectId}/executive-sourcing`, {
    method: "POST",
    body: { triageCompanyIds },
  });
}

export function getSourcingRun(projectId: string, runId: string, signal?: AbortSignal): Promise<SourcingRun> {
  return request<SourcingRun>(`/projects/${projectId}/executive-sourcing/${runId}`, { signal });
}

/** Null when the mandate has never run one (the server answers 204). */
export async function getLatestSourcingRun(projectId: string, signal?: AbortSignal): Promise<SourcingRun | null> {
  const run = await request<SourcingRun | undefined>(`/projects/${projectId}/executive-sourcing/latest`, { signal });
  return run ?? null;
}

export function isSourcingInProgress(run: SourcingRun | null | undefined): boolean {
  return run?.status === "QUEUED" || run?.status === "RUNNING";
}
