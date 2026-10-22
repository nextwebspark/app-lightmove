import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { streamEvents } from "../../../lib/apiClient";
import { refreshOutreachActivity } from "../../outreach/lib/outreachRefresh";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import { NOTIFICATIONS_KEY_PREFIX } from "../api/notificationsApi";

/** Failures back off exponentially to this; a healthy stream's cyclic close reconnects at once. */
const MAX_RETRY_MS = 30_000;

/** A reply webhook and the task it files arrive together; one refetch answers both. */
const COALESCE_MS = 500;

/**
 * Holds the caller's own notification stream open while the tab is visible, and refreshes the bell and
 * every task read whenever the server says something reached them. A frame names no content, so the
 * refresh goes through the ordinary reads.
 *
 * <p>The useProjectStream idiom: a hidden tab holds no stream, and coming back refreshes once, because
 * whatever happened meanwhile was missed by design. Answers whether the stream is live, so the caller
 * can poll only while it is not.
 */
export function useNotificationStream(): boolean {
  const queryClient = useQueryClient();
  const [isLive, setLive] = useState(false);

  useEffect(() => {
    let disposed = false;
    let failures = 0;
    let retryTimer: number | undefined;
    let coalesceTimer: number | undefined;
    let controller: AbortController | null = null;

    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY_PREFIX });
      void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
      // A reply or a booking reaches the bell and the conversation together; only open reads refetch.
      refreshOutreachActivity(queryClient);
    };

    const announceChange = () => {
      window.clearTimeout(coalesceTimer);
      coalesceTimer = window.setTimeout(() => {
        if (!disposed) refresh();
      }, COALESCE_MS);
    };

    const schedule = (delayMs: number) => {
      window.clearTimeout(retryTimer);
      retryTimer = window.setTimeout(connect, delayMs);
    };

    const backOff = () => {
      setLive(false);
      failures += 1;
      schedule(Math.min(MAX_RETRY_MS, 1_000 * 2 ** failures));
    };

    const connect = () => {
      if (disposed || document.visibilityState === "hidden") return;
      controller?.abort();
      const attempt = new AbortController();
      controller = attempt;
      // The server greets every stream with `connected`: a stream that ends unheard is a proxy hanging up,
      // which backs off, where the server's own cyclic close reconnects at once.
      let heardTheServer = false;
      streamEvents(
        "/me/notifications/stream",
        (event) => {
          heardTheServer = true;
          failures = 0;
          setLive(true);
          if (event.name === "change") announceChange();
        },
        attempt.signal,
      ).then(
        () => {
          if (disposed || attempt.signal.aborted) return;
          if (heardTheServer) schedule(0);
          else backOff();
        },
        () => {
          if (disposed || attempt.signal.aborted) return;
          backOff();
        },
      );
    };

    const handleVisibility = () => {
      if (document.visibilityState === "hidden") {
        window.clearTimeout(retryTimer);
        controller?.abort();
        setLive(false);
        return;
      }
      refresh();
      connect();
    };

    document.addEventListener("visibilitychange", handleVisibility);
    connect();

    return () => {
      disposed = true;
      setLive(false);
      window.clearTimeout(retryTimer);
      window.clearTimeout(coalesceTimer);
      controller?.abort();
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [queryClient]);

  return isLive;
}
