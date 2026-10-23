import { request } from "../../../lib/apiClient";

/**
 * The Notetaker: a Recall.ai bot sent into a call with an executive, whose transcript becomes a Meeting note. The
 * position's `WORK_EXECUTE`, like the meetings it records.
 */

export const NOTETAKER_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker"] as const;

export const NOTETAKER_TRANSCRIPT_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker", recordingId, "transcript"] as const;

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
