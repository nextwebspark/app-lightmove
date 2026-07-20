import { useCallback, useEffect, useRef, useState } from "react";

export type SaveStatus = "idle" | "saving" | "saved";

/**
 * Debounced autosave: call `schedule(payload)` on every edit; the latest payload is flushed after
 * the delay, on unmount, and immediately via `flush()`. Concurrent schedules collapse to one save —
 * the screens that use it have no Save button, so this is the only write path for their section.
 */
export function useAutosave<T>(save: (payload: T) => Promise<unknown>, delayMs = 700) {
  const [status, setStatus] = useState<SaveStatus>("idle");
  const pending = useRef<T | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inFlight = useRef<Promise<unknown> | null>(null);
  const saveRef = useRef(save);
  saveRef.current = save;

  const flush = useCallback(async (): Promise<void> => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }

    // A save is already in flight. Firing another now would put two concurrent writes on the same row,
    // and the second commit would lose the optimistic-lock race (StaleObjectStateException → 409). So
    // wait for it to settle, then send whatever is pending by then — one request at a time. An explicit
    // flush() (e.g. the lock flow's Promise.all) therefore also awaits the real in-flight request, not
    // just the queued payload.
    if (inFlight.current) {
      try {
        await inFlight.current;
      } catch {
        // Its own turn already reported the failure; fall through to send anything queued since.
      }
      return flush();
    }

    const payload = pending.current;
    if (payload === null) return;
    pending.current = null;
    setStatus("saving");

    const save = saveRef.current(payload);
    inFlight.current = save;
    try {
      await save;
    } catch {
      setStatus("idle"); // the mutation's onError owns the toast
      inFlight.current = null;
      return;
    }
    inFlight.current = null;

    // A newer edit landed while this was in flight — send it before reporting "saved".
    if (pending.current !== null) return flush();
    setStatus("saved");
  }, []);

  const schedule = useCallback(
    (payload: T) => {
      pending.current = payload;
      setStatus("saving");
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => void flush(), delayMs);
    },
    [flush, delayMs],
  );

  // Flush on unmount so navigating away never drops the last keystrokes.
  useEffect(() => () => void flush(), [flush]);

  return { schedule, flush, status };
}
