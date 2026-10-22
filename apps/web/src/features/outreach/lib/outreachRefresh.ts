import type { QueryClient } from "@tanstack/react-query";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import { NOTETAKER_KEY_PREFIX } from "../api/notetakerApi";

/**
 * Refetches what a pushed outreach or task change can move: every open outreach read but a call's free slots, which
 * ask the mail provider and would shift under the consultant's cursor in Book a call. Without a position, every one's.
 */
export function refreshOutreachActivity(queryClient: QueryClient, projectId?: string): void {
  void queryClient.invalidateQueries({
    queryKey: projectId ? ["outreach", projectId] : ["outreach"],
    predicate: (query) => !query.queryKey.includes("slots"),
  });
}

/**
 * A bot moved on one of the position's calls: its chips, and the notes a filed call adds to (a Meeting note) and the
 * person's timeline.
 */
export function refreshNotetaker(queryClient: QueryClient, projectId: string): void {
  void queryClient.invalidateQueries({ queryKey: NOTETAKER_KEY_PREFIX(projectId) });
  void queryClient.invalidateQueries({ queryKey: [...CANDIDATES_KEY_PREFIX(projectId), "notes"] });
  void queryClient.invalidateQueries({ queryKey: [...CANDIDATES_KEY_PREFIX(projectId), "timeline"] });
}
