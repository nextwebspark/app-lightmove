import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { NoteRecord, NoteTarget, WriteNotePayload } from "./types";

/**
 * A person's notes from a position, or a company's notes on it — the routes the web app's two drawers use. Both are
 * staff-only (`WORK_EXECUTE`), as the panel is.
 */

function notesUrl(target: NoteTarget): string {
  return target.subject === "person"
    ? `/projects/${target.projectId}/candidates/${target.candidateId}/notes`
    : `/projects/${target.projectId}/triage/${target.triageCompanyId}/notes`;
}

export function listNotes(api: LightMoveApiClient, target: NoteTarget): Promise<NoteRecord[]> {
  return api.request<NoteRecord[]>(notesUrl(target));
}

export function writeNote(api: LightMoveApiClient, target: NoteTarget, note: WriteNotePayload): Promise<NoteRecord> {
  return api.request<NoteRecord>(notesUrl(target), { method: "POST", body: bodyFor(target, note) });
}

export function reviseNote(
  api: LightMoveApiClient,
  target: NoteTarget,
  noteId: string,
  note: WriteNotePayload,
): Promise<NoteRecord> {
  return api.request<NoteRecord>(`${notesUrl(target)}/${noteId}`, { method: "PUT", body: bodyFor(target, note) });
}

export function removeNote(api: LightMoveApiClient, target: NoteTarget, noteId: string): Promise<void> {
  return api.request<void>(`${notesUrl(target)}/${noteId}`, { method: "DELETE" });
}

export function restoreNote(api: LightMoveApiClient, target: NoteTarget, noteId: string): Promise<NoteRecord> {
  return api.request<NoteRecord>(`${notesUrl(target)}/${noteId}/restore`, { method: "POST" });
}

export function pinNote(
  api: LightMoveApiClient,
  target: NoteTarget,
  noteId: string,
  pinned: boolean,
): Promise<NoteRecord> {
  return api.request<NoteRecord>(`${notesUrl(target)}/${noteId}/pin`, { method: "PATCH", body: { pinned } });
}

/** A company's note names no position, and its request refuses a field it does not declare. */
function bodyFor(target: NoteTarget, note: WriteNotePayload): WriteNotePayload {
  if (target.subject === "company" || !note.allPositions) {
    return { kind: note.kind, body: note.body, format: note.format };
  }
  return note;
}
