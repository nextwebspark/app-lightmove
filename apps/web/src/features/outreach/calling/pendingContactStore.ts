/**
 * What a Call, WhatsApp or Copy on a phone left waiting to be logged, in this tab only (#1337): nothing reaches the
 * server until an outcome is tapped. Held in `sessionStorage` so it survives a page change and a reload, never another
 * tab or another member; every access may throw (a private window, blocked storage) and then the tray simply forgets.
 */
export type PendingChannel = "call" | "whatsapp" | "copy";

export interface PendingContact {
  id: string;
  channel: PendingChannel;
  projectId: string;
  candidateId: string;
  personName: string;
  positionTitle: string | null;
  /** The number as the record spells it. */
  number: string;
  /** The chat to reopen from a WhatsApp card. */
  whatsappHref: string | null;
  /** When the number was pressed: the log's time. */
  at: string;
  /** Logged as Wrong number already; the card stays only to offer Fix number. */
  loggedWrongNumber?: boolean;
}

const STORAGE_KEY = "uncava.pendingContacts";
export const PENDING_EXPIRY_MS = 12 * 60 * 60 * 1000;

export function readPending(now: number = Date.now()): PendingContact[] {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter(isPendingContact).filter((pending) => isFresh(pending, now)) : [];
  } catch {
    return [];
  }
}

/** On sign-out: the next person at this browser never sees what the last one left to log. */
export function forgetPendingContacts(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing held, or nothing reachable.
  }
}

export function writePending(pending: readonly PendingContact[]): void {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(pending));
  } catch {
    // Storage refused: the cards live as long as the page does.
  }
}

/** The newest first; pressing the same number again on the same person only freshens its card. */
export function withPending(pending: readonly PendingContact[], added: PendingContact): PendingContact[] {
  const kept = pending.filter(
    (held) => !(held.candidateId === added.candidateId && held.number === added.number && held.channel === added.channel),
  );
  return [added, ...kept];
}

function isFresh(pending: PendingContact, now: number): boolean {
  const at = Date.parse(pending.at);
  return !Number.isNaN(at) && now - at < PENDING_EXPIRY_MS;
}

function isPendingContact(value: unknown): value is PendingContact {
  if (typeof value !== "object" || value === null) return false;
  const held = value as Record<string, unknown>;
  return (
    typeof held.id === "string" &&
    (held.channel === "call" || held.channel === "whatsapp" || held.channel === "copy") &&
    typeof held.projectId === "string" &&
    typeof held.candidateId === "string" &&
    typeof held.personName === "string" &&
    typeof held.number === "string" &&
    typeof held.at === "string"
  );
}
