import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { CANDIDATE_OUTREACH_KEY } from "../api/runApi";
import { CONVERSATIONS_KEY_PREFIX } from "../api/conversationApi";
import { MEETING_SLOTS_KEY } from "../api/meetingApi";
import { refreshOutreachActivity } from "./outreachRefresh";

function cacheWith(...keys: (readonly unknown[])[]) {
  const queryClient = new QueryClient();
  keys.forEach((key) => queryClient.setQueryData(key, {}));
  return queryClient;
}

const isInvalidated = (queryClient: QueryClient, key: readonly unknown[]) =>
  queryClient.getQueryState(key)?.isInvalidated ?? false;

describe("refreshOutreachActivity", () => {
  it("refreshes the position's outreach reads but never a call's free slots", () => {
    const slots = MEETING_SLOTS_KEY("p1", "c1", 30, "2026-10-21");
    const queryClient = cacheWith(CONVERSATIONS_KEY_PREFIX("p1"), CANDIDATE_OUTREACH_KEY("p1", "c1"), slots,
      CONVERSATIONS_KEY_PREFIX("p2"));

    refreshOutreachActivity(queryClient, "p1");

    expect(isInvalidated(queryClient, CONVERSATIONS_KEY_PREFIX("p1"))).toBe(true);
    expect(isInvalidated(queryClient, CANDIDATE_OUTREACH_KEY("p1", "c1"))).toBe(true);
    expect(isInvalidated(queryClient, slots)).toBe(false);
    expect(isInvalidated(queryClient, CONVERSATIONS_KEY_PREFIX("p2"))).toBe(false);
  });

  it("without a position, refreshes every position's", () => {
    const queryClient = cacheWith(CONVERSATIONS_KEY_PREFIX("p2"));

    refreshOutreachActivity(queryClient);

    expect(isInvalidated(queryClient, CONVERSATIONS_KEY_PREFIX("p2"))).toBe(true);
  });
});
