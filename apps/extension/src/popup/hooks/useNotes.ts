import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { NoteRecord, NoteTarget, WriteNotePayload } from "../../api/types";
import { askOrThrow } from "../lib/askOrThrow";

const notesKey = (target: NoteTarget) =>
  ["extension", "notes", target.subject, target.projectId, target.subject === "person" ? target.candidateId : target.triageCompanyId] as const;

/** Pinned first, then newest — the web app's drawers' order. */
function inReadingOrder(notes: NoteRecord[]): NoteRecord[] {
  return [...notes].sort(
    (a, b) => Number(b.pinned) - Number(a.pinned) || Date.parse(b.createdAt) - Date.parse(a.createdAt),
  );
}

/** A person's notes from this position, or a company's on it: read, written, revised, pinned, removed and restored. */
export function useNotes(target: NoteTarget) {
  const queryClient = useQueryClient();
  const key = notesKey(target);
  const notes = useQuery<NoteRecord[]>({
    queryKey: key,
    queryFn: () => askOrThrow({ kind: "listNotes", target }),
    select: inReadingOrder,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: key });

  const write = useMutation<NoteRecord, Error, WriteNotePayload>({
    mutationFn: (note) => askOrThrow({ kind: "writeNote", target, note }),
    onSuccess: () => void refresh(),
  });
  const revise = useMutation<NoteRecord, Error, { noteId: string; note: WriteNotePayload }>({
    mutationFn: ({ noteId, note }) => askOrThrow({ kind: "reviseNote", target, noteId, note }),
    onSuccess: () => void refresh(),
  });
  const pin = useMutation<NoteRecord, Error, { noteId: string; pinned: boolean }>({
    mutationFn: ({ noteId, pinned }) => askOrThrow({ kind: "pinNote", target, noteId, pinned }),
    onSuccess: () => void refresh(),
  });
  const remove = useMutation<null, Error, string>({
    mutationFn: (noteId) => askOrThrow({ kind: "removeNote", target, noteId }),
    onSuccess: () => void refresh(),
  });
  const restore = useMutation<NoteRecord, Error, string>({
    mutationFn: (noteId) => askOrThrow({ kind: "restoreNote", target, noteId }),
    onSuccess: () => void refresh(),
  });

  return {
    notes: notes.data ?? [],
    isLoading: notes.isPending,
    loadError: notes.error,
    write,
    revise,
    pin,
    remove,
    restore,
    isBusy: revise.isPending || pin.isPending || remove.isPending || restore.isPending,
  };
}
