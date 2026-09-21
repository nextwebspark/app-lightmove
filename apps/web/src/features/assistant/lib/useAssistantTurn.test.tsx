import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAssistantTurn } from "./useAssistantTurn";

const streamEvents = vi.hoisted(() => vi.fn());
vi.mock("../../../lib/apiClient", () => ({ streamEvents }));

type Emit = (name: string, data: string) => void;

/** One connection the test drives: it hands back the emitter and the path that was asked for. */
function connection() {
  const opened: string[] = [];
  let emit: Emit = () => {};
  let close: () => void = () => {};

  streamEvents.mockImplementation(
    (path: string, onEvent: (event: { name: string; data: string }) => void) => {
      opened.push(path);
      emit = (name, data) => onEvent({ name, data });
      return new Promise<void>((resolve) => {
        close = resolve;
      });
    },
  );

  return {
    opened,
    frame: (seq: number, kind: string, payload: Record<string, unknown> = {}) =>
      emit("assistant", JSON.stringify({ seq, kind, payload, occurredAt: "2026-01-01T00:00:00Z" })),
    close: () => close(),
  };
}

describe("useAssistantTurn", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => {
    vi.useRealTimers();
    streamEvents.mockReset();
  });

  it("builds the answer out of the deltas", async () => {
    const stream = connection();
    const { result } = renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "turn.started", { question: "q" });
    stream.frame(2, "message.delta", { text: "Saudi " });
    stream.frame(3, "message.delta", { text: "Electricity" });

    await waitFor(() => expect(result.current.answer).toBe("Saudi Electricity"));
  });

  it("shows a tool as running until its result arrives", async () => {
    const stream = connection();
    const { result } = renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "tool.called", { tool: "searchCompanyUniverse", arguments: "{}" });
    await waitFor(() => expect(result.current.steps).toHaveLength(1));
    expect(result.current.steps[0]).toMatchObject({
      label: "Searching the company universe",
      running: true,
    });

    stream.frame(2, "tool.result", { tool: "searchCompanyUniverse", result: "{}" });

    await waitFor(() => expect(result.current.steps[0].running).toBe(false));
  });

  it("reconnects at the cursor and ignores what it has already drawn", async () => {
    const stream = connection();
    const { result } = renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "message.delta", { text: "half " });
    await waitFor(() => expect(result.current.answer).toBe("half "));
    stream.close();

    // The server's ordinary ~55s close. It replays from the cursor, and a frame at or below it has
    // already been drawn — the server repeats a frame rather than losing one, by design.
    await waitFor(() => expect(stream.opened).toHaveLength(2));
    expect(stream.opened[1]).toContain("afterSeq=1");
    stream.frame(1, "message.delta", { text: "half " });
    stream.frame(2, "message.delta", { text: "an answer" });

    await waitFor(() => expect(result.current.answer).toBe("half an answer"));
  });

  it("stops reconnecting once the turn has finished", async () => {
    const stream = connection();
    renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "turn.finished", { status: "SUCCEEDED" });
    await waitFor(() => expect(stream.opened).toHaveLength(1));
    stream.close();

    await vi.advanceTimersByTimeAsync(5_000);

    expect(stream.opened).toHaveLength(1);
  });

  it("carries the settled answer and the failure code off the terminal frame", async () => {
    const stream = connection();
    const { result } = renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "tool.called", { tool: "searchCompanyUniverse" });
    stream.frame(2, "turn.finished", { status: "FAILED", code: "ASSISTANT_TURN_STRANDED" });

    await waitFor(() => expect(result.current.status).toBe("FAILED"));
    // The server names it `code`; reading `errorCode` would have swallowed every failure silently.
    expect(result.current.errorCode).toBe("ASSISTANT_TURN_STRANDED");
    // A turn that ends mid-tool must not leave a spinner running for ever.
    expect(result.current.steps[0].running).toBe(false);
  });

  it("prefers the settled answer over the deltas it reassembled", async () => {
    const stream = connection();
    const { result } = renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "message.delta", { text: "partial" });
    stream.frame(2, "answer", { text: "the whole answer" });

    await waitFor(() => expect(result.current.answer).toBe("the whole answer"));
  });

  it("ignores a kind this build does not know", async () => {
    const stream = connection();
    const { result } = renderHook(() => useAssistantTurn("t1"));

    stream.frame(1, "message.delta", { text: "kept" });
    stream.frame(2, "something.newer", { text: "not rendered raw" });

    await waitFor(() => expect(result.current.answer).toBe("kept"));
  });

  it("opens nothing at all without a turn", () => {
    connection();
    renderHook(() => useAssistantTurn(null));

    expect(streamEvents).not.toHaveBeenCalled();
  });
});
