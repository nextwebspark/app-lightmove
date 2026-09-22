import type { Receipts } from "./documentFill";

/**
 * Keeps a document reading's receipts for the tab.
 *
 * <p>What a reading leaves behind — the confidence, the snippet, the Undo, the strip's count and the
 * rail's `N filled` badge — is session state that nothing persists server-side: only the `source`
 * stamp reaches the database. Held in component state alone, all of it was lost on reload, so the
 * same sparkle answered with a snippet and an Undo one moment and a bare line the next. That read as
 * a broken popover rather than as a deliberate forgetting.
 *
 * <p>`sessionStorage`, not `localStorage`: a reading belongs to the sitting somebody is in, it holds
 * quoted lines of a client's position description, and a closed tab should take it with it. Every
 * access is guarded — a private window, blocked site data or a full quota all throw rather than
 * answering — and a failure is never worth surfacing, since the brief itself is unaffected.
 *
 * <p>Keyed by project and stamped with the attached document's name, so a receipt is read back only
 * against the document it was read from: replace the file and the stored receipt is a description of
 * a document nobody is looking at any more.
 */

const KEY_PREFIX = "lightmove.position.receipts.";

interface StoredReceipts {
  /** The document these receipts were read from — a different one makes them stale, not wrong. */
  fileName: string;
  receipts: Receipts;
}

const keyOf = (projectId: string) => `${KEY_PREFIX}${projectId}`;

export function loadReceipts(projectId: string, fileName: string | undefined): Receipts {
  if (!fileName) return {};
  try {
    const raw = sessionStorage.getItem(keyOf(projectId));
    if (!raw) return {};
    const stored = JSON.parse(raw) as StoredReceipts;
    if (stored?.fileName !== fileName || typeof stored.receipts !== "object") return {};
    return stored.receipts ?? {};
  } catch {
    return {};
  }
}

export function saveReceipts(projectId: string, fileName: string | undefined, receipts: Receipts): void {
  if (!fileName) return clearReceipts(projectId);
  try {
    // An empty map is cleared rather than written: "the strip was dismissed" and "nothing was ever
    // read" are the same screen, and leaving the row behind only invites it back on the next reload.
    if (Object.keys(receipts).length === 0) return clearReceipts(projectId);
    sessionStorage.setItem(keyOf(projectId), JSON.stringify({ fileName, receipts } satisfies StoredReceipts));
  } catch {
    // Storage refused or is full — the reading still filled the brief, which is what mattered.
  }
}

export function clearReceipts(projectId: string): void {
  try {
    sessionStorage.removeItem(keyOf(projectId));
  } catch {
    // As above.
  }
}
