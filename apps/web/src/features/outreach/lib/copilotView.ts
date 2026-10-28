import { useCallback, useSyncExternalStore } from "react";

export type CopilotView = "live" | "plan" | "transcript";

export interface CopilotViewState {
  view: CopilotView;
}

const DEFAULT: CopilotViewState = { view: "live" };
const CHANNEL = "uncava-copilot-view";

/**
 * What the consultant chose to look at on one call, shared by every copilot this browser draws — the drawer's tab, the
 * floating window (same page, so the same map) and the popup (another page, so a broadcast) — so moving between them
 * lands on the same view. A popup opens knowing nothing, so it asks.
 */
const states = new Map<string, CopilotViewState>();
const listeners = new Set<() => void>();
let channel: BroadcastChannel | null | undefined;

/** A state to take, or, without one, a question for whoever holds this call's. */
interface ViewMessage {
  recordingId: string;
  state?: CopilotViewState;
}

function channelOrNull(): BroadcastChannel | null {
  if (channel === undefined) {
    channel = typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(CHANNEL);
    channel?.addEventListener("message", (event: MessageEvent<ViewMessage>) => {
      const { recordingId, state } = event.data;
      if (state) store(recordingId, state);
      else if (states.has(recordingId)) channel?.postMessage({ recordingId, state: states.get(recordingId) });
    });
  }
  return channel;
}

function store(recordingId: string, state: CopilotViewState): void {
  states.set(recordingId, state);
  listeners.forEach((listener) => listener());
}

export function useCopilotView(recordingId: string): [CopilotViewState, (change: Partial<CopilotViewState>) => void] {
  const subscribe = useCallback(
    (listener: () => void) => {
      if (!states.has(recordingId)) channelOrNull()?.postMessage({ recordingId });
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    [recordingId],
  );
  const state = useSyncExternalStore(subscribe, () => states.get(recordingId) ?? DEFAULT);
  const update = useCallback(
    (change: Partial<CopilotViewState>) => {
      const next = { ...(states.get(recordingId) ?? DEFAULT), ...change };
      store(recordingId, next);
      channelOrNull()?.postMessage({ recordingId, state: next });
    },
    [recordingId],
  );
  return [state, update];
}

/** Forgets every call's view; for tests, which share this module. */
export function clearCopilotViews(): void {
  states.clear();
}
