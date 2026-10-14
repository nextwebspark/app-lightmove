import { describe, expect, it } from "vitest";
import type { NotificationResponse } from "../api/types";
import { groupByDay, notificationHref, snoozeOptionsOf, timeLabelOf, unreadBadgeOf } from "./notificationView";

/** Wednesday 14 Oct 2026, 16:20 in the viewer's zone. */
const NOW = new Date(2026, 9, 14, 16, 20);

const at = (day: number, hour: number, minute = 0) => new Date(2026, 9, day, hour, minute).toISOString();

function aNotification(id: string, createdAt: string, overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id,
    kind: "mentioned",
    count: 1,
    actor: null,
    projectId: "p1",
    projectTitle: "CFO Search",
    personId: "pe1",
    candidateId: null,
    personName: "Ahmed Saleh",
    taskId: null,
    taskTitle: null,
    note: { id: "note-1", kind: "general", writtenAt: "2026-10-14T09:00:00Z" },
    names: [],
    dueAt: null,
    createdAt,
    read: false,
    snoozedUntil: null,
    ...overrides,
  };
}

describe("unreadBadgeOf", () => {
  it("is hidden at none, the count up to nine, and 9+ past it", () => {
    expect(unreadBadgeOf(0)).toBeNull();
    expect(unreadBadgeOf(3)).toBe("3");
    expect(unreadBadgeOf(9)).toBe("9");
    expect(unreadBadgeOf(10)).toBe("9+");
  });
});

describe("groupByDay", () => {
  it("puts rows under Today, Yesterday and then their date", () => {
    const days = groupByDay(
      [aNotification("a", at(14, 16, 5)), aNotification("b", at(14, 9)), aNotification("c", at(13, 17)), aNotification("d", at(8, 10))],
      NOW,
    );

    expect(days.map((day) => [day.label, day.items.map((item) => item.id)])).toEqual([
      ["Today", ["a", "b"]],
      ["Yesterday", ["c"]],
      ["08 Oct 2026", ["d"]],
    ]);
  });
});

describe("timeLabelOf", () => {
  it("is a time of day today and a date before", () => {
    expect(timeLabelOf(at(14, 16, 5), NOW)).toBe("16:05");
    expect(timeLabelOf(at(3, 16, 5), NOW)).toBe("03 Oct 2026");
  });
});

describe("notificationHref", () => {
  it("opens the executive's drawer on the position's pipeline when the row is about one", () => {
    expect(notificationHref(aNotification("a", at(14, 9), { candidateId: "c1", note: null }))).toBe(
      "/projects/p1/pipeline?candidate=c1",
    );
  });

  it("opens the drawer on the note a row names", () => {
    expect(notificationHref(aNotification("a", at(14, 9), { candidateId: "c1" }))).toBe(
      "/projects/p1/pipeline?candidate=c1&note=note-1",
    );
  });

  it("opens the position's pipeline otherwise, and My positions when there is no position", () => {
    expect(notificationHref(aNotification("a", at(14, 9)))).toBe("/projects/p1/pipeline");
    expect(notificationHref(aNotification("a", at(14, 9), { projectId: null }))).toBe("/");
  });
});

describe("snoozeOptionsOf", () => {
  it("offers an hour from now, tomorrow at 09:00 and next Monday at 09:00, each with its date", () => {
    expect(snoozeOptionsOf(NOW).map((option) => [option.label, option.hint])).toEqual([
      ["In 1 hour", "14 Oct 2026, 17:20"],
      ["Tomorrow", "15 Oct 2026, 09:00"],
      ["Next week", "19 Oct 2026, 09:00"],
    ]);
  });

  it("takes a Monday's next week to the Monday after", () => {
    expect(snoozeOptionsOf(new Date(2026, 9, 19, 8, 0))[2].hint).toBe("26 Oct 2026, 09:00");
  });
});
