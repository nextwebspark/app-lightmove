import { useCallback, useEffect, useRef, useState } from "react";

export type SaveStatus = "idle" | "saving" | "saved" | "error";

/** The waits before each automatic resend of a refused save; past the last, only `retry()` resends. */
export const AUTOSAVE_RETRY_DELAYS_MS = [2_000, 5_000, 15_000] as const;

/** What `flush()` rejects with when the edit could not be written: the edit is still held, unsent. */
export class AutosaveFailedError extends Error {
  constructor(cause: unknown) {
    super("The last change could not be saved.", { cause });
    this.name = "AutosaveFailedError";
  }
}

export interface AutosaveOptions {
  delayMs?: number;
  /**
   * Told once when saving starts failing, not on every automatic resend: a refused save is resent on
   * its own, and a toast per attempt would bury the screen. Told again after a success or a `retry()`.
   */
  onError?: (error: unknown) => void;
}

/**
 * Debounced autosave: call `schedule(payload)` on every edit; the latest payload is flushed after
 * the delay, on unmount, and immediately via `flush()`. Concurrent schedules collapse to one save —
 * the screens that use it have no Save button, so this is the only write path for their section.
 *
 * <p>A refused save keeps its payload and reads `"error"` until a save lands: it is resent after
 * each of {@link AUTOSAVE_RETRY_DELAYS_MS}, and `flush()` rejects rather than letting a caller act as
 * if the edit were stored — a "Draft saved" over a dropped edit is what lost people's typing.
 */
export function useAutosave<T>(save: (payload: T) => Promise<unknown>, options: AutosaveOptions = {}) {
  const { delayMs = 700 } = options;
  const [status, setStatus] = useState<SaveStatus>("idle");
  const pending = useRef<T | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inFlight = useRef<Promise<unknown> | null>(null);
  const failures = useRef(0);
  const reported = useRef(false);
  const unmounted = useRef(false);
  const saveRef = useRef(save);
  saveRef.current = save;
  const onErrorRef = useRef(options.onError);
  onErrorRef.current = options.onError;

  const clearTimer = () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  };

  const flush = useCallback(async (): Promise<void> => {
    clearTimer();

    // A save is already in flight. Waiting on it, rather than sending the queued payload alongside,
    // keeps two PUTs of the same row from racing each other into an optimistic-lock 409; if it is
    // refused, its payload is back in `pending` and goes out again below.
    if (inFlight.current) {
      try {
        await inFlight.current;
      } catch {
        // Its own turn already recorded the failure; fall through to send what it left behind.
      }
      return flush();
    }

    const payload = pending.current;
    if (payload === null) return;
    pending.current = null;
    setStatus("saving");

    const request = saveRef.current(payload);
    inFlight.current = request;
    try {
      await request;
    } catch (error) {
      inFlight.current = null;
      // A newer edit queued while this was in flight supersedes it: each payload is the whole section.
      pending.current ??= payload;
      failures.current += 1;
      setStatus("error");
      if (!reported.current) {
        reported.current = true;
        onErrorRef.current?.(error);
      }
      const delay = AUTOSAVE_RETRY_DELAYS_MS[failures.current - 1];
      // Never resent once the screen is gone: a remount reads the server afresh, and a late resend
      // of this older payload would overwrite whatever is typed there.
      if (delay !== undefined && !unmounted.current) {
        clearTimer();
        timer.current = setTimeout(() => void flush().catch(() => {}), delay);
      }
      throw new AutosaveFailedError(error);
    }
    inFlight.current = null;
    failures.current = 0;
    reported.current = false;

    // A newer edit landed while this was in flight — send it before reporting "saved".
    if (pending.current !== null) return flush();
    setStatus("saved");
  }, []);

  const schedule = useCallback(
    (payload: T) => {
      pending.current = payload;
      failures.current = 0;
      setStatus("saving");
      clearTimer();
      timer.current = setTimeout(() => void flush().catch(() => {}), delayMs);
    },
    [flush, delayMs],
  );

  /** Sends now without waiting on the outcome; a refusal still reads "error" and reaches `onError`. */
  const saveNow = useCallback(() => void flush().catch(() => {}), [flush]);

  /** Resends the held edit now, as a person pressing Retry means it: a fresh round of resends, and a fresh report. */
  const retry = useCallback((): Promise<void> => {
    failures.current = 0;
    reported.current = false;
    return flush();
  }, [flush]);

  // Flush on unmount so navigating away never drops the last keystrokes; a failure there was
  // already reported, and nobody is left on the screen to resend it.
  useEffect(() => {
    unmounted.current = false;
    return () => {
      unmounted.current = true;
      void flush().catch(() => {});
    };
  }, [flush]);

  const hasUnsavedChanges = status === "saving" || status === "error";
  return { schedule, flush, saveNow, retry, status, hasUnsavedChanges };
}
