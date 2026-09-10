import { useEffect, useRef, useState } from "react";
import { streamEvents } from "../../../lib/apiClient";

/** Failures back off exponentially to this; a healthy stream's cyclic close reconnects at once. */
const MAX_RETRY_MS = 30_000;

/**
 * How long a burst of changes is allowed to keep collapsing into one refetch.
 *
 * <p>A spreadsheet import commits a row at a time and announces each one, so a thousand-row file
 * would otherwise be a thousand refetches at every open tab. Lossless to coalesce: the events carry
 * no content, so the only thing a caller ever does with them is refetch.
 */
const COALESCE_MS = 500;

/** What a `change` frame names, so a caller can refetch the half that moved. */
export type ProjectStreamKind =
  | "candidate-captured"
  | "candidate-enriched"
  | "company-captured"
  | "company-enriched";

const EVERY_KIND: ProjectStreamKind[] = [
  "candidate-captured",
  "candidate-enriched",
  "company-captured",
  "company-enriched",
];

/**
 * Holds the mandate's live stream open while the tab is visible, calling `onChange` with the kinds
 * that arrived whenever the server says something under the project moved. A frame carries its kind
 * and no content, so the handler still refetches through the ordinary guarded reads.
 *
 * A hidden tab holds no stream (it would pin a Cloud Run request slot to a screen nobody is
 * watching); coming back fires one catch-up `onChange` naming every kind, because whatever happened
 * meanwhile was missed by design.
 *
 * <p>Answers whether the stream is currently live, so a caller can poll only while it is not. That
 * stays true across the server's ordinary 55s close, which reconnects at once — it turns false only
 * when an attempt ends without the server having been heard from and the retry starts backing off.
 */
export function useProjectStream(
  projectId: string,
  onChange: (kinds: ProjectStreamKind[]) => void,
): boolean {
  // The latest handler without re-running the effect: the callers pass a fresh closure per render,
  // and tearing the stream down on every render would be a reconnect per keystroke.
  const handleChange = useRef(onChange);
  handleChange.current = onChange;
  const [isLive, setLive] = useState(false);

  useEffect(() => {
    let disposed = false;
    let failures = 0;
    let retryTimer: number | undefined;
    let coalesceTimer: number | undefined;
    let controller: AbortController | null = null;

    // Trailing rather than leading: the refetch that matters is the one after the burst stops, and a
    // leading call would read the grid halfway through an import and then never correct it.
    let pendingKinds = new Set<ProjectStreamKind>();
    const announceChange = (kinds: ProjectStreamKind[]) => {
      kinds.forEach((kind) => pendingKinds.add(kind));
      window.clearTimeout(coalesceTimer);
      coalesceTimer = window.setTimeout(() => {
        const announced = [...pendingKinds];
        pendingKinds = new Set();
        if (!disposed) {
          handleChange.current(announced);
        }
      }, COALESCE_MS);
    };

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
          setLive(true);
          if (event.name === "change") {
            announceChange(kindsOf(event.data));
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
          setLive(false);
          failures += 1;
          schedule(Math.min(MAX_RETRY_MS, 1_000 * 2 ** failures));
        },
        () => {
          if (disposed || controller?.signal.aborted) {
            return;
          }
          setLive(false);
          failures += 1;
          schedule(Math.min(MAX_RETRY_MS, 1_000 * 2 ** failures));
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
      handleChange.current(EVERY_KIND);
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
  }, [projectId]);

  return isLive;
}

/**
 * The kinds a frame names. A payload this build does not recognise answers with all of them: a client
 * that half-understands the stream must fall back to refetching too much, never to showing stale rows.
 */
function kindsOf(payload: string): ProjectStreamKind[] {
  try {
    const kind = (JSON.parse(payload) as { kind?: string }).kind;
    return EVERY_KIND.includes(kind as ProjectStreamKind) ? [kind as ProjectStreamKind] : EVERY_KIND;
  } catch {
    return EVERY_KIND;
  }
}
