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

    expect(readPending(now).map((held) => held.id)).toEqual(["a"]);
    expect(readPending(Date.parse("2026-11-16T09:30:00.000Z") + PENDING_EXPIRY_MS)).toEqual([]);
  });

  it("reads nothing from a broken or foreign value, and forgets everything on sign-out", () => {
    window.sessionStorage.setItem("uncava.pendingContacts", "{not json");
    expect(readPending()).toEqual([]);
    window.sessionStorage.setItem("uncava.pendingContacts", JSON.stringify([{ id: 1 }, card("a")]));
    expect(readPending(Date.parse("2026-11-16T09:31:00.000Z")).map((held) => held.id)).toEqual(["a"]);

    forgetPendingContacts();
    expect(window.sessionStorage.getItem("uncava.pendingContacts")).toBeNull();
  });
});
