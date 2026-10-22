import { request } from "../../../lib/apiClient";

/**
 * Uncava Notetaker: the bot on an executive's calls (the position's `WORK_EXECUTE`), and its two standing switches —
 * the workspace's (Settings → Integrations, `WORKSPACE_MANAGE` to change) and the consultant's own (Profile).
 */

export const NOTETAKER_KEY_PREFIX = (projectId: string) => ["outreach", projectId, "notetaker"] as const;

export const NOTETAKER_MEETINGS_KEY = (projectId: string, candidateId: string) =>
  [...NOTETAKER_KEY_PREFIX(projectId), candidateId] as const;

export const NOTETAKER_TRANSCRIPT_KEY = (projectId: string, candidateId: string, meetingId: string) =>
  [...NOTETAKER_KEY_PREFIX(projectId), candidateId, "transcript", meetingId] as const;

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
  | "PROCESSING_FAILED";

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

export interface NotetakerMeetings {
  offered: boolean;
  workspaceEnabled: boolean;
  myMeetingsEnabled: boolean;
  meetings: NotetakerMeeting[];
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
  recordedUntil: string | null;
  lines: TranscriptLine[];
}

export interface WorkspaceNotetaker {
  offered: boolean;
  enabled: boolean;
  platforms: NotetakerPlatform[];
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

export function getTranscript(
  projectId: string,
  candidateId: string,
  meetingId: string,
  signal?: AbortSignal,
): Promise<NotetakerTranscript> {
  return request<NotetakerTranscript>(`${meetingsPath(projectId, candidateId)}/${meetingId}/transcript`, { signal });
}

export function getWorkspaceNotetaker(signal?: AbortSignal): Promise<WorkspaceNotetaker> {
  return request<WorkspaceNotetaker>("/workspace/notetaker", { signal });
}

export function setWorkspaceNotetaker(enabled: boolean): Promise<WorkspaceNotetaker> {
  return request<WorkspaceNotetaker>("/workspace/notetaker", { method: "PUT", body: { enabled } });
}

export function getMyNotetaker(signal?: AbortSignal): Promise<MyNotetaker> {
  return request<MyNotetaker>("/me/notetaker", { signal });
}

export function setMyNotetaker(enabled: boolean): Promise<MyNotetaker> {
  return request<MyNotetaker>("/me/notetaker", { method: "PUT", body: { enabled } });
}
