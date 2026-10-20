import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import type { PersonNote } from "../api/types";
import type { NoteCardActions } from "../components/NoteParts";

/** One drawer's routes to a note, so both drawers' cards behave alike whatever they are keyed under. */
export interface NoteRoutes {
  pin: (noteId: string, pinned: boolean) => Promise<PersonNote>;
  revise: (note: PersonNote, body: string) => Promise<PersonNote>;
  remove: (noteId: string) => Promise<void>;
  restore: (noteId: string) => Promise<PersonNote>;
}

/**
 * Pin, Edit and Delete for one note card. Delete happens at once and offers Undo: the server keeps the note until
 * the purge, so taking it back is a restore rather than a rewrite. Tasks are refreshed too, since a task set from
 * the note names it only while it exists.
 */
export function useNoteCardActions(note: PersonNote, routes: NoteRoutes, refresh: () => void): NoteCardActions {
  const toast = useToast();
  const queryClient = useQueryClient();
  const refreshAll = () => {
    refresh();
    void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
  };
  const fail = (error: unknown) => toast.error(messageFor(error));
  const pinning = useMutation({
    mutationFn: () => routes.pin(note.id, !note.pinned),
    onSuccess: refresh,
    onError: fail,
  });
  const revising = useMutation({
    mutationFn: (body: string) => routes.revise(note, body),
    onSuccess: refresh,
    onError: fail,
  });
  // Undo runs after this card has left the list, so it is a plain call rather than this card's own mutation.
  const restore = () =>
    routes.restore(note.id).then(() => {
      toast("Note restored");
      refreshAll();
    }, fail);
  const removing = useMutation({
    mutationFn: () => routes.remove(note.id),
    onSuccess: () => {
      toast.success("Note deleted", { undo: () => void restore() });
      refreshAll();
    },
    onError: fail,
  });

  return {
    onPin: () => pinning.mutate(),
    onRevise: (body) => revising.mutate(body),
    onRemove: () => removing.mutate(),
    busy: pinning.isPending || revising.isPending || removing.isPending,
  };
}
