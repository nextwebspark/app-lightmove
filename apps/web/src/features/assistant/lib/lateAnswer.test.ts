import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AssistantThread } from "../api/types";
import { chatTitleOf, findLateAnswer } from "./lateAnswer";

const getThread = vi.hoisted(() => vi.fn());
const listThreads = vi.hoisted(() => vi.fn());
vi.mock("../api/assistantApi", () => ({ getThread, listThreads }));

const ASKED_AT = Date.parse("2026-10-13T10:00:00Z");

function thread(id: string, turns: number): AssistantThread {
  return { id, title: "Chat", projectId: "p1", turns: Array.from({ length: turns }) } as unknown as AssistantThread;
}

describe("looking for an answer that outlived its stream", () => {
  beforeEach(() => {
    getThread.mockReset();
    listThreads.mockReset();
  });

  it("titles a chat as the server does: one line, eighty characters", () => {
    expect(chatTitleOf("  Top retailers\n in  the UAE ")).toBe("Top retailers in the UAE");
    expect(chatTitleOf("x".repeat(120))).toHaveLength(80);
  });

  it("finds the answer in its chat once the chat has one more turn", async () => {
    const awaited = { question: "And in Qatar?", threadId: "th1", turnsBefore: 2, askedAt: ASKED_AT };
    getThread.mockResolvedValueOnce(thread("th1", 2));
    expect(await findLateAnswer(awaited, "p1")).toBeNull();

    getThread.mockResolvedValueOnce(thread("th1", 3));
    expect((await findLateAnswer(awaited, "p1"))?.turns).toHaveLength(3);
  });

  it("finds a new chat by the question that founded it, never an older chat of the same title", async () => {
    const awaited = { question: "Top retailers in the UAE", threadId: null, turnsBefore: 0, askedAt: ASKED_AT };
    listThreads.mockResolvedValueOnce([
      { id: "old", title: "Top retailers in the UAE", updatedAt: "2026-10-12T09:00:00Z" },
    ]);
    expect(await findLateAnswer(awaited, "p1")).toBeNull();

    listThreads.mockResolvedValueOnce([
      { id: "new", title: "Top retailers in the UAE", updatedAt: "2026-10-13T10:02:00Z" },
      { id: "old", title: "Top retailers in the UAE", updatedAt: "2026-10-12T09:00:00Z" },
    ]);
    getThread.mockResolvedValueOnce(thread("new", 1));
    expect((await findLateAnswer(awaited, "p1"))?.id).toBe("new");
    expect(getThread).toHaveBeenCalledWith("new");
  });
});
