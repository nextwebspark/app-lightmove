import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as personCrmApi from "../../candidates/api/personCrmApi";
import type { NoteOutcome, PersonNoteKind } from "../../candidates/api/types";
import { loggedToastOf } from "../../candidates/lib/noteOutcomes";
import { invalidateNoteReadsOf } from "../../candidates/lib/invalidateNoteReads";
import { usePendingContacts } from "./pendingContactsContext";
import type { PendingContact } from "./pendingContactStore";

export function kindOfCard(card: PendingContact): PersonNoteKind {
  return card.channel === "whatsapp" ? "whatsapp" : "call";
}

/**
 * A card's one-tap log: the note, with its outcome and the press time and no words, filed on the position the number
 * was pressed from. Wrong number keeps the card to offer Fix number; anything else settles it.
 */
export function useLogPendingContact(card: PendingContact) {
  const tray = usePendingContacts();
  const queryClient = useQueryClient();
  const toast = useToast();
  const kind = kindOfCard(card);
  return useMutation({
    mutationFn: (outcome: NoteOutcome) =>
      personCrmApi.writePersonNote(card.projectId, card.candidateId, {
        kind,
        body: "",
        format: "markdown",
        occurredAt: card.at,
        outcome,
      }),
    onSuccess: (_note, outcome) => {
      invalidateNoteReadsOf(queryClient, { projectId: card.projectId, candidateId: card.candidateId });
      if (outcome === "wrong_number") {
        toast(`${loggedToastOf(kind)} · Wrong number`);
        tray?.markWrongNumber(card.id);
        return;
      }
      toast(loggedToastOf(kind));
      tray?.settle(card.id);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
}
