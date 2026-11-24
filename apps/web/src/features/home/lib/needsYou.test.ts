import { describe, expect, it } from "vitest";
import type { TaskResponse } from "../../tasks/api/types";
import type { MyConversationRow } from "../api/homeApi";
import { attentionOf, needsYouOf } from "./needsYou";

const person = { userId: "u1", fullName: "Yara Haddad", avatarUrl: null };

const task = (id: string, overrides: Partial<TaskResponse> = {}): TaskResponse => ({
  id,
  projectId: "p1",
  projectTitle: "Group CFO",
  candidateId: "c1",
  personId: "x1",
  personName: "Priya Raman",
  title: `Task ${id}`,
  kind: "todo",
  detail: null,
  presetId: null,
  note: null,
  assignee: person,
  createdBy: person,
  dueAt: "2026-10-14T09:00:00Z",
  status: "open",
  source: "manual",
  rule: null,
  completedAt: null,
  completedBy: null,
  createdAt: "2026-10-01T09:00:00Z",
  editable: true,
  ...overrides,
});

const conversation = (candidateId: string, group: MyConversationRow["row"]["group"], taskId?: string): MyConversationRow => ({
  projectId: "p1",
  positionTitle: "Group CFO",
  row: {
    candidateId,
    personId: `person-${candidateId}`,
    fullName: candidateId,
    title: null,
    companyName: null,
    enrichedAt: null,
    status: "contacted",
    statusLabel: "Contacted",
    statusColour: "blue",
    group,
    action: taskId
      ? { taskId, title: "Reply", kind: "follow_up", rule: "reply", dueAt: "2026-10-14T08:00:00Z", assignee: person }
      : null,
    run: null,
    latest: null,
    lastSentAt: null,
    scheduledAt: null,
    lastActivityAt: "2026-10-13T08:00:00Z",
  },
});

describe("needsYouOf", () => {
  it("puts the outreach rows first, then the caller's own tasks", () => {
    const items = needsYouOf([conversation("reply", "NEEDS_REPLY")], [task("t1")], "u1");
    expect(items.map((item) => item.key)).toEqual(["c:p1:reply", "t:t1"]);
  });

  it("never draws a task twice when a row already carries it as its next action", () => {
    const items = needsYouOf([conversation("reply", "NEEDS_REPLY", "t1")], [task("t1"), task("t2")], "u1");
    expect(items.map((item) => item.key)).toEqual(["c:p1:reply", "t:t2"]);
  });

  it("keeps a call still ahead for Today, and done tasks out", () => {
    const now = new Date("2026-10-14T08:00:00Z");
    const items = needsYouOf(
      [],
      [task("call", { kind: "call", dueAt: "2026-10-14T11:00:00Z" }), task("done", { status: "done" }), task("t1")],
      "u1",
      now,
    );
    expect(items.map((item) => item.key)).toEqual(["t:t1"]);
  });

  it("leaves a follow-up row waiting only on the caller's own call later today to Today", () => {
    const now = new Date("2026-10-14T08:00:00Z");
    const calling = conversation("caller", "PREPARATION", "t5");
    calling.row = { ...calling.row, action: { ...calling.row.action!, kind: "call", dueAt: "2026-10-14T11:00:00Z" } };
    expect(needsYouOf([calling, conversation("reply", "NEEDS_REPLY")], [], "u1", now).map((item) => item.key)).toEqual([
      "c:p1:reply",
    ]);
  });

  // A follow-up waiting on a call tomorrow, or on a teammate's call under Team, showed in neither Needs nor Today.
  it("keeps a row whose call is on another day or is a teammate's", () => {
    const now = new Date("2026-10-14T08:00:00Z");
    const tomorrow = conversation("tomorrow", "FOLLOW_UP", "t6");
    tomorrow.row = { ...tomorrow.row, action: { ...tomorrow.row.action!, kind: "call", dueAt: "2026-10-15T11:00:00Z" } };
    const teammates = conversation("teammate", "FOLLOW_UP", "t7");
    teammates.row = {
      ...teammates.row,
      action: {
        ...teammates.row.action!,
        kind: "call",
        dueAt: "2026-10-14T11:00:00Z",
        assignee: { userId: "u2", fullName: "Omar Khalil", avatarUrl: null },
      },
    };
    const omar = { userId: "u2", fullName: "Omar Khalil", avatarUrl: null };
    const theirs = task("theirs", { kind: "call", dueAt: "2026-10-14T11:00:00Z", assignee: omar });
    const items = needsYouOf([tomorrow, teammates], [theirs], "u1", now);
    expect(items.map((item) => item.key)).toEqual(["c:p1:tomorrow", "c:p1:teammate", "t:theirs"]);
    const { lines, comingUp } = attentionOf(items, now);
    expect(lines.map((line) => line.key)).toEqual(["c:p1:teammate", "t:theirs"]);
    expect(comingUp).toBe(1);
  });

  // An overdue call fell between Today (still ahead only) and the queue, and showed nowhere.
  it("keeps a call whose time has passed in the queue", () => {
    const now = new Date("2026-10-14T12:00:00Z");
    const items = needsYouOf([], [task("missed", { kind: "call", dueAt: "2026-10-14T09:00:00Z" })], "u1", now);
    expect(items.map((item) => item.key)).toEqual(["t:missed"]);
  });
});

describe("attentionOf", () => {
  const now = new Date("2026-10-14T10:00:00Z");

  // A follow-up an outreach row carried showed whatever its date, while the caller's own task due the same day did not.
  it("leaves out a follow-up due next week even when an executive's outreach row carries it", () => {
    const quiet = conversation("quiet", "FOLLOW_UP", "t9");
    quiet.row = { ...quiet.row, action: { ...quiet.row.action!, dueAt: "2026-10-21T09:00:00Z" } };
    const { lines, comingUp } = attentionOf(needsYouOf([quiet, conversation("reply", "NEEDS_REPLY")], [], "u1", now), now);
    expect(lines.map((line) => line.key)).toEqual(["c:p1:reply"]);
    expect(comingUp).toBe(1);
  });

  it("ranks overdue first, then replies and delivery problems, then today, and only counts what is due later", () => {
    const items = needsYouOf(
      [conversation("bounced", "ATTENTION"), conversation("reply", "NEEDS_REPLY")],
      [
        task("later", { dueAt: "2026-10-20T09:00:00Z" }),
        task("today", { dueAt: "2026-10-14T15:00:00Z" }),
        task("late", { dueAt: "2026-10-12T09:00:00Z" }),
        task("undated", { dueAt: null }),
      ],
      "u1",
      now,
    );
    const { lines, comingUp } = attentionOf(items, now);
    expect(lines.map((line) => [line.key, line.reason])).toEqual([
      ["t:late", "overdue"],
      ["c:p1:reply", "reply"],
      ["c:p1:bounced", "delivery"],
      ["t:today", "today"],
    ]);
    expect(comingUp).toBe(2);
  });

  it("puts the longest-late first, and the newest reply first", () => {
    const older = conversation("older", "NEEDS_REPLY");
    const newer = { ...conversation("newer", "NEEDS_REPLY") };
    newer.row = { ...newer.row, lastActivityAt: "2026-10-14T09:00:00Z" };
    const items = needsYouOf(
      [older, newer],
      [task("yesterday", { dueAt: "2026-10-13T09:00:00Z" }), task("lastWeek", { dueAt: "2026-10-07T09:00:00Z" })],
      "u1",
      now,
    );
    expect(attentionOf(items, now).lines.map((line) => line.key)).toEqual([
      "t:lastWeek",
      "t:yesterday",
      "c:p1:newer",
      "c:p1:older",
    ]);
  });

  it("draws a follow-up row as the task it waits on", () => {
    const [line] = attentionOf(needsYouOf([conversation("quiet", "FOLLOW_UP", "t9")], [], "u1", now), now).lines;
    expect(line).toMatchObject({ group: "FOLLOW_UP", taskKind: "follow_up", taskTitle: "Reply", reason: "overdue" });
  });
});
