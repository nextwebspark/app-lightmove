import { request } from "../../../lib/apiClient";
import type { NoteFormat } from "../../notes/lib/noteFormat";
import { TRIAGE_KEY_PREFIX } from "./triageApi";

/**
 * The notes on one of a position's companies. Staff-only: a client seat is refused every one of these, and the
 * company drawer never asks. Keyed under the triage prefix, so whatever refreshes the grid refreshes these too.
 */

export type CompanyNoteKind = "general" | "call" | "meeting" | "email";

export interface CompanyNote {
  id: string;
  kind: CompanyNoteKind;
  body: string;
  /** How `body` is to be read: as typed, or the editor's Markdown. */
  format: NoteFormat;
  pinned: boolean;
  authorUserId: string;
  authorName: string | null;
  authorAvatarUrl: string | null;
  createdAt: string;
  editedAt: string | null;
  editedByName: string | null;
  /** The viewer may change, remove or restore it: its author, or a workspace admin. */
  editable: boolean;
}

export interface WriteCompanyNotePayload {
  kind: CompanyNoteKind;
  body: string;
  format?: NoteFormat;
}

export const COMPANY_NOTES_KEY = (projectId: string, triageCompanyId: string) =>
  [...TRIAGE_KEY_PREFIX(projectId), "notes", triageCompanyId] as const;

const notesUrl = (projectId: string, triageCompanyId: string) =>
  `/projects/${projectId}/triage/${triageCompanyId}/notes`;

export function getCompanyNotes(projectId: string, triageCompanyId: string, signal?: AbortSignal) {
  return request<CompanyNote[]>(notesUrl(projectId, triageCompanyId), { signal });
}

export function writeCompanyNote(projectId: string, triageCompanyId: string, note: WriteCompanyNotePayload) {
  return request<CompanyNote>(notesUrl(projectId, triageCompanyId), { method: "POST", body: note });
}

export function reviseCompanyNote(
  projectId: string,
  triageCompanyId: string,
  noteId: string,
  note: WriteCompanyNotePayload,
) {
  return request<CompanyNote>(`${notesUrl(projectId, triageCompanyId)}/${noteId}`, { method: "PUT", body: note });
}

export function removeCompanyNote(projectId: string, triageCompanyId: string, noteId: string) {
  return request<void>(`${notesUrl(projectId, triageCompanyId)}/${noteId}`, { method: "DELETE" });
}

/** Takes a delete back while the note is still held; a live note answers as it is. */
export function restoreCompanyNote(projectId: string, triageCompanyId: string, noteId: string) {
  return request<CompanyNote>(`${notesUrl(projectId, triageCompanyId)}/${noteId}/restore`, { method: "POST" });
}

export function pinCompanyNote(projectId: string, triageCompanyId: string, noteId: string, pinned: boolean) {
  return request<CompanyNote>(`${notesUrl(projectId, triageCompanyId)}/${noteId}/pin`, {
    method: "PATCH",
    body: { pinned },
  });
}
