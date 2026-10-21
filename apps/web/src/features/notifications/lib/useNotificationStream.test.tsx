import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { streamEvents } from "../../../lib/apiClient";
import type { SseEvent } from "../../../lib/sse";
import { useNotificationStream } from "./useNotificationStream";

vi.mock("../../../lib/apiClient", () => ({ streamEvents: vi.fn() }));

const streamEventsMock = vi.mocked(streamEvents);

interface OpenStream {
  path: string;
  emit: (event: SseEvent) => void;
  end: () => void;
}

let openStreams: OpenStream[] = [];
let queryClient: QueryClient;

const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
);

const invalidatedKeys = () =>
  vi.mocked(queryClient.invalidateQueries).mock.calls.map(([filters]) => filters?.queryKey);

beforeEach(() => {
  vi.useFakeTimers();
  openStreams = [];
  queryClient = new QueryClient();
  vi.spyOn(queryClient, "invalidateQueries");
  streamEventsMock.mockReset();
  streamEventsMock.mockImplementation(
    (path, onEvent) =>
      new Promise<void>((resolve) => {
        openStreams.push({ path, emit: onEvent, end: resolve });
      }),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

it("listens on the caller's own stream and refreshes the bell, every task read and the open outreach reads once per burst", async () => {
  renderHook(() => useNotificationStream(), { wrapper });

  act(() => {
    openStreams[0].emit({ name: "connected", data: "{}" });
    openStreams[0].emit({ name: "change", data: '{"kind":"notifications"}' });
    openStreams[0].emit({ name: "change", data: '{"kind":"notifications"}' });
  });
  await act(() => vi.advanceTimersByTimeAsync(500));

  expect(openStreams[0].path).toBe("/me/notifications/stream");
  expect(invalidatedKeys()).toEqual([["notifications"], ["tasks"], ["outreach"]]);
});

it("refreshes nothing for the greeting alone", async () => {
  renderHook(() => useNotificationStream(), { wrapper });

  act(() => openStreams[0].emit({ name: "connected", data: "{}" }));
  await act(() => vi.advanceTimersByTimeAsync(500));

  expect(queryClient.invalidateQueries).not.toHaveBeenCalled();
});

it("backs off when the stream closes unheard, and reconnects at once after the server's own close", async () => {
  renderHook(() => useNotificationStream(), { wrapper });

  act(() => openStreams[0].end());
  await act(() => vi.advanceTimersByTimeAsync(1_000));
  expect(streamEventsMock).toHaveBeenCalledTimes(1);
  await act(() => vi.advanceTimersByTimeAsync(1_000));
  expect(streamEventsMock).toHaveBeenCalledTimes(2);

  act(() => {
    openStreams[1].emit({ name: "connected", data: "{}" });
    openStreams[1].end();
  });
  await act(() => vi.advanceTimersByTimeAsync(0));
  expect(streamEventsMock).toHaveBeenCalledTimes(3);
});
