import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import type { AssistantThread } from "../api/types";
import { findLateAnswer } from "./lateAnswer";

const getThread = vi.hoisted(() => vi.fn());
vi.mock("../api/assistantApi", () => ({ getThread }));

const AWAITED = { question: "And in Qatar?", threadId: "th1", askedIn: "th1", turnsBefore: 2 };

function thread(turns: number): AssistantThread {
  return { id: "th1", title: "Chat", projectId: "p1", turns: Array.from({ length: turns }) } as unknown as AssistantThread;
}

describe("looking for an answer that outlived its stream", () => {
  beforeEach(() => getThread.mockReset());

  it("finds the answer once its chat has one more turn", async () => {
    getThread.mockResolvedValueOnce(thread(2));
    expect(await findLateAnswer(AWAITED)).toBeNull();

    getThread.mockResolvedValueOnce(thread(3));
    expect(await findLateAnswer(AWAITED)).toMatchObject({ id: "th1" });
    expect(getThread).toHaveBeenCalledWith("th1");
  });

  it("reads a chat that no longer exists as an answer that will not come", async () => {
    getThread.mockRejectedValueOnce(new ApiRequestError({
      code: "NOT_FOUND", detail: "", status: 404, correlationId: "none",
    }));

    expect(await findLateAnswer(AWAITED)).toBe("gone");
  });

  it("keeps waiting through a read that failed for any other reason", async () => {
    getThread.mockRejectedValueOnce(new TypeError("network"));

    expect(await findLateAnswer(AWAITED)).toBeNull();
  });
});
