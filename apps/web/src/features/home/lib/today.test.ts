import { describe, expect, it } from "vitest";
import type { TaskResponse } from "../../tasks/api/types";
import type { MyMeeting } from "../api/homeApi";
import { isOverAt, lengthOf, todayEventsOf } from "./today";

const meeting = (id: string, startsAt: string, personId = "x1"): MyMeeting => ({
  id,
  title: "Screening",
  startsAt,
  endsAt: new Date(new Date(startsAt).getTime() + 45 * 60_000).toISOString(),
  joinUrl: "https://meet.example/1",
  videoProvider: "Google Meet",
  personId,
  personName: "Karim Nassar",
  projectId: "p1",
  candidateId: "c1",
  positionTitle: "Buying Director",
  clientName: "IKEA UAE",
});

const callTask = (id: string, dueAt: string, personId = "x1"): TaskResponse => ({
  id,
  projectId: "p2",
  projectTitle: "Leasing Director",
  candidateId: "c2",
  personId,
  personName: "Mona El-Sayed",
  title: "Call Mona",
  kind: "call",
  detail: null,
  presetId: null,
  note: null,
  assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
  createdBy: null,
  dueAt,
  status: "open",
  source: "manual",
  rule: null,
  completedAt: null,
  completedBy: null,
  createdAt: "2026-11-20T09:00:00Z",
  editable: true,
});

describe("todayEventsOf", () => {
  it("merges meetings and call tasks soonest first, a task's client read from its position", () => {
    const events = todayEventsOf(
      [meeting("m1", "2026-11-23T11:00:00.000Z")],
      [callTask("t1", "2026-11-23T09:30:00.000Z", "x2")],
      (projectId) => (projectId === "p2" ? "Al-Futtaim" : undefined),
      () => "Call",
    );
    expect(events.map((event) => [event.key, event.clientName, event.minutes])).toEqual([
      ["t:t1", "Al-Futtaim", null],
      ["m:m1", "IKEA UAE", 45],
    ]);
  });

  it("draws a call task for the same person at the meeting's time once, as the meeting", () => {
    const events = todayEventsOf(
      [meeting("m1", "2026-11-23T11:00:00.000Z")],
      [callTask("t1", "2026-11-23T11:00:00.000Z")],
      () => undefined,
      () => "Call",
    );
    expect(events.map((event) => event.key)).toEqual(["m:m1"]);
  });
});

describe("lengthOf", () => {
  it("says a call's length in minutes and hours", () => {
    expect([lengthOf(30), lengthOf(60), lengthOf(90)]).toEqual(["30 min", "1 h", "1 h 30 min"]);
  });
});

describe("isOverAt", () => {
  it("keeps a meeting under way current until it ends, and a call task past once its time has come", () => {
    const [underWay] = todayEventsOf([meeting("m1", "2026-11-23T10:00:00.000Z")], [], () => undefined, () => "Call");
    expect(isOverAt(underWay, new Date("2026-11-23T10:05:00Z"))).toBe(false);
    expect(isOverAt(underWay, new Date("2026-11-23T10:45:00Z"))).toBe(true);
    const [call] = todayEventsOf([], [callTask("t1", "2026-11-23T10:00:00.000Z")], () => undefined, () => "Call");
    expect(isOverAt(call, new Date("2026-11-23T10:01:00Z"))).toBe(true);
  });
});
