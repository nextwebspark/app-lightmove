import { request } from "../../../lib/apiClient";

/**
 * Uncava Notetaker: the bot on an executive's calls (the position's `WORK_EXECUTE`), and its two standing switches —
 * the workspace's (Settings → Integrations, `WORKSPACE_MANAGE` to change) and the consultant's own (Profile).
 */

export const NOTETAKER_KEY_PREFIX = (projectId: string) => ["outreach", projectId, "notetaker"] as const;

export const NOTETAKER_MEETINGS_KEY = (projectId: string, candidateId: string) =>
  [...NOTETAKER_KEY_PREFIX(projectId), candidateId] as const;

export const NOTETAKER_TRANSCRIPT_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  [...NOTETAKER_KEY_PREFIX(projectId), candidateId, "transcript", recordingId] as const;

export const WORKSPACE_NOTETAKER_KEY = ["notetaker", "workspace"] as const;
export const MY_NOTETAKER_KEY = ["notetaker", "mine"] as const;

export type NotetakerPlatform = "GOOGLE_MEET" | "MICROSOFT_TEAMS" | "ZOOM";

export type NotetakerStatus =
  | "LAUNCHING"
  | "WAITING_ROOM"
  | "RECORDING"
  | "REMOVE_REQUESTED"
  | "PROCESSING"
  | "DONE"
  | "FAILED"
  | "REMOVED"
  | "SKIPPED";

export type NotetakerEndReason =
  | "DO_NOT_CONTACT"
  | "LAUNCH_FAILED"
  | "LAUNCH_TIMEOUT"
  | "JOIN_BLOCKED"
  | "NOT_ADMITTED"
  | "CONSENT_NOT_SHOWN"
  | "DEVICES_NOT_OFF"
  | "CRASHED"
  | "HEARTBEAT_LOST"
  | "PROCESSING_FAILED"
  | "BACKUP_FAILED"
  | "RECORDING_NOT_ALLOWED"
  | "FAIR_USE_REACHED"
  | "TRIAL_ENDED"
  | "ZOOM_NOT_CONNECTED";

export type NotetakerRecorder = "OWN" | "RECALL";

export interface NotetakerMeeting {
  meetingId: string;
  platform: NotetakerPlatform | null;
  /** A call this deployment's bot can join. */
  joinable: boolean;
  /** Nobody turned it off for this call. */
  enabled: boolean;
  recordingId: string | null;
  /** Null until a bot was sent, or the call skipped. */
  status: NotetakerStatus | null;
  endReason: NotetakerEndReason | null;
  recorder: NotetakerRecorder | null;
  joinedAt: string | null;
  /** Set when the bot stopped before the call ended: the note is partial. */
  recordedUntil: string | null;
  /** Its note is filed here (or about the person), so this drawer may read the transcript. */
  transcriptHere: boolean;
  noteId: string | null;
  /** Filed under another position, which is never named here. */
  notesElsewhere: boolean;
}

/** A call whose note this position may read, found by its note: its meeting row may be gone. */
export interface FiledRecording {
  recordingId: string;
  noteId: string;
  recordedUntil: string | null;
}

export interface NotetakerMeetings {
  offered: boolean;
  workspaceEnabled: boolean;
  myMeetingsEnabled: boolean;
  meetings: NotetakerMeeting[];
  recordings: FiledRecording[];
}

export interface TranscriptLine {
  at: string;
  speaker: string;
  text: string;
  /** False for a voice no caption matched, which keeps the transcriber's number. */
  named: boolean;
}

export interface NotetakerTranscript {
  recordingId: string;
  status: NotetakerStatus;
  complete: boolean;
  /** The call as it was when the bot went in. */
  title: string | null;
  startsAt: string;
  endsAt: string;
  /** Elapsed times count from here, as the note's evidence does. */
  joinedAt: string | null;
  consultantName: string | null;
  recordedUntil: string | null;
  lines: TranscriptLine[];
}

export interface WorkspaceNotetaker {
  offered: boolean;
  enabled: boolean;
  platforms: NotetakerPlatform[];
  /** The deployment has a backup recorder, a third party a call's audio goes to. */
  backupOffered: boolean;
  /** An admin turned the backup on for the workspace; off until they do. */
  backupEnabled: boolean;
}

export interface MyNotetaker {
  offered: boolean;
  workspaceEnabled: boolean;
  enabled: boolean;
}

const meetingsPath = (projectId: string, candidateId: string) =>
  `/projects/${projectId}/outreach/candidates/${candidateId}/meetings`;

export function getNotetakerMeetings(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<NotetakerMeetings> {
  return request<NotetakerMeetings>(`${meetingsPath(projectId, candidateId)}/notetaker`, { signal });
}

export function setMeetingNotetaker(
  projectId: string,
  candidateId: string,
  meetingId: string,
  enabled: boolean,
): Promise<void> {
  return request<void>(`${meetingsPath(projectId, candidateId)}/${meetingId}/notetaker`, {
    method: "PUT",
    body: { enabled },
  });
}

export function removeNotetaker(projectId: string, candidateId: string, meetingId: string): Promise<void> {
  return request<void>(`${meetingsPath(projectId, candidateId)}/${meetingId}/notetaker/remove`, { method: "POST" });
}

export function retryNotes(projectId: string, candidateId: string, meetingId: string): Promise<void> {
  return request<void>(`${meetingsPath(projectId, candidateId)}/${meetingId}/notetaker/retry`, { method: "POST" });
}

/** By the recording, never its meeting: a calendar read may delete the meeting row while the note stands. */
export function getTranscript(
  projectId: string,
  candidateId: string,
  recordingId: string,
  signal?: AbortSignal,
): Promise<NotetakerTranscript> {
  return request<NotetakerTranscript>(
    `/projects/${projectId}/outreach/candidates/${candidateId}/notetaker/recordings/${recordingId}/transcript`,
    { signal },
  );
}

export function getWorkspaceNotetaker(signal?: AbortSignal): Promise<WorkspaceNotetaker> {
  return request<WorkspaceNotetaker>("/workspace/notetaker", { signal });
}

export function setWorkspaceNotetaker(enabled: boolean): Promise<WorkspaceNotetaker> {
  return request<WorkspaceNotetaker>("/workspace/notetaker", { method: "PUT", body: { enabled } });
}

export function setWorkspaceNotetakerBackup(enabled: boolean): Promise<WorkspaceNotetaker> {
  return request<WorkspaceNotetaker>("/workspace/notetaker/backup", { method: "PUT", body: { enabled } });
}

export function getMyNotetaker(signal?: AbortSignal): Promise<MyNotetaker> {
  return request<MyNotetaker>("/me/notetaker", { signal });
}

export function setMyNotetaker(enabled: boolean): Promise<MyNotetaker> {
  return request<MyNotetaker>("/me/notetaker", { method: "PUT", body: { enabled } });
}
