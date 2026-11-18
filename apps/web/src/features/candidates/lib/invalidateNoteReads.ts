import type { QueryClient } from "@tanstack/react-query";
import { invalidateConversationsOf, type NoteSubject } from "../../outreach/api/conversationApi";
import { POOL_KEY } from "../api/poolApi";

const NOTE_READS = new Set(["notes", "timeline"]);

/**
 * Every read a note write changes: each position's notes and timeline (a note about every position shows on all of
 * them), the Talent pool's notes, timeline, feed and rows, and through Last call every position's conversation.
 * A tray log once refreshed only its own position, and the Talent pool drawer kept missing the call.
 */
export function invalidateNoteReadsOf(queryClient: QueryClient, subject: NoteSubject): void {
  void queryClient.invalidateQueries({
    predicate: (query) => query.queryKey[0] === "candidates" && NOTE_READS.has(String(query.queryKey[2])),
  });
  for (const read of ["notes", "timeline", "activity", "page"]) {
    void queryClient.invalidateQueries({ queryKey: [...POOL_KEY, read] });
  }
  invalidateConversationsOf(queryClient, subject);
}
