import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { POOL_NOTES_KEY } from "../api/poolApi";
import { PERSON_NOTES_KEY, PERSON_TIMELINE_KEY } from "../api/personCrmApi";
import { invalidateNoteReadsOf } from "./invalidateNoteReads";

describe("invalidateNoteReadsOf", () => {
  it("marks the Talent pool's notes and every position's notes stale after a log on one position", () => {
    const queryClient = new QueryClient();
    const held = [
      POOL_NOTES_KEY("person-1"),
      PERSON_NOTES_KEY("p1", "c1"),
      PERSON_TIMELINE_KEY("p1", "c1"),
      PERSON_NOTES_KEY("p2", "c9"),
    ];
    const untouched = ["candidates", "p1", "page"];
    for (const key of [...held, untouched]) queryClient.setQueryData(key, []);

    invalidateNoteReadsOf(queryClient, { projectId: "p1", candidateId: "c1" });

    for (const key of held) expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(untouched)?.isInvalidated).toBe(false);
  });
});
