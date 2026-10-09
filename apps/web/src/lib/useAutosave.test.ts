import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AUTOSAVE_RETRY_DELAYS_MS, AutosaveFailedError, useAutosave } from "./useAutosave";

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
    const { result } = renderHook(() => useAutosave(save, { delayMs: 5 }));

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
    const { result } = renderHook(() => useAutosave(save, { delayMs: 5 }));

    act(() => result.current.schedule(1));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(1));

    let flushed = false;
    const flushing = act(async () => {
      await result.current.flush();
      flushed = true;
    });

    // The payload was already sent, but the request is still open — flush must not resolve yet.
    await new Promise((r) => setTimeout(r, 10));
    expect(flushed).toBe(false);

    await act(async () => resolve(0));
    await waitFor(() => expect(flushed).toBe(true));
    await flushing;
  });

  describe("when the server refuses a save", () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    /** Refuses the first `refusals` saves, then accepts. */
    function refusingSave(refusals: number) {
      let calls = 0;
      return vi.fn(async (_payload: string) => {
        calls += 1;
        if (calls <= refusals) throw new Error("503");
      });
    }

    it("keeps the edit, reads error and reports the refusal once", async () => {
      vi.useFakeTimers();
      const save = refusingSave(Infinity);
      const onError = vi.fn();
      const { result } = renderHook(() => useAutosave(save, { delayMs: 5, onError }));

      act(() => result.current.schedule("typed"));
      await act(() => vi.advanceTimersByTimeAsync(5));

      expect(result.current.status).toBe("error");
      expect(result.current.hasUnsavedChanges).toBe(true);
      expect(onError).toHaveBeenCalledTimes(1);

      // Every automatic resend carries the same edit, and none of them reports again.
      for (const delay of AUTOSAVE_RETRY_DELAYS_MS) {
        await act(() => vi.advanceTimersByTimeAsync(delay));
      }
      expect(save.mock.calls.map((call) => call[0])).toEqual(["typed", "typed", "typed", "typed"]);
      expect(onError).toHaveBeenCalledTimes(1);

      // Past the last delay it waits for a person.
      await act(() => vi.advanceTimersByTimeAsync(60_000));
      expect(save).toHaveBeenCalledTimes(4);
      expect(result.current.status).toBe("error");
    });

    it("resends on its own and reads saved once a resend lands", async () => {
      vi.useFakeTimers();
      const save = refusingSave(1);
      const { result } = renderHook(() => useAutosave(save, { delayMs: 5 }));

      act(() => result.current.schedule("typed"));
      await act(() => vi.advanceTimersByTimeAsync(5));
      expect(result.current.status).toBe("error");

      await act(() => vi.advanceTimersByTimeAsync(AUTOSAVE_RETRY_DELAYS_MS[0]));
      expect(save).toHaveBeenLastCalledWith("typed");
      expect(result.current.status).toBe("saved");
      expect(result.current.hasUnsavedChanges).toBe(false);
    });

    it("flush rejects while the edit is refused, so no caller can report it saved", async () => {
      const save = refusingSave(Infinity);
      const { result } = renderHook(() => useAutosave(save, { delayMs: 60_000 }));

      act(() => result.current.schedule("typed"));
      await act(async () => {
        await expect(result.current.flush()).rejects.toBeInstanceOf(AutosaveFailedError);
      });
      expect(result.current.status).toBe("error");
    });

    it("retry resends the latest edit and reports a fresh refusal", async () => {
      const save = refusingSave(2);
      const onError = vi.fn();
      const { result } = renderHook(() => useAutosave(save, { delayMs: 60_000, onError }));

      act(() => result.current.schedule("first"));
      await act(async () => {
        await result.current.flush().catch(() => {});
      });
      act(() => result.current.schedule("second"));
      await act(async () => {
        await result.current.flush().catch(() => {});
      });
      expect(onError).toHaveBeenCalledTimes(1);

      await act(async () => {
        await result.current.retry();
      });
      expect(save.mock.calls.map((call) => call[0])).toEqual(["first", "second", "second"]);
      expect(result.current.status).toBe("saved");
    });

    it("a newer edit queued during the refused save wins over the refused one", async () => {
      let refuse: (error: Error) => void = () => {};
      const save = vi
        .fn<(payload: string) => Promise<void>>()
        .mockImplementationOnce(() => new Promise((_resolve, reject) => (refuse = reject)))
        .mockResolvedValue(undefined);
      const { result } = renderHook(() => useAutosave(save, { delayMs: 60_000 }));

      act(() => result.current.schedule("older"));
      let first: Promise<void> = Promise.resolve();
      act(() => {
        first = result.current.flush();
      });
      act(() => result.current.schedule("newer"));
      await act(async () => {
        refuse(new Error("503"));
        await first.catch(() => {});
        await result.current.retry();
      });
      expect(save.mock.calls.map((call) => call[0])).toEqual(["older", "newer"]);
    });

    it("stops resending once the screen is gone", async () => {
      vi.useFakeTimers();
      const save = refusingSave(Infinity);
      const { result, unmount } = renderHook(() => useAutosave(save, { delayMs: 5 }));

      act(() => result.current.schedule("typed"));
      await act(() => vi.advanceTimersByTimeAsync(5));
      unmount();
      await act(() => vi.advanceTimersByTimeAsync(60_000));

      // The unmount's own last attempt, and nothing after it.
      expect(save).toHaveBeenCalledTimes(2);
    });
  });
});
