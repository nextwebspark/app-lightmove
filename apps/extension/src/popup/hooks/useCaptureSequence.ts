import { useMutation } from "@tanstack/react-query";
import type {
  CaptureCompanyRequest,
  CapturedCandidate,
  NoteRecord,
  SaveCandidateRequest,
  TriagedCompany,
  WriteNotePayload,
} from "../../api/types";
import { askServiceWorker } from "../../background/extensionMessages";
import { askOrThrow } from "../lib/askOrThrow";
import { CaptureRefusal } from "../lib/captureRefusal";

/**
 * What a capture filed, and what of the rest did not land. The row stands either way: a note or a tag that failed is
 * kept for a retry on the record, never thrown away with a refusal that would read as if nothing was saved.
 */
export interface FiledCapture<TFiled> {
  filed: TFiled;
  filedNote: NoteRecord | null;
  unsentNote: { note: WriteNotePayload; reason: string } | null;
  untaggedTagIds: string[];
}

export interface PersonCaptureAttempt {
  projectId: string;
  candidate: SaveCandidateRequest;
  note: WriteNotePayload | null;
  tagIds: string[];
}

/**
 * Files a person: the row, then the note through the notes routes (so it keeps its kind, its formatting and who reads
 * it — the door's own `note` field is plain), then each tag on the person.
 */
export function useCapturePerson() {
  return useMutation<FiledCapture<CapturedCandidate>, CaptureRefusal, PersonCaptureAttempt>({
    mutationFn: async ({ projectId, candidate, note, tagIds }) => {
      const filed = await askOrThrow({ kind: "captureCandidate", projectId, candidate });
      const target = { subject: "person" as const, projectId, candidateId: filed.id };
      const { filedNote, unsentNote } = await fileNote(note, (payload) =>
        askServiceWorker({ kind: "writeNote", target, note: payload }),
      );
      const untaggedTagIds: string[] = [];
      for (const tagId of tagIds) {
        const tagged = await askServiceWorker({ kind: "tagPerson", personId: filed.personId, tagId });
        if (!tagged.ok) untaggedTagIds.push(tagId);
      }
      return { filed, filedNote, unsentNote, untaggedTagIds };
    },
  });
}

export interface CompanyCaptureAttempt {
  projectId: string;
  capture: CaptureCompanyRequest;
  note: WriteNotePayload | null;
}

/** Files a company into the universe, then its note through the company notes route. */
export function useCaptureCompany() {
  return useMutation<FiledCapture<TriagedCompany>, CaptureRefusal, CompanyCaptureAttempt>({
    mutationFn: async ({ projectId, capture, note }) => {
      const filed = await askOrThrow({ kind: "captureCompany", projectId, capture });
      const target = { subject: "company" as const, projectId, triageCompanyId: filed.id };
      const { filedNote, unsentNote } = await fileNote(note, (payload) =>
        askServiceWorker({ kind: "writeNote", target, note: payload }),
      );
      return { filed, filedNote, unsentNote, untaggedTagIds: [] };
    },
  });
}

async function fileNote(
  note: WriteNotePayload | null,
  send: (note: WriteNotePayload) => ReturnType<typeof askServiceWorker<"writeNote">>,
): Promise<Pick<FiledCapture<unknown>, "filedNote" | "unsentNote">> {
  if (!note) return { filedNote: null, unsentNote: null };
  const written = await send(note);
  return written.ok
    ? { filedNote: written.value, unsentNote: null }
    : { filedNote: null, unsentNote: { note, reason: written.message } };
}

/**
 * Takes a capture back: the note it filed first — a person's notes outlive the row, being the person's — then the row,
 * which takes the company filed for them where nobody has worked it. A company's notes go with its row. A row already
 * gone is undone; a row that stays puts the note back.
 */
export function useUndoCapture() {
  return useMutation<null, CaptureRefusal, { projectId: string; candidateId?: string; triageCompanyId?: string; noteId?: string | null }>({
    mutationFn: async ({ projectId, candidateId, triageCompanyId, noteId }) => {
      if (!candidateId) {
        return removedUnlessGone(() => askOrThrow({ kind: "removeTriageCompany", projectId, triageCompanyId: triageCompanyId! }));
      }
      const target = { subject: "person" as const, projectId, candidateId };
      // A note that will not go still lets the person go: the Undo the consultant asked for is the row, and a note
      // left behind is the person's, readable and removable in Uncava.
      const removedNoteId = noteId && (await askServiceWorker({ kind: "removeNote", target, noteId })).ok ? noteId : null;
      try {
        return await removedUnlessGone(() => askOrThrow({ kind: "removeCandidate", projectId, candidateId }));
      } catch (refusal) {
        if (removedNoteId) {
          await askServiceWorker({ kind: "restoreNote", target, noteId: removedNoteId });
        }
        throw refusal;
      }
    },
  });
}

async function removedUnlessGone(remove: () => Promise<null>): Promise<null> {
  try {
    return await remove();
  } catch (refusal) {
    if (refusal instanceof CaptureRefusal && refusal.code === "NOT_FOUND") {
      return null;
    }
    throw refusal;
  }
}
