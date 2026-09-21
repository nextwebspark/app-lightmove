import { useEffect, useRef, useState } from "react";
import { streamEvents } from "../../../lib/apiClient";
import type { AssistantFrame, AssistantTurnStatus } from "../api/types";

/** Failures back off exponentially to this; the server's ordinary cyclic close reconnects at once. */
const MAX_RETRY_MS = 15_000;

/**
 * Below this, an attempt that received nothing is a connection that never worked.
 *
 * <p>{@code useProjectStream} tells a healthy cyclic close from a proxy answering 200-and-hangup by
 * the `connected` greeting every project stream opens with. **The assistant stream has no greeting**
 * — registering sets the cursor and the first drain *is* the replay, so a reconnect to a turn that
 * has said nothing new correctly sends nothing at all. Duration is what is left: the server holds a
 * stream ~55s, so an attempt that returned in under a second without a frame did not work.
 */
const HEALTHY_MS = 1_000;

/** One step of the turn's trace, as the panel draws it. */
export type TurnStep = {
  seq: number;
  label: string;
  running: boolean;
};

export type TurnProgress = {
  answer: string;
  steps: TurnStep[];
  status: AssistantTurnStatus;
  errorCode: string | null;
};

const IDLE: TurnProgress = { answer: "", steps: [], status: "RUNNING", errorCode: null };

/**
 * Renders one turn as it happens, and resumes correctly across the server's ~55s close, a tab going
 * background, and a refresh mid-turn.
 *
 * <p>The cursor is the whole resume story: every frame carries its `seq`, a reconnect asks for
 * `?afterSeq=N`, and the server replays from there. A frame at or below the cursor is dropped rather
 * than re-applied, because a replay after a partial send is the case the server is explicitly built
 * to produce — it advances its own cursor only after a successful write, so repeating a frame is the
 * failure mode it chose over losing one.
 */
export function useAssistantTurn(turnId: string | null): TurnProgress {
  const [progress, setProgress] = useState<TurnProgress>(IDLE);
  // Survives reconnects within one turn, so a resumed stream does not re-apply what it already drew.
  const cursor = useRef(0);

  useEffect(() => {
    cursor.current = 0;
    setProgress(IDLE);
    if (!turnId) return;

    let disposed = false;
    let failures = 0;
    let retryTimer: number | undefined;
    let controller: AbortController | null = null;
    let finished = false;

    const schedule = (delayMs: number) => {
      window.clearTimeout(retryTimer);
      retryTimer = window.setTimeout(connect, delayMs);
    };

    const connect = () => {
      if (disposed || finished || document.visibilityState === "hidden") return;

      controller = new AbortController();
      const openedAt = Date.now();
      let heardAnything = false;

      streamEvents(
        `/assistant/turns/${turnId}/stream?afterSeq=${cursor.current}`,
        (event) => {
          if (event.name !== "assistant") return;
          heardAnything = true;
          failures = 0;
          const frame = parseFrame(event.data);
          if (!frame || frame.seq <= cursor.current) return;
          cursor.current = frame.seq;
          if (frame.kind === "turn.finished") finished = true;
          setProgress((current) => applyFrame(current, frame));
        },
        controller.signal,
      ).then(settled, settled);

      function settled() {
        if (disposed || finished || controller?.signal.aborted) return;
        if (heardAnything || Date.now() - openedAt >= HEALTHY_MS) {
          schedule(0);
          return;
        }
        failures += 1;
        schedule(Math.min(MAX_RETRY_MS, 1_000 * 2 ** failures));
      }
    };

    // A hidden tab holds no stream — it would pin a Cloud Run request slot to a panel nobody is
    // watching. Coming back reconnects at the cursor, so nothing is missed rather than caught up.
    const handleVisibility = () => {
      if (document.visibilityState === "hidden") {
        window.clearTimeout(retryTimer);
        controller?.abort();
        return;
      }
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
  }, [turnId]);

  return progress;
}

function parseFrame(data: string): AssistantFrame | null {
  try {
    const frame = JSON.parse(data) as AssistantFrame;
    return typeof frame?.seq === "number" && typeof frame?.kind === "string" ? frame : null;
  } catch {
    return null;
  }
}

/**
 * A kind this build does not know is ignored, never rendered raw. The server's `kind` is an open
 * vocabulary by design, so an instance running older code meets kinds from a newer one — and showing
 * a consultant `proposal.accepted` as prose would be worse than showing nothing.
 */
function applyFrame(current: TurnProgress, frame: AssistantFrame): TurnProgress {
  switch (frame.kind) {
    case "message.delta":
      return { ...current, answer: current.answer + text(frame.payload.text) };
    case "answer":
      // The settled answer in full, so a replay does not have to reassemble the deltas.
      return { ...current, answer: text(frame.payload.text) || current.answer };
    case "tool.called":
      return { ...current, steps: [...current.steps, startedStep(frame)] };
    case "tool.result":
      return { ...current, steps: finishStep(current.steps, text(frame.payload.tool)) };
    case "turn.finished":
      return {
        ...current,
        steps: current.steps.map((step) => ({ ...step, running: false })),
        status: (text(frame.payload.status) || "SUCCEEDED") as AssistantTurnStatus,
        // The server names it `code`, not `errorCode` — see AssistantTurnStore.fail.
        errorCode: text(frame.payload.code) || null,
      };
    default:
      return current;
  }
}

function startedStep(frame: AssistantFrame): TurnStep {
  return { seq: frame.seq, label: labelFor(text(frame.payload.tool)), running: true };
}

/** The most recent running step for that tool: one turn can call the same tool more than once. */
function finishStep(steps: TurnStep[], tool: string): TurnStep[] {
  const label = labelFor(tool);
  const index = steps.map((step) => step.running && step.label === label).lastIndexOf(true);
  if (index < 0) return steps;
  return steps.map((step, at) => (at === index ? { ...step, running: false } : step));
}

/**
 * What a step says while it runs. A 60s turn is tolerable only if it says what it is doing — and it
 * is also how somebody notices the assistant is about to do something they did not want.
 */
const TOOL_LABELS: Record<string, string> = {
  describeMarket: "Reading what the universe holds",
  searchCompanyUniverse: "Searching the company universe",
  searchMandateFilter: "Searching this mandate's own filter",
  listMandateCompanies: "Reading the companies this mandate has filed",
  listMandateExecutives: "Reading the executives this mandate has mapped",
  mandateCompensation: "Reading what the brief says the role pays",
  proposeCompanies: "Putting companies forward",
};

function labelFor(tool: string): string {
  return TOOL_LABELS[tool] ?? "Looking something up";
}

function text(value: unknown): string {
  return typeof value === "string" ? value : "";
}
