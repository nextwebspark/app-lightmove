import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { streamEvents } from "../../../lib/apiClient";
import type { SseEvent } from "../../../lib/sse";
import { useProjectStream } from "./useProjectStream";

vi.mock("../../../lib/apiClient", () => ({ streamEvents: vi.fn() }));

const streamEventsMock = vi.mocked(streamEvents);

interface OpenStream {
  emit: (event: SseEvent) => void;
  end: () => void;
  fail: (error: Error) => void;
  signal: AbortSignal;
}

let openStreams: OpenStream[] = [];

beforeEach(() => {
  vi.useFakeTimers();
  openStreams = [];
  streamEventsMock.mockReset();
  streamEventsMock.mockImplementation(
    (_path, onEvent, signal) =>
      new Promise<void>((resolve, reject) => {
        openStreams.push({ emit: onEvent, end: resolve, fail: reject, signal });
      }),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

it("refetches when the server announces a change, and only then", async () => {
  const onChange = vi.fn();
  renderHook(() => useProjectStream("p1", onChange));

  act(() => {
    openStreams[0].emit({ name: "connected", data: "{}" });
    openStreams[0].emit({ name: "change", data: '{"kind":"candidate-enriched"}' });
  });
  await act(() => vi.advanceTimersByTimeAsync(500));

  expect(onChange).toHaveBeenCalledTimes(1);
});

it("collapses a burst of changes into one refetch", async () => {
  const onChange = vi.fn();
  renderHook(() => useProjectStream("p1", onChange));

  // What a spreadsheet import looks like from here: it commits a row at a time and announces each,
  // so without the coalesce a thousand-row file is a thousand refetches on every open tab.
  act(() => {
    for (let row = 0; row < 50; row += 1) {
      openStreams[0].emit({ name: "change", data: '{"kind":"company-captured"}' });
    }
  });
  await act(() => vi.advanceTimersByTimeAsync(500));

  expect(onChange).toHaveBeenCalledTimes(1);
});

it("reconnects immediately after the server's cyclic close", async () => {
  renderHook(() => useProjectStream("p1", vi.fn()));

  act(() => {
    openStreams[0].emit({ name: "connected", data: "{}" });
    openStreams[0].end();
  });
  await act(() => vi.advanceTimersByTimeAsync(0));

  expect(streamEventsMock).toHaveBeenCalledTimes(2);
});

it("backs off when the stream closes without the server ever speaking", async () => {
  renderHook(() => useProjectStream("p1", vi.fn()));

  act(() => openStreams[0].end());
  await act(() => vi.advanceTimersByTimeAsync(0));
  expect(streamEventsMock).toHaveBeenCalledTimes(1);

  await act(() => vi.advanceTimersByTimeAsync(2_000));
  expect(streamEventsMock).toHaveBeenCalledTimes(2);
});

it("backs off on failure and lets go entirely on unmount", async () => {
  const { unmount } = renderHook(() => useProjectStream("p1", vi.fn()));

  act(() => openStreams[0].fail(new Error("network down")));
  await act(() => vi.advanceTimersByTimeAsync(2_000));
  expect(streamEventsMock).toHaveBeenCalledTimes(2);

  unmount();
  expect(openStreams[1].signal.aborted).toBe(true);

  await act(() => vi.advanceTimersByTimeAsync(60_000));
  expect(streamEventsMock).toHaveBeenCalledTimes(2);
});
