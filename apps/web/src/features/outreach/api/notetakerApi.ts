import { request } from "../../../lib/apiClient";

/**
 * The Notetaker: a Recall.ai bot sent into a call with an executive, whose transcript becomes a Meeting note. The
 * position's `WORK_EXECUTE`, like the meetings it records.
 */

export const NOTETAKER_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker"] as const;

export const NOTETAKER_TRANSCRIPT_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker", recordingId, "transcript"] as const;

export const CALL_SUGGESTIONS_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker", recordingId, "suggestions"] as const;

export type NotetakerStatus =
  | "SCHEDULED"
  | "JOINING"
  | "RECORDING"
  | "PROCESSING"
  | "DONE"
  | "FAILED"
  | "CANCELLED";

export interface NotetakerRecording {
  id: string;
  /** The meeting row it was sent for; a calendar's re-read can replace that row, so `startsAt` and `title` match too. */
  meetingId: string | null;
  startsAt: string;
  title: string | null;
  status: NotetakerStatus;
  /** Recall's sub-code or our own word, worded by `lib/notetakerChip.ts`. */
  failureReason: string | null;
  hasTranscript: boolean;
  retryable: boolean;
  noteId: string | null;
  /** Someone pressed Suggest changes on this call. */
  suggestionsAsked: boolean;
  /** Changes it suggested that nobody has applied or skipped yet. */
  pendingSuggestions: number;
}

export interface CandidateNotetaker {
  offered: boolean;
  recordings: NotetakerRecording[];
  /** Upcoming rows the caller may send a bot to: on their own calendar or booked by them, with a video link. */
  recordableMeetingIds: string[];
}

export interface NotetakerTranscriptLine {
  at: string;
  speaker: string;
  text: string;
}

export interface NotetakerTranscript {
  recordingId: string;
  title: string | null;
  startsAt: string;
  lines: NotetakerTranscriptLine[];
}

export type CallSuggestionKind =
  | "NOTICE_PERIOD"
  | "CURRENT_PACKAGE"
  | "LOCATION"
  | "OPEN_TO_RELOCATE"
  | "MOTIVATION"
  | "EXPECTED_PACKAGE"
  | "TASK";

export type CallSuggestionStatus = "PENDING" | "APPLIED" | "SKIPPED";

/** Which parts are set follows the kind: `text`, a place, or a package's currency and annual figures. */
export interface SuggestedValue {
  text: string | null;
  city: string | null;
  country: string | null;
  currency: string | null;
  baseSalary: number | null;
  bonus: number | null;
  allowances: number | null;
  /** How monthly figures were annualised, when they were. */
  working: string | null;
}

export interface CallSuggestion {
  id: string;
  kind: CallSuggestionKind;
  status: CallSuggestionStatus;
  value: SuggestedValue;
  /** What the record holds now, read live; null for a task. */
  current: SuggestedValue | null;
  /** The executive's own words, and when in the call they said them. */
  said: string | null;
  at: string | null;
  /** False once the record moved on so it can't be applied, such as the tag being retired. */
  available: boolean;
  edited: boolean;
}

export interface CallSuggestions {
  recordingId: string;
  title: string | null;
  startsAt: string;
  asked: boolean;
  mayAskAgain: boolean;
  suggestions: CallSuggestion[];
}

export type PackageField = "baseSalary" | "bonus" | "allowances";

export interface ApplyCallSuggestion {
  value?: Partial<Omit<SuggestedValue, "working">>;
  packageFields?: PackageField[];
}

function candidatePath(projectId: string, candidateId: string): string {
  return `/projects/${projectId}/outreach/candidates/${candidateId}`;
}

export function getNotetaker(projectId: string, candidateId: string, signal?: AbortSignal): Promise<CandidateNotetaker> {
  return request<CandidateNotetaker>(`${candidatePath(projectId, candidateId)}/notetaker`, { signal });
}

export function scheduleNotetaker(projectId: string, candidateId: string, meetingId: string): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(`${candidatePath(projectId, candidateId)}/meetings/${meetingId}/notetaker`, {
    method: "POST",
  });
}

export function cancelNotetaker(projectId: string, candidateId: string, recordingId: string): Promise<void> {
  return request<void>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}`, { method: "DELETE" });
}

export function retryNotetaker(projectId: string, candidateId: string, recordingId: string): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/retry`, {
    method: "POST",
  });
}

export function getTranscript(
  projectId: string,
  candidateId: string,
  recordingId: string,
  signal?: AbortSignal,
): Promise<NotetakerTranscript> {
  return request<NotetakerTranscript>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/transcript`, {
    signal,
  });
}

function suggestionsPath(projectId: string, candidateId: string, recordingId: string): string {
  return `${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/suggestions`;
}

export function getCallSuggestions(
  projectId: string,
  candidateId: string,
  recordingId: string,
  signal?: AbortSignal,
): Promise<CallSuggestions> {
  return request<CallSuggestions>(suggestionsPath(projectId, candidateId, recordingId), { signal });
}

/** Spends a model call: only ever on the consultant's press. */
export function suggestChanges(projectId: string, candidateId: string, recordingId: string): Promise<CallSuggestions> {
  return request<CallSuggestions>(suggestionsPath(projectId, candidateId, recordingId), { method: "POST" });
}

export function decideCallSuggestion(
  projectId: string,
  candidateId: string,
  recordingId: string,
  suggestionId: string,
  decision: "apply" | "skip" | "reopen",
  body?: ApplyCallSuggestion,
): Promise<CallSuggestions> {
  return request<CallSuggestions>(`${suggestionsPath(projectId, candidateId, recordingId)}/${suggestionId}/${decision}`, {
    method: "POST",
    ...(body && { body }),
  });
}
