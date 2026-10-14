import { describe, expect, it } from "vitest";
import type { NotificationResponse } from "../api/types";
import { namesLine, sentenceOf, type NotificationSentence } from "./notificationSentence";

const NOW = new Date(2026, 9, 14, 15, 0);

const CALL_NOTE = { id: "note-1", kind: "call" as const, writtenAt: new Date(2026, 9, 14, 11, 0).toISOString() };

function aNotification(overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: "n1",
    kind: "task_assigned",
    count: 1,
    actor: { userId: "u2", fullName: "Yara Haddad", avatarUrl: null },
    projectId: "p1",
    projectTitle: "CFO Search",
    personId: "pe1",
    candidateId: "c1",
    personName: "Ahmed Saleh",
    taskId: "t1",
    taskTitle: "Compensation check",
    note: null,
    names: [],
    dueAt: null,
    createdAt: new Date(2026, 9, 14, 14, 0).toISOString(),
    read: false,
    snoozedUntil: null,
    ...overrides,
  };
}

/** The sentence as a reader sees it, names in **bold** and muted asides in _underscores_. */
function spoken(sentence: NotificationSentence): string {
  const body = sentence.parts
    .map((part) => (part.strong ? `**${part.text}**` : part.muted ? `_${part.text}_` : part.text))
    .join("");
  return sentence.where ? `${body} · ${sentence.where}` : body;
}

const say = (overrides: Partial<NotificationResponse>) => spoken(sentenceOf(aNotification(overrides), NOW));

describe("sentenceOf", () => {
  it("says who assigned which task on whom, and when it is due", () => {
    const sentence = sentenceOf(aNotification({ dueAt: new Date(2026, 9, 16, 12, 0).toISOString() }), NOW);

    expect(spoken(sentence)).toBe("**Yara Haddad** assigned you **Compensation check** on **Ahmed Saleh** · CFO Search");
    expect(sentence.sub).toEqual({ text: "Due 16 Oct 2026, 12:00", tone: "muted" });
  });

  it("says a task is due at a time today, and on a date otherwise", () => {
    expect(say({ kind: "task_due_soon", actor: null, dueAt: new Date(2026, 9, 14, 16, 0).toISOString() })).toBe(
      "**Compensation check** on **Ahmed Saleh** is due at 16:00 · CFO Search",
    );
    expect(say({ kind: "task_due_soon", actor: null, dueAt: new Date(2026, 9, 15, 9, 0).toISOString() })).toBe(
      "**Compensation check** on **Ahmed Saleh** is due 15 Oct 2026, 09:00 · CFO Search",
    );
  });

  it("says a task is overdue and since when, as an aside", () => {
    expect(say({ kind: "task_overdue", actor: null, dueAt: new Date(2026, 9, 3, 17, 0).toISOString() })).toBe(
      "**Compensation check** on **Ahmed Saleh** is overdue_ · due 03 Oct 2026, 17:00_ · CFO Search",
    );
  });

  it("names a mention's note by its kind and whom it is on, never by what it says", () => {
    const sentence = sentenceOf(aNotification({ kind: "mentioned", taskId: null, note: CALL_NOTE }), NOW);

    expect(spoken(sentence)).toBe("**Yara Haddad** mentioned you in a call note on **Ahmed Saleh** · CFO Search");
    expect(sentence.sub).toBeNull();
  });

  it("says which note an assigned task was set from, before when it is due", () => {
    const sentence = sentenceOf(
      aNotification({ note: CALL_NOTE, dueAt: new Date(2026, 9, 16, 12, 0).toISOString() }),
      NOW,
    );

    expect(sentence.sub).toEqual({ text: "From a call note · Due 16 Oct 2026, 12:00", tone: "muted" });
  });

  it("says one reply by the executive, with the task it filed", () => {
    const sentence = sentenceOf(
      aNotification({ kind: "outreach_replied", actor: null, taskTitle: "Reply to Lina Farouk", personName: "Lina Farouk" }),
      NOW,
    );

    expect(spoken(sentence)).toBe("**Lina Farouk** replied · CFO Search");
    expect(sentence.sub).toEqual({ text: "Reply to Lina Farouk is on your tasks", tone: "task" });
  });

  it("folds grouped replies into a count on the position, naming two people and counting the rest", () => {
    const sentence = sentenceOf(
      aNotification({
        kind: "outreach_replied",
        actor: null,
        count: 3,
        projectTitle: "Head of Treasury",
        names: ["Karim Nassar", "Rania Odeh", "Omar Khalil"],
        taskId: null,
      }),
      NOW,
    );

    expect(spoken(sentence)).toBe("**3 replies** on **Head of Treasury** — Karim Nassar, Rania Odeh and 1 other");
    expect(sentence.where).toBeNull();
  });

  it("says a bounce, and offers no task line when no task was filed", () => {
    const sentence = sentenceOf(aNotification({ kind: "outreach_bounced", actor: null, taskId: null }), NOW);

    expect(spoken(sentence)).toBe("An email to **Ahmed Saleh** bounced · CFO Search");
    expect(sentence.sub).toBeNull();
  });

  it("says a booked call, with the debrief it put on the caller's tasks", () => {
    const sentence = sentenceOf(
      aNotification({ kind: "call_booked", actor: null, dueAt: new Date(2026, 9, 15, 11, 0).toISOString() }),
      NOW,
    );

    expect(spoken(sentence)).toBe("**Ahmed Saleh** booked a call · CFO Search");
    expect(sentence.sub).toEqual({ text: "Debrief is on your tasks · 15 Oct 2026, 11:00", tone: "task" });
  });

  it("names the position someone was added to, once", () => {
    expect(say({ kind: "added_to_team", projectTitle: "COO Search", taskId: null })).toBe(
      "**Yara Haddad** added you to **COO Search**",
    );
  });

  it("counts the tasks moved, and from whom", () => {
    expect(say({ kind: "tasks_moved", count: 4, actor: { userId: "u3", fullName: "Omar Khalil", avatarUrl: null } })).toBe(
      "**4 tasks** moved to you from **Omar Khalil** · CFO Search",
    );
    expect(say({ kind: "tasks_moved", count: 1 })).toBe("**1 task** moved to you from **Yara Haddad** · CFO Search");
  });

  it("says Uncava did it when nobody did", () => {
    expect(say({ actor: null })).toBe("**Uncava** assigned you **Compensation check** on **Ahmed Saleh** · CFO Search");
  });
});

describe("namesLine", () => {
  it("names two and counts the others", () => {
    expect(namesLine(["A", "B", "C"], 3)).toBe("A, B and 1 other");
    expect(namesLine(["A", "B", "C", "D"], 5)).toBe("A, B and 3 others");
  });

  it("joins two names with and, and says one alone", () => {
    expect(namesLine(["A", "B"], 2)).toBe("A and B");
    expect(namesLine(["A"], 1)).toBe("A");
  });

  it("counts replies past the names it was sent, so the total still adds up", () => {
    expect(namesLine(["A"], 3)).toBe("A and 2 others");
    expect(namesLine([], 3)).toBe("");
  });
});
