import { describe, expect, it } from "vitest";
import type { ConnectedMailbox, Mailbox } from "../api/mailboxApi";
import { canSendFrom, mailboxStateOf, providerLabel } from "./mailboxState";

const connection: ConnectedMailbox = {
  address: "yara@firm.example",
  provider: "google",
  status: "ACTIVE",
  dailyCap: 50,
  timeZone: "Asia/Dubai",
  connectedAt: "2026-10-01T09:00:00Z",
  movesOffNylas: false,
  runsStoppedByMove: 0,
  liveSequences: 0,
  livePeople: 0,
  readsReplies: true,
  reconnectToReadReplies: false,
  sentToday: 0,
  scheduledEmails: 0,
};

function read(data: Mailbox) {
  return mailboxStateOf({ data, isError: false, isPending: false });
}

describe("mailboxStateOf", () => {
  it("reads a refused or unfinished read as neither connected nor missing", () => {
    expect(mailboxStateOf({ isError: true, isPending: false }).kind).toBe("refused");
    expect(mailboxStateOf({ isError: false, isPending: true }).kind).toBe("loading");
  });

  it("says outreach is unavailable where nothing can be connected", () => {
    expect(read({ offered: false, providers: [], connection: null, bookingLinkOffered: false }).kind).toBe("unavailable");
  });

  it("offers the hosts the deployment lists when nothing is connected", () => {
    expect(read({ offered: true, providers: ["google"], connection: null, bookingLinkOffered: false })).toEqual({
      kind: "none",
      providers: ["google"],
    });
  });

  it("puts a withdrawn mailbox before a move or a missing read, and a move before a missing read", () => {
    const base = { offered: true, providers: ["google"], bookingLinkOffered: false };
    const everything = { ...connection, status: "ERROR" as const, movesOffNylas: true, reconnectToReadReplies: true };
    expect(read({ ...base, connection: everything }).kind).toBe("error");
    expect(read({ ...base, connection: { ...everything, status: "ACTIVE" } }).kind).toBe("movesOffNylas");
    expect(read({ ...base, connection: { ...everything, status: "ACTIVE", movesOffNylas: false } }).kind).toBe(
      "readScopeMissing",
    );
    expect(read({ ...base, connection }).kind).toBe("ready");
  });

  it("sends from a mailbox that is ready, still to move, or missing only the read", () => {
    const base = { offered: true, providers: ["google"], bookingLinkOffered: false };
    expect(canSendFrom(read({ ...base, connection }))).toBe(true);
    expect(canSendFrom(read({ ...base, connection: { ...connection, movesOffNylas: true } }))).toBe(true);
    expect(canSendFrom(read({ ...base, connection: { ...connection, reconnectToReadReplies: true } }))).toBe(true);
    expect(canSendFrom(read({ ...base, connection: { ...connection, status: "ERROR" } }))).toBe(false);
    expect(canSendFrom(read({ ...base, connection: null }))).toBe(false);
  });
});

describe("providerLabel", () => {
  it("names a host as people know it, and an unknown one by itself", () => {
    expect(providerLabel("google")).toBe("Gmail");
    expect(providerLabel("microsoft")).toBe("Outlook");
    expect(providerLabel("zoho")).toBe("Zoho");
  });
});
