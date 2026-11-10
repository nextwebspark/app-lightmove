import { request, requestBlob } from "../../../lib/apiClient";

/**
 * Settings → Data & retention: how long candidates are kept, who is past it (an admin keeps or deletes them; nothing
 * is deleted on its own), and the suppression list, whose entries are one-way hashes and never read back as a name.
 */

export const CANDIDATE_RETENTION_KEY = ["candidate-retention"] as const;
export const RETENTION_DUE_KEY = ["candidate-retention", "due"] as const;
export const SUPPRESSIONS_KEY = ["suppressions"] as const;
export const WORKSPACE_EXPORT_KEY = ["workspace-export"] as const;

/** The most people the review list draws at once; keeping or deleting them brings the next ones up. */
export const DUE_PAGE_SIZE = 200;

export interface CandidateRetention {
  /** Null: never delete. */
  months: number | null;
  /** False while the workspace keeps Uncava's default. */
  chosen: boolean;
  defaultMonths: number | null;
  options: number[];
}

export interface RetentionDuePerson {
  personId: string;
  fullName: string;
  title: string | null;
  employer: string | null;
  lastActivityAt: string;
  /** A timeline kind, `RETENTION_KEPT` for an admin's keep, or null where nothing was ever recorded. */
  lastActivityKind: string | null;
  /** Positions that held them, none of them live. */
  positions: number;
  ownerName: string | null;
}

export interface RetentionDue {
  months: number | null;
  cutoff: string | null;
  total: number;
  people: RetentionDuePerson[];
}

export interface RetentionErased {
  erased: number;
  /** Touched, mapped or restricted since the list was read. */
  noLongerDue: number;
  /** An email to them was going out; they wait for the next review. */
  sendInFlight: number;
}

export type SuppressionKind = "linkedin" | "email";
export type SuppressionReason = "erased" | "objected" | "manual";

export interface Suppression {
  id: string;
  kind: SuppressionKind;
  reason: SuppressionReason;
  note: string | null;
  addedBy: { userId: string; name: string; avatarUrl: string | null } | null;
  createdAt: string;
}

export interface NewSuppression {
  linkedinUrl: string | null;
  email: string | null;
  reason: "manual" | "objected";
  note: string | null;
}

export function getCandidateRetention(signal?: AbortSignal): Promise<CandidateRetention> {
  return request<CandidateRetention>("/workspace/retention", { signal });
}

export function putCandidateRetention(months: number | null): Promise<CandidateRetention> {
  return request<CandidateRetention>("/workspace/retention", { method: "PUT", body: { months } });
}

export function getRetentionDue(signal?: AbortSignal): Promise<RetentionDue> {
  return request<RetentionDue>(`/workspace/retention/due?limit=${DUE_PAGE_SIZE}`, { signal });
}

export function keepDue(personIds: string[]): Promise<{ kept: number }> {
  return request<{ kept: number }>("/workspace/retention/due/keep", { method: "POST", body: { personIds } });
}

/** The most people one delete request carries: each is a whole erasure, so a long selection goes in batches. */
export const ERASE_BATCH_SIZE = 25;

/** Batch by batch, each already committed when the next is sent; a failure part-way leaves the earlier ones gone. */
export async function eraseDue(personIds: string[]): Promise<RetentionErased> {
  const result: RetentionErased = { erased: 0, noLongerDue: 0, sendInFlight: 0 };
  for (let start = 0; start < personIds.length; start += ERASE_BATCH_SIZE) {
    const batch = await request<RetentionErased>("/workspace/retention/due/erase", {
      method: "POST",
      body: { personIds: personIds.slice(start, start + ERASE_BATCH_SIZE) },
    });
    result.erased += batch.erased;
    result.noLongerDue += batch.noLongerDue;
    result.sendInFlight += batch.sendInFlight;
  }
  return result;
}

export function getSuppressions(signal?: AbortSignal): Promise<Suppression[]> {
  return request<Suppression[]>("/workspace/suppressions", { signal });
}

export function addSuppression(entry: NewSuppression): Promise<Suppression[]> {
  return request<Suppression[]>("/workspace/suppressions", { method: "POST", body: entry });
}

export function removeSuppression(suppressionId: string): Promise<void> {
  return request<void>(`/workspace/suppressions/${suppressionId}`, { method: "DELETE" });
}

/** Only READY has a file; a READY one past its expiry already reads EXPIRED. */
export type WorkspaceExportStatus = "RUNNING" | "READY" | "FAILED" | "EXPIRED";

export interface WorkspaceExport {
  id: string;
  status: WorkspaceExportStatus;
  fileName: string;
  sizeBytes: number | null;
  people: number | null;
  documents: number | null;
  positions: number | null;
  requestedAt: string;
  requestedByName: string | null;
  completedAt: string | null;
  expiresAt: string | null;
}

/** Null until the workspace's first export. */
export async function getLatestWorkspaceExport(signal?: AbortSignal): Promise<WorkspaceExport | null> {
  return (await request<WorkspaceExport | undefined>("/workspace/exports/latest", { signal })) ?? null;
}

export function startWorkspaceExport(): Promise<WorkspaceExport> {
  return request<WorkspaceExport>("/workspace/exports", { method: "POST" });
}

export function downloadWorkspaceExport(exportId: string): Promise<Blob> {
  return requestBlob(`/workspace/exports/${exportId}/download`);
}
