import { describe, expect, it } from "vitest";
import type { TaskResponse } from "../../tasks/api/types";
import type { MyConversationRow } from "../api/homeApi";
import { DEFAULT_HOME_LAYOUT } from "./homeLayout";
import { needsYouOf } from "./needsYou";

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
  it("puts replies first, then the caller's own tasks", () => {
    const items = needsYouOf(
      [conversation("follow", "FOLLOW_UP"), conversation("reply", "NEEDS_REPLY")],
      [task("t1")],
      DEFAULT_HOME_LAYOUT.kinds,
    );
    expect(items.map((item) => item.key)).toEqual(["c:p1:reply", "c:p1:follow", "t:t1"]);
  });

  it("never draws a task twice when a row already carries it as its next action", () => {
    const items = needsYouOf([conversation("reply", "NEEDS_REPLY", "t1")], [task("t1"), task("t2")], DEFAULT_HOME_LAYOUT.kinds);
    expect(items.map((item) => item.key)).toEqual(["c:p1:reply", "t:t2"]);
  });

  it("leaves out the groups the person turned off, but never their tasks", () => {
    const items = needsYouOf([conversation("reply", "NEEDS_REPLY")], [task("t1")], {
      ...DEFAULT_HOME_LAYOUT.kinds,
      NEEDS_REPLY: false,
    });
    expect(items.map((item) => item.key)).toEqual(["t:t1"]);
  });

  it("keeps calls and meetings for Today, and done tasks out", () => {
    const items = needsYouOf([], [task("call", { kind: "call" }), task("done", { status: "done" }), task("t1")], DEFAULT_HOME_LAYOUT.kinds);
    expect(items.map((item) => item.key)).toEqual(["t:t1"]);
  });
});
