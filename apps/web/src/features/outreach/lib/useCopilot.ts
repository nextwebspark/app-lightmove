import { useCallback, useEffect, useRef, useState } from "react";
import { streamEvents } from "../../../lib/apiClient";
import { codeOf } from "../../../lib/errorCodes";
import type { Copilot, CopilotLine } from "../api/notetakerApi";
import * as notetakerApi from "../api/notetakerApi";

/** Failures back off exponentially to this; a healthy stream's cyclic close reconnects at once. */
const MAX_RETRY_MS = 30_000;
/** A line, a reading and a status change land together; one read answers them all. */
const COALESCE_MS = 300;
/** While the stream is down the window still reads, just less often. */
const POLL_MS = 15_000;
/** Codes after which reading again can only fail the same way: the window says why and stops. */
const FINAL_CODES = new Set(["FORBIDDEN", "NOT_FOUND", "PROJECT_ARCHIVED", "NOTETAKER_COPILOT_OFF"]);

/**
 * One live call's copilot as its window holds it: the server's latest reading, and every finished line read so far,
 * each read asking only for the lines after the last one held. The stream says when to read; it names no content.
 *
 * <p>Unlike the project stream, a hidden page keeps its stream: the consultant is looking at the call, not this tab,
 * and the copilot window floats over it.
 */
export function useCopilot(projectId: string, candidateId: string, recordingId: string) {
  const [copilot, setCopilot] = useState<Copilot | null>(null);
  const [lines, setLines] = useState<CopilotLine[]>([]);
  const [isLive, setLive] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const cursor = useRef(0);
  const reading = useRef<Promise<void> | null>(null);
  const readAgain = useRef(false);
  const stopped = useRef(false);

  const read = useCallback(async (): Promise<void> => {
    if (stopped.current) return;
    if (reading.current) {
      readAgain.current = true;
      return reading.current;
    }
    const run = (async () => {
      try {
        const next = await notetakerApi.getCopilot(projectId, candidateId, recordingId, cursor.current);
        cursor.current = Math.max(cursor.current, next.lastSeq);
        setLines((held) => {
          const last = held.at(-1)?.seq ?? 0;
          const fresh = next.lines.filter((line) => line.seq > last);
          return fresh.length === 0 ? held : [...held, ...fresh];
        });
        setCopilot(next);
        setError(null);
      } catch (failed) {
        setError(failed);
        if (FINAL_CODES.has(codeOf(failed) ?? "")) stopped.current = true;
      }
    })();
    reading.current = run;
    await run;
    reading.current = null;
    if (readAgain.current) {
      readAgain.current = false;
      await read();
    }
  }, [projectId, candidateId, recordingId]);

  useEffect(() => {
    let disposed = false;
    let failures = 0;
    let retryTimer: number | undefined;
    let coalesceTimer: number | undefined;
    let controller: AbortController | null = null;
    stopped.current = false;
    cursor.current = 0;
    setLines([]);

    const announceChange = () => {
      window.clearTimeout(coalesceTimer);
      coalesceTimer = window.setTimeout(() => {
        if (!disposed) void read();
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
      if (disposed || stopped.current) return;
      controller?.abort();
      const attempt = new AbortController();
      controller = attempt;
      let heardTheServer = false;
      streamEvents(
        `${notetakerApi.copilotPath(projectId, candidateId, recordingId)}/stream`,
        (event) => {
          heardTheServer = true;
          failures = 0;
          setLive(true);
          if (event.name === "change" || event.name === "connected") announceChange();
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

    void read();
    connect();
    const poll = window.setInterval(() => {
      if (!disposed) void read();
    }, POLL_MS);

    return () => {
      disposed = true;
      setLive(false);
      window.clearTimeout(retryTimer);
      window.clearTimeout(coalesceTimer);
      window.clearInterval(poll);
      controller?.abort();
    };
  }, [projectId, candidateId, recordingId, read]);

  return { copilot, lines, isLive, error, read, setCopilot };
}
