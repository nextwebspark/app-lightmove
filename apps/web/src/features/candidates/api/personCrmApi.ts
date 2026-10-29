import { request } from "../../../lib/apiClient";
import { CANDIDATES_KEY_PREFIX } from "./candidatesApi";
import type {
  PersonNote,
  PersonPosition,
  PersonTimelineEntry,
  PersonTimelinePage,
  WritePersonNotePayload,
} from "./types";

/**
 * The shared person behind one of a position's rows — where else they are mapped, the notes on them and
 * their history — reached through the position's own routes, so a researcher seated on it needs nothing
 * more. Staff-only: a client seat is refused every one of these, and the drawer never asks.
 *
 * <p>Keyed under the candidates prefix, so whatever refreshes the grid refreshes these too.
 */

export const PERSON_POSITIONS_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "positions", candidateId] as const;

export const PERSON_NOTES_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "notes", candidateId] as const;

export const PERSON_TIMELINE_KEY = (projectId: string, candidateId: string) =>
  [...CANDIDATES_KEY_PREFIX(projectId), "timeline", candidateId] as const;

const personUrl = (projectId: string, candidateId: string) =>
  `/projects/${projectId}/candidates/${candidateId}`;

export function getPersonPositions(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<PersonPosition[]> {
  return request<PersonPosition[]>(`${personUrl(projectId, candidateId)}/positions`, { signal });
}

export function getPersonNotes(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<PersonNote[]> {
  return request<PersonNote[]>(`${personUrl(projectId, candidateId)}/notes`, { signal });
}

/** A note written from a position's drawer is about that position, unless it is for all positions. */
export function writePersonNote(
  projectId: string,
  candidateId: string,
  note: WritePersonNotePayload,
): Promise<PersonNote> {
  return request<PersonNote>(`${personUrl(projectId, candidateId)}/notes`, {
    method: "POST",
    body: note,
  });
}

export function revisePersonNote(
  projectId: string,
  candidateId: string,
  noteId: string,
  note: WritePersonNotePayload,
): Promise<PersonNote> {
  return request<PersonNote>(`${personUrl(projectId, candidateId)}/notes/${noteId}`, { method: "PUT", body: note });
}

export function removePersonNote(projectId: string, candidateId: string, noteId: string): Promise<void> {
  return request<void>(`${personUrl(projectId, candidateId)}/notes/${noteId}`, { method: "DELETE" });
}

/** Takes a delete back while the note is still held; a live note answers as it is. */
export function restorePersonNote(projectId: string, candidateId: string, noteId: string): Promise<PersonNote> {
  return request<PersonNote>(`${personUrl(projectId, candidateId)}/notes/${noteId}/restore`, { method: "POST" });
}

export function pinPersonNote(
  projectId: string,
  candidateId: string,
  noteId: string,
  pinned: boolean,
): Promise<PersonNote> {
  return request<PersonNote>(`${personUrl(projectId, candidateId)}/notes/${noteId}/pin`, {
    method: "PATCH",
    body: { pinned },
  });
}

/** The most of a position's history the Activity tab reads at once (the server's own ceiling for this route). */
export const POSITION_HISTORY_LIMIT = 500;

export interface PositionHistory {
  entries: PersonTimelineEntry[];
  /** True when the history holds more than {@link POSITION_HISTORY_LIMIT} lines; the oldest are left unread. */
  truncated: boolean;
}

/**
 * Everything one position's drawer may read of the person's history, newest first, in one read so the Activity tab
 * can count and filter it whole. The server holds the boundary: another position's work is never sent.
 */
export async function getPositionHistory(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<PositionHistory> {
  const read = await request<PersonTimelinePage>(
    `${personUrl(projectId, candidateId)}/timeline?limit=${POSITION_HISTORY_LIMIT}`,
    { signal },
  );
  return { entries: read.entries, truncated: read.nextCursor != null };
}
