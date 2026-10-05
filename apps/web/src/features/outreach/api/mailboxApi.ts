import { request } from "../../../lib/apiClient";

/**
 * The caller's own mailbox for outreach. Every route reads the caller's own row, so nothing here
 * takes an id: a consultant can only ever see or change their own connection.
 */

export const MAILBOX_KEY = ["outreach", "mailbox"] as const;

export type MailboxStatus = "ACTIVE" | "ERROR";

export interface ConnectedMailbox {
  address: string;
  /** Null until the owner's first Start that uses `{{bookingLink}}` makes one. */
  bookingLink?: string | null;
  /** The mail service's name for the host, e.g. `google`, `microsoft`. */
  provider: string;
  status: MailboxStatus;
  dailyCap: number;
  /** The IANA zone the sending hours and the daily cap are read in. */
  timeZone: string;
  connectedAt: string;
  /** A reconnect now would move this mailbox off Nylas onto Uncava's own connection. */
  movesOffNylas: boolean;
  /** The running sequences that reconnect would stop. */
  runsStoppedByMove: number;
}

export interface Mailbox {
  /** False where the deployment has no mail service: nothing can be connected. */
  offered: boolean;
  providers: string[];
  connection: ConnectedMailbox | null;
  /** Whether sequences may use `{{bookingLink}}`: a direct mailbox always may; a Nylas one where its plan carries Scheduler. */
  bookingLinkOffered: boolean;
}

interface MailboxConnectStart {
  authorizationUrl: string;
}

export function getMailbox(signal?: AbortSignal): Promise<Mailbox> {
  return request<Mailbox>("/outreach/mailbox", { signal });
}

/** Also sets the cookie that ties the consent screen's answer to this browser. */
export function startMailboxConnect(provider: string): Promise<MailboxConnectStart> {
  return request<MailboxConnectStart>("/outreach/mailbox/connect", { method: "POST", body: { provider } });
}

/** Answers the mailbox as it now reads. */
export function changeMailboxTimeZone(timeZone: string): Promise<Mailbox> {
  return request<Mailbox>("/outreach/mailbox/time-zone", { method: "PUT", body: { timeZone } });
}

export function disconnectMailbox(): Promise<void> {
  return request<void>("/outreach/mailbox", { method: "DELETE" });
}

export function sendMailboxTest(): Promise<void> {
  return request<void>("/outreach/mailbox/test", { method: "POST" });
}
