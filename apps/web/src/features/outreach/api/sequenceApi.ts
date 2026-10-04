import { request } from "../../../lib/apiClient";

/**
 * A position's outreach sequences and the Add to sequence dialog's reads and press. Every route is the
 * position's `WORK_EXECUTE`: a client seat sees none of it.
 */

export const SEQUENCES_KEY = (projectId: string) => ["outreach", projectId, "sequences"] as const;
export const SEQUENCE_KEY = (projectId: string, sequenceId: string) =>
  [...SEQUENCES_KEY(projectId), sequenceId] as const;

export interface SequenceStep {
  /** Working days after the step before; always 0 on the first, which goes when the consultant starts. */
  delayWorkingDays: number;
  /** The first step's only; the others reply in its thread. */
  subject: string | null;
  body: string;
}

export interface Sequence {
  id: string;
  name: string;
  steps: SequenceStep[];
  createdByName: string | null;
  /** Everyone ever put on it; a sequence with anyone is Live, one with nobody a Draft. */
  enrolledCount: number;
  /** Emails it has sent, follow-ups included. */
  sentCount: number;
  /** People who answered it. */
  repliedCount: number;
  updatedAt: string;
}

export interface SaveSequence {
  name: string;
  steps: SequenceStep[];
}

export type OutreachSkipReason = "NO_EMAIL" | "DO_NOT_CONTACT" | "LEFT_THE_RUNNING" | "ALREADY_IN_SEQUENCE";

export interface RecipientEmail {
  address: string;
  kind: "work" | "personal" | null;
  verified: boolean;
}

/** The server's values for a person's tokens, so the review shows exactly what Start freezes. */
export interface RecipientTokens {
  firstName: string | null;
  currentTitle: string | null;
  currentCompany: string | null;
  positionTitle: string | null;
  location: string | null;
  senderFirstName: string | null;
  /** The sender's booking link; null until their first Start that uses it makes one. */
  bookingLink?: string | null;
}

export interface EnrollmentCandidate {
  candidateId: string;
  personId: string;
  triageCompanyId: string | null;
  fullName: string;
  title: string | null;
  companyName: string | null;
  emails: RecipientEmail[];
  /** Null: can be added. Otherwise shown dimmed with the reason, and refused at Start. */
  skipReason: OutreachSkipReason | null;
  inSequence: string | null;
  tokens: RecipientTokens;
}

/** Who the dialog was opened on: one executive from the drawer, or the companies ticked on a stage. */
export interface EnrollmentScope {
  candidateIds?: string[];
  triageCompanyIds?: string[];
}

export interface DraftedOpener {
  candidateId: string;
  opener: string | null;
}

export interface EnrollPerson {
  candidateId: string;
  toAddress: string;
  opener: string | null;
  openerEdited: boolean;
}

export function getSequences(projectId: string, signal?: AbortSignal): Promise<Sequence[]> {
  return request<{ sequences: Sequence[] }>(`/projects/${projectId}/outreach/sequences`, { signal }).then(
    (response) => response.sequences,
  );
}

export function getSequence(projectId: string, sequenceId: string, signal?: AbortSignal): Promise<Sequence> {
  return request<Sequence>(`/projects/${projectId}/outreach/sequences/${sequenceId}`, { signal });
}

export function createSequence(projectId: string, sequence: SaveSequence): Promise<Sequence> {
  return request<Sequence>(`/projects/${projectId}/outreach/sequences`, {
    method: "POST",
    body: sequence,
  });
}

export function updateSequence(projectId: string, sequenceId: string, sequence: SaveSequence): Promise<Sequence> {
  return request<Sequence>(`/projects/${projectId}/outreach/sequences/${sequenceId}`, {
    method: "PUT",
    body: sequence,
  });
}

export function deleteSequence(projectId: string, sequenceId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/outreach/sequences/${sequenceId}`, { method: "DELETE" });
}

export function getEnrollmentCandidates(
  projectId: string,
  scope: EnrollmentScope,
  signal?: AbortSignal,
): Promise<EnrollmentCandidate[]> {
  return request<{ people: EnrollmentCandidate[] }>(`/projects/${projectId}/outreach/enrollment-candidates`, {
    method: "POST",
    body: scope,
    signal,
  }).then((response) => response.people);
}

/** The server takes at most this many per press, and each press is one unit of the AI budget. */
export const OPENERS_PER_PRESS = 10;

/** `StartSequenceRequest`'s `@Size(max = 50)`: one Start enrolls at most this many. */
export const MAX_PEOPLE_PER_START = 50;

export function draftOpeners(projectId: string, candidateIds: string[]): Promise<DraftedOpener[]> {
  return request<{ openers: DraftedOpener[] }>(`/projects/${projectId}/outreach/openers`, {
    method: "POST",
    body: { candidateIds },
  }).then((response) => response.openers);
}

export function startSequence(
  projectId: string,
  sequenceId: string,
  people: EnrollPerson[],
): Promise<{ enrolled: number }> {
  return request<{ enrolled: number }>(`/projects/${projectId}/outreach/sequences/${sequenceId}/enrollments`, {
    method: "POST",
    body: { people },
  });
}
