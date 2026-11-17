import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { CONVERSATION_KEY, CONVERSATIONS_KEY, invalidateConversationsOf, type Conversation } from "./conversationApi";

const conversationOf = (personId: string) => ({ executive: { personId } }) as unknown as Conversation;

function seeded(): QueryClient {
  const queryClient = new QueryClient();
  queryClient.setQueryData(CONVERSATION_KEY("p1", "layla-on-p1"), conversationOf("layla"));
  queryClient.setQueryData(CONVERSATION_KEY("p2", "layla-on-p2"), conversationOf("layla"));
  queryClient.setQueryData(CONVERSATION_KEY("p1", "omar-on-p1"), conversationOf("omar"));
  queryClient.setQueryData(CONVERSATIONS_KEY("p1", "all", "team", ""), {});
  return queryClient;
}

const isInvalidated = (queryClient: QueryClient, key: readonly unknown[]) =>
  queryClient.getQueryState(key)?.isInvalidated;

describe("invalidateConversationsOf", () => {
  it("refreshes the person's conversation on every position, since Last call reads them all, and nobody else's", () => {
    const queryClient = seeded();

    invalidateConversationsOf(queryClient, { projectId: "p1", candidateId: "layla-on-p1" });

    expect(isInvalidated(queryClient, CONVERSATION_KEY("p1", "layla-on-p1"))).toBe(true);
    expect(isInvalidated(queryClient, CONVERSATION_KEY("p2", "layla-on-p2"))).toBe(true);
    expect(isInvalidated(queryClient, CONVERSATION_KEY("p1", "omar-on-p1"))).toBe(false);
    expect(isInvalidated(queryClient, CONVERSATIONS_KEY("p1", "all", "team", ""))).toBe(false);
  });

  it("reaches the same conversations from the person, as the workspace drawer names them", () => {
    const queryClient = seeded();

    invalidateConversationsOf(queryClient, { personId: "omar" });

    expect(isInvalidated(queryClient, CONVERSATION_KEY("p1", "omar-on-p1"))).toBe(true);
    expect(isInvalidated(queryClient, CONVERSATION_KEY("p1", "layla-on-p1"))).toBe(false);
  });

  it("refreshes every conversation when no cached one says whose row it was", () => {
    const queryClient = seeded();

    invalidateConversationsOf(queryClient, { projectId: "p3", candidateId: "unknown" });

    expect(isInvalidated(queryClient, CONVERSATION_KEY("p1", "omar-on-p1"))).toBe(true);
    expect(isInvalidated(queryClient, CONVERSATION_KEY("p2", "layla-on-p2"))).toBe(true);
  });
});
