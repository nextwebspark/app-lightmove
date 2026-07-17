import { useCallback, useEffect, useRef, useState } from "react";

export type SaveStatus = "idle" | "saving" | "saved";

/**
 * Debounced autosave: call `schedule(payload)` on every edit; the latest payload is flushed after
 * the delay, on unmount, and immediately via `flush()`. Concurrent schedules collapse to one save —
 * the screen has no Save button, so this is the only write path for its section.
 */
export function useAutosave<T>(save: (payload: T) => Promise<unknown>, delayMs = 700) {
  const [status, setStatus] = useState<SaveStatus>("idle");
  const pending = useRef<T | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const saveRef = useRef(save);
  saveRef.current = save;

  const flush = useCallback(async () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    const payload = pending.current;
    if (payload === null) return;
    pending.current = null;
    setStatus("saving");
    try {
      await saveRef.current(payload);
      // A newer edit may have arrived while this one was in flight — stay dirty, not "saved".
      setStatus(pending.current === null ? "saved" : "saving");
    } catch {
      setStatus("idle"); // the mutation's onError owns the toast
    }
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
