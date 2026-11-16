import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { CONVERSATION_KEY, CONVERSATIONS_KEY, invalidateEveryConversation } from "./conversationApi";

describe("invalidateEveryConversation", () => {
  it("refreshes the person's conversation on every position, since Last call reads them all, and leaves the queues", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(CONVERSATION_KEY("p1", "c1"), {});
    queryClient.setQueryData(CONVERSATION_KEY("p2", "c7"), {});
    queryClient.setQueryData(CONVERSATIONS_KEY("p1", "all", "team", ""), {});

    invalidateEveryConversation(queryClient);

    const isInvalidated = (key: readonly unknown[]) => queryClient.getQueryState(key)?.isInvalidated;
    expect(isInvalidated(CONVERSATION_KEY("p1", "c1"))).toBe(true);
    expect(isInvalidated(CONVERSATION_KEY("p2", "c7"))).toBe(true);
    expect(isInvalidated(CONVERSATIONS_KEY("p1", "all", "team", ""))).toBe(false);
  });
});
