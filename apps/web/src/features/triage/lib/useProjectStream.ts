import { useEffect, useRef } from "react";
import { streamEvents } from "../../../lib/apiClient";

/** Failures back off exponentially to this; a healthy stream's cyclic close reconnects at once. */
const MAX_RETRY_MS = 30_000;

/**
 * Holds the mandate's live stream open while the tab is visible, calling `onChange` whenever the
 * server says something under the project moved. The stream carries no content — the handler
 * refetches through the ordinary guarded reads — so there is nothing here to keep consistent.
 *
 * A hidden tab holds no stream (it would pin a Cloud Run request slot to a screen nobody is
 * watching); coming back fires one catch-up `onChange`, because whatever happened meanwhile was
 * missed by design.
 */
export function useProjectStream(projectId: string, onChange: () => void): void {
  // The latest handler without re-running the effect: the callers pass a fresh closure per render,
  // and tearing the stream down on every render would be a reconnect per keystroke.
  const handleChange = useRef(onChange);
  handleChange.current = onChange;

  useEffect(() => {
    let disposed = false;
    let failures = 0;
    let retryTimer: number | undefined;
    let controller: AbortController | null = null;

    const schedule = (delayMs: number) => {
      window.clearTimeout(retryTimer);
      retryTimer = window.setTimeout(connect, delayMs);
    };

    const connect = () => {
      if (disposed || document.visibilityState === "hidden") {
        return;
      }
      controller = new AbortController();
      // The server greets every stream with a `connected` event, so a healthy connection always
      // hears something — which is what separates its ordinary cyclic close (reconnect at once)
      // from a proxy answering 200 and hanging up (back off, or this would be a request storm).
      let heardTheServer = false;
      streamEvents(
        `/projects/${projectId}/stream`,
        (event) => {
          heardTheServer = true;
          failures = 0;
          if (event.name === "change") {
            handleChange.current();
          }
        },
        controller.signal,
      ).then(
        () => {
          if (disposed) {
            return;
          }
          if (heardTheServer) {
            schedule(0);
            return;
          }
          failures += 1;
          schedule(Math.min(MAX_RETRY_MS, 1_000 * 2 ** failures));
        },
        () => {
          if (disposed || controller?.signal.aborted) {
            return;
          }
          failures += 1;
          schedule(Math.min(MAX_RETRY_MS, 1_000 * 2 ** failures));
        },
      );
    };

    const handleVisibility = () => {
      if (document.visibilityState === "hidden") {
        window.clearTimeout(retryTimer);
        controller?.abort();
        return;
      }
      handleChange.current();
      connect();
    };

    document.addEventListener("visibilitychange", handleVisibility);
    connect();

    return () => {
      disposed = true;
      window.clearTimeout(retryTimer);
      controller?.abort();
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [projectId]);
}
