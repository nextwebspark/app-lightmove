import { useSyncExternalStore } from "react";

/** The zone an Automatic clock moved from and to, held until its person keeps or undoes it. */
export interface TimeZoneMove {
  from: string;
  to: string;
}

// Kept in the browser, not read off /me: the next /me already carries the new zone, and the move would vanish unseen.
const STORAGE_KEY = "lm-time-zone-move";

let pending: TimeZoneMove | null = read();
const listeners = new Set<() => void>();

export function recordTimeZoneMove(move: TimeZoneMove): void {
  pending = move;
  write(move);
  listeners.forEach((listener) => listener());
}

export function clearTimeZoneMove(): void {
  pending = null;
  write(null);
  listeners.forEach((listener) => listener());
}

export function useTimeZoneMove(): TimeZoneMove | null {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}

/** For a test: forget what this module read at import. */
export function reloadTimeZoneMove(): void {
  pending = read();
}

function snapshot(): TimeZoneMove | null {
  return pending;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function read(): TimeZoneMove | null {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
    if (parsed && typeof parsed === "object" && "from" in parsed && "to" in parsed) {
      const { from, to } = parsed as Record<string, unknown>;
      if (typeof from === "string" && typeof to === "string") return { from, to };
    }
  } catch {
    // An unreadable or blocked store holds no move.
  }
  return null;
}

function write(move: TimeZoneMove | null): void {
  try {
    if (move) localStorage.setItem(STORAGE_KEY, JSON.stringify(move));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // A private window keeps no move; the notice is then this tab's only.
  }
}
