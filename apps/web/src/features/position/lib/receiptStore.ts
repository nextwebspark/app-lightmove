import type { Receipts } from "./documentFill";

/**
 * Keeps a document reading's receipts (confidence, snippet, Undo, counts) for the tab; only `source` reaches the server.
 * `sessionStorage` because it quotes a client's description and should die with the tab; every access is guarded and
 * failures ignored. Keyed by project and stamped with the document's name, so a replaced file's receipts are not read.
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
