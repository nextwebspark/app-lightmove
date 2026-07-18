import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useAutosave } from "./useAutosave";

/** A save whose completion the test controls, tracking how many run at once. */
function deferredSave() {
  let active = 0;
  let maxActive = 0;
  const resolvers: Array<() => void> = [];
  const save = vi.fn((_payload: number) =>
    new Promise<void>((resolve) => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      resolvers.push(() => {
        active -= 1;
        resolve();
      });
    }),
  );
  return { save, resolve: (i: number) => resolvers[i](), peakConcurrency: () => maxActive };
}

describe("useAutosave", () => {
  it("never runs two saves concurrently — a save in flight defers the next", async () => {
    const { save, resolve, peakConcurrency } = deferredSave();
    const { result } = renderHook(() => useAutosave(save, 5));

    act(() => result.current.schedule(1));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(1));

    // Queue a second edit while the first request is still open.
    act(() => result.current.schedule(2));
    await new Promise((r) => setTimeout(r, 20)); // past the debounce
    expect(save).toHaveBeenCalledTimes(1); // still one — the second is held behind the in-flight save

    await act(async () => resolve(0)); // first settles → second is sent
    await waitFor(() => expect(save).toHaveBeenCalledTimes(2));
    await act(async () => resolve(1));

    await waitFor(() => expect(result.current.status).toBe("saved"));
    expect(peakConcurrency()).toBe(1);
    expect(save.mock.calls.map((c) => c[0])).toEqual([1, 2]);
  });

  it("an explicit flush awaits the in-flight save, not just the queued payload", async () => {
    const { save, resolve } = deferredSave();
    const { result } = renderHook(() => useAutosave(save, 5));

    act(() => result.current.schedule(1));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(1));

    let flushed = false;
    void act(async () => {
      await result.current.flush();
      flushed = true;
    });

    // The payload was already sent, but the request is still open — flush must not resolve yet.
    await new Promise((r) => setTimeout(r, 10));
    expect(flushed).toBe(false);

    await act(async () => resolve(0));
    await waitFor(() => expect(flushed).toBe(true));
  });
});
