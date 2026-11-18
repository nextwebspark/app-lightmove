import type { QueryClient } from "@tanstack/react-query";
import { invalidateConversationsOf, type NoteSubject } from "../../outreach/api/conversationApi";
import { isAnyPositionNoteRead } from "../api/personCrmApi";
import { POOL_KEY } from "../api/poolApi";

/**
 * Every read a note write changes: each position's notes and timeline (a note about every position shows on all of
 * them), the Talent pool's notes, timeline, feed and rows, and through Last call every position's conversation.
 * A tray log once refreshed only its own position, and the Talent pool drawer kept missing the call.
 */
export function invalidateNoteReadsOf(queryClient: QueryClient, subject: NoteSubject): void {
  void queryClient.invalidateQueries({
    predicate: (query) => isAnyPositionNoteRead(query.queryKey),
  });
  for (const read of ["notes", "timeline", "activity", "page"]) {
    void queryClient.invalidateQueries({ queryKey: [...POOL_KEY, read] });
  }
  invalidateConversationsOf(queryClient, subject);
}
