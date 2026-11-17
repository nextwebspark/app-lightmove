import { beforeEach, describe, expect, it } from "vitest";
import {
  forgetPendingContacts,
  PENDING_EXPIRY_MS,
  readPending,
  withPending,
  writePending,
  type PendingContact,
} from "./pendingContactStore";

const card = (id: string, patch: Partial<PendingContact> = {}): PendingContact => ({
  id,
  workspaceId: "w1",
  channel: "call",
  projectId: "p1",
  candidateId: "c1",
  personName: "Fatima Al Mazrouei",
  positionTitle: "Group CFO",
  number: "050 441 8836",
  whatsappHref: null,
  at: "2026-11-16T09:30:00.000Z",
  ...patch,
});

describe("the pending contacts store", () => {
  beforeEach(() => window.sessionStorage.clear());

  it("puts the newest first, and a second press of the same number only freshens its card", () => {
    const first = withPending([], card("a"));
    const other = withPending(first, card("b", { candidateId: "c2" }));
    const again = withPending(other, card("c", { at: "2026-11-16T09:40:00.000Z" }));

    expect(again.map((held) => held.id)).toEqual(["c", "b"]);
    expect(withPending(again, card("d", { channel: "whatsapp" })).map((held) => held.id)).toEqual(["d", "c", "b"]);
  });

  it("survives a reload, and lets a card go after twelve hours", () => {
    writePending([card("a"), card("b", { at: "2026-11-15T20:00:00.000Z" })]);
    const now = Date.parse("2026-11-16T09:30:00.000Z") + 60_000;

    expect(readPending("w1", now).map((held) => held.id)).toEqual(["a"]);
    expect(readPending("w1", Date.parse("2026-11-16T09:30:00.000Z") + PENDING_EXPIRY_MS)).toEqual([]);
  });

  it("reads only the workspace it is asked for, so a switch leaves the other workspace's cards unread", () => {
    writePending([card("a"), card("b", { workspaceId: "w2" })]);
    const now = Date.parse("2026-11-16T09:31:00.000Z");

    expect(readPending("w1", now).map((held) => held.id)).toEqual(["a"]);
    expect(readPending("w2", now).map((held) => held.id)).toEqual(["b"]);
  });

  it("reads nothing from a broken or foreign value, nor a chat link that is not WhatsApp's, and forgets on sign-out", () => {
    window.sessionStorage.setItem("lightmove.pendingContacts", "{not json");
    expect(readPending("w1")).toEqual([]);
    window.sessionStorage.setItem(
      "lightmove.pendingContacts",
      JSON.stringify([{ id: 1 }, card("a"), card("b", { whatsappHref: "javascript:alert(1)" })]),
    );
    expect(readPending("w1", Date.parse("2026-11-16T09:31:00.000Z")).map((held) => held.id)).toEqual(["a"]);

    forgetPendingContacts();
    expect(window.sessionStorage.getItem("lightmove.pendingContacts")).toBeNull();
  });
});
