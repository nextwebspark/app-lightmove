import type { IntegrationProvider } from "../api/types";

/** "6 mailboxes are connected through this app." — Zoom connects accounts, not mailboxes. */
export function connectionsLine(provider: IntegrationProvider, count: number): string {
  const noun = provider === "ZOOM" ? "Zoom account" : "mailbox";
  const plural = provider === "ZOOM" ? "Zoom accounts" : "mailboxes";
  return count === 1 ? `1 ${noun} is connected through this app.` : `${count} ${plural} are connected through this app.`;
}
