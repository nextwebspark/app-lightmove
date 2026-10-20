import { request } from "../../../lib/apiClient";
import { CANDIDATES_KEY_PREFIX } from "./candidatesApi";
import type {
  PersonNote,
  PersonPosition,
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

/** The person's history, newest first; `before` is the previous page's `nextCursor`. */
export function getPersonTimeline(
  projectId: string,
  candidateId: string,
  before?: number | null,
  signal?: AbortSignal,
): Promise<PersonTimelinePage> {
  const params = new URLSearchParams();
  if (before != null) params.set("before", String(before));
  const query = params.size > 0 ? `?${params}` : "";
  return request<PersonTimelinePage>(`${personUrl(projectId, candidateId)}/timeline${query}`, {
    signal,
  });
}
