import type { ConnectedMailbox, Mailbox } from "../api/mailboxApi";

/**
 * Where the consultant's own mailbox stands, as the Outreach page's banner, chip and composer all read it. `ready`
 * and `readScopeMissing` can send; the rest say what to do before anything goes.
 */
export type MailboxState =
  | { kind: "loading" }
  | { kind: "refused" }
  | { kind: "unavailable" }
  | { kind: "none"; providers: readonly string[] }
  | { kind: "error"; connection: ConnectedMailbox }
  | { kind: "movesOffNylas"; connection: ConnectedMailbox }
  | { kind: "readScopeMissing"; connection: ConnectedMailbox }
  | { kind: "ready"; connection: ConnectedMailbox };

export function mailboxStateOf(mailbox: { data?: Mailbox; isError: boolean; isPending: boolean }): MailboxState {
  if (mailbox.isError) return { kind: "refused" };
  if (mailbox.isPending || !mailbox.data) return { kind: "loading" };
  const { offered, providers, connection } = mailbox.data;
  if (!offered) return { kind: "unavailable" };
  if (!connection) return { kind: "none", providers };
  if (connection.status === "ERROR") return { kind: "error", connection };
  if (connection.movesOffNylas) return { kind: "movesOffNylas", connection };
  if (connection.reconnectToReadReplies) return { kind: "readScopeMissing", connection };
  return { kind: "ready", connection };
}

/** Whether an email can be written from it now; a Nylas mailbox due to move still sends until it does. */
export function canSendFrom(state: MailboxState): boolean {
  return state.kind === "ready" || state.kind === "readScopeMissing" || state.kind === "movesOffNylas";
}

/** The mail service's host names, as people know them. A host this list lacks reads as its own name. */
const PROVIDER_LABELS: Record<string, string> = { google: "Gmail", microsoft: "Outlook" };

export function providerLabel(provider: string): string {
  return PROVIDER_LABELS[provider] ?? provider.charAt(0).toUpperCase() + provider.slice(1);
}
