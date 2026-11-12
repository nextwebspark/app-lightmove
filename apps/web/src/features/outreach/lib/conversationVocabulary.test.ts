import { describe, expect, it } from "vitest";
import type { ConversationMessage, ConversationRow } from "../api/conversationApi";
import type { PersonNote } from "../../candidates/api/types";
import type { OutreachRun } from "../api/runApi";
import type { Meeting } from "../api/meetingApi";
import {
  actorOfEvent,
  addressLineOf,
  composerFootnoteOf,
  dueChipOf,
  eventFaceOf,
  glanceOf,
  queueTimeOf,
  sinceOf,
  tileOfEvent,
  tileOfMeeting,
} from "./conversationVocabulary";

const NOW = new Date("2026-10-21T12:00:00Z");

function message(overrides: Partial<ConversationMessage>): ConversationMessage {
  return {
    id: "m1",
    direction: "OUTBOUND",
    channel: "EMAIL",
    kind: "SEQUENCE_STEP",
    status: "SENT",
    at: "2026-10-20T05:00:00Z",
    subject: "A confidential conversation",
    text: null,
    format: "TEXT",
    fromAddress: null,
    toAddress: null,
    senderUserId: "u1",
    senderName: null,
    senderAvatarUrl: null,
    enrollmentId: null,
    sequenceName: null,
    stepNumber: null,
    stepCount: null,
    failReason: null,
    attachments: [],
    ...overrides,
  };
}

describe("eventFaceOf", () => {
  const face = (overrides: Partial<ConversationMessage>) =>
    eventFaceOf({ type: "MESSAGE", at: "2026-10-20T05:00:00Z", message: message(overrides) }, "Fatima", "p1");

  it("tags each email as the mockup does", () => {
    expect(face({ sequenceName: "CFO — first approach", stepNumber: 2, stepCount: 3 })).toEqual({
      tag: "SENT EMAIL",
      tone: "accent",
      title: "A confidential conversation",
      context: "CFO — first approach · Step 2 of 3",
    });
    expect(face({ kind: "MANUAL" }).context).toBe("One-off email");
    expect(face({ direction: "INBOUND", kind: "REPLY", status: "RECEIVED" })).toMatchObject({
      tag: "SYNCED EMAIL",
      title: "Fatima replied",
    });
    expect(face({ direction: "INBOUND", kind: "BOUNCE", status: "RECEIVED" })).toMatchObject({
      tag: "DELIVERY",
      title: "Email bounced",
    });
    expect(face({ kind: "MANUAL", status: "FAILED", failReason: "DO_NOT_CONTACT" })).toMatchObject({
      tag: "DELIVERY",
      title: "Email not sent",
      context: "Marked do not contact",
    });
    expect(face({ kind: "MANUAL", status: "SCHEDULED" }).tag).toBe("SCHEDULED");
  });

  it("calls a team note private and names who wrote it", () => {
    expect(
      eventFaceOf(
        {
          type: "NOTE",
          at: "2026-10-20T05:00:00Z",
          note: {
            id: "n1",
            kind: "call",
            body: "Spoke briefly",
            format: "plain",
            pinned: false,
            projectId: "p1",
            projectTitle: "Group CFO",
            authorUserId: "u1",
            authorName: "Yara Haddad",
            authorAvatarUrl: null,
            createdAt: "2026-10-20T05:00:00Z",
            editedAt: null,
            editedByName: null,
            editable: true,
          },
        },
        "Fatima",
        "p1",
      ),
    ).toEqual({
      tag: "HUMAN NOTE",
      tone: "accent",
      title: "Call note added by Yara Haddad",
      context: "Private team note · not shared with client",
    });
  });

  it("names a LinkedIn note by the network", () => {
    const note: PersonNote = {
      id: "n1",
      kind: "linkedin",
      body: "Accepted the request",
      format: "plain",
      pinned: false,
      projectId: "p1",
      projectTitle: "Group CFO",
      authorUserId: "u1",
      authorName: "Yara Haddad",
      authorAvatarUrl: null,
      createdAt: "2026-10-20T05:00:00Z",
      editedAt: null,
      editedByName: null,
      editable: true,
    };
    expect(eventFaceOf({ type: "NOTE", at: note.createdAt, note }, "Fatima", "p1").title).toBe(
      "LinkedIn note added by Yara Haddad",
    );
  });
});

describe("an entry's lead and face", () => {
  const email = (overrides: Partial<ConversationMessage>) =>
    ({ type: "MESSAGE", at: "2026-10-20T05:00:00Z", message: message(overrides) }) as const;
  const note = (kind: PersonNote["kind"], overrides: Partial<PersonNote> = {}) =>
    ({
      type: "NOTE",
      at: "2026-10-20T05:00:00Z",
      note: {
        id: "n1",
        kind,
        body: "Spoke briefly",
        format: "plain",
        pinned: false,
        projectId: "p1",
        projectTitle: "Group CFO",
        authorUserId: "u1",
        authorName: "Yara Haddad",
        authorAvatarUrl: "/api/v1/avatars/yara",
        createdAt: "2026-10-20T05:00:00Z",
        editedAt: null,
        editedByName: null,
        editable: true,
        ...overrides,
      },
    }) as const;
  const meeting: Meeting = {
    id: "mt1",
    title: "First conversation",
    startsAt: "2026-10-22T09:00:00Z",
    endsAt: "2026-10-22T09:30:00Z",
    ownerUserId: "u1",
    ownerName: "Yara Haddad",
    ownerAvatarUrl: null,
    joinUrl: null,
    videoProvider: null,
    viaLink: false,
    bookedInUncava: true,
    editable: false,
  };

  it("leads each email with its own tile", () => {
    expect(tileOfEvent(email({}))).toBe("email");
    expect(tileOfEvent(email({ status: "SCHEDULED" }))).toBe("scheduled");
    expect(tileOfEvent(email({ direction: "INBOUND", kind: "REPLY", status: "RECEIVED" }))).toBe("reply");
    expect(tileOfEvent(email({ direction: "INBOUND", kind: "BOUNCE", status: "RECEIVED" }))).toBe("problem");
    expect(tileOfEvent(email({ status: "FAILED" }))).toBe("problem");
  });

  it("leads a call, a LinkedIn message and a meeting with their own mark, a video call with the camera", () => {
    expect(tileOfEvent(note("call"))).toBe("call");
    expect(tileOfEvent(note("linkedin"))).toBe("linkedin");
    expect(tileOfEvent(note("whatsapp"))).toBe("whatsapp");
    expect(tileOfEvent(note("meeting"))).toBe("meeting");
    expect(tileOfEvent({ type: "MEETING", at: meeting.startsAt, meeting })).toBe("meeting");
    expect(tileOfMeeting({ ...meeting, videoProvider: "Google Meet" })).toBe("video");
    expect(tileOfMeeting(meeting, true)).toBe("video");
  });

  it("names the sender, the author, the call's holder, the executive answering, and nobody on a bounce", () => {
    expect(actorOfEvent(email({ senderName: "Yara Haddad", senderAvatarUrl: "/api/v1/avatars/yara" }))).toEqual({
      kind: "user",
      id: "u1",
      name: "Yara Haddad",
      avatarUrl: "/api/v1/avatars/yara",
    });
    expect(actorOfEvent(email({ direction: "INBOUND", kind: "REPLY", status: "RECEIVED" }))).toEqual({
      kind: "executive",
    });
    expect(actorOfEvent(email({ direction: "INBOUND", kind: "BOUNCE", status: "RECEIVED" }))).toEqual({
      kind: "automation",
      triggeredBy: null,
    });
    expect(actorOfEvent(note("linkedin"))).toMatchObject({ kind: "user", avatarUrl: "/api/v1/avatars/yara" });
    expect(actorOfEvent({ type: "MEETING", at: meeting.startsAt, meeting })).toMatchObject({
      kind: "user",
      name: "Yara Haddad",
    });
    expect(actorOfEvent(email({ senderName: null }))).toEqual({ kind: "unknown" });
  });

  it("says where an email went and where a reply came from", () => {
    expect(addressLineOf(message({ toAddress: "fatima@gulfports.example" }))).toBe("→ fatima@gulfports.example");
    expect(
      addressLineOf(message({ direction: "INBOUND", kind: "REPLY", fromAddress: "fatima@gulfports.example" })),
    ).toBe("fatima@gulfports.example");
    expect(addressLineOf(message({ direction: "INBOUND", kind: "BOUNCE", fromAddress: "mailer-daemon@x" }))).toBeNull();
  });
});

describe("queueTimeOf", () => {
  function row(overrides: Partial<ConversationRow>): ConversationRow {
    return {
      candidateId: "c1",
      personId: "p1",
      fullName: "Fatima",
      title: null,
      companyName: null,
      enrichedAt: null,
      status: "contacted",
      statusLabel: "Contacted",
      statusColour: "accent",
      group: "NEEDS_REPLY",
      action: null,
      run: null,
      latest: null,
      lastSentAt: null,
      scheduledAt: null,
      lastActivityAt: "2026-10-21T11:48:00Z",
      ...overrides,
    };
  }

  it("says how long ago they wrote, PREP before a first email, and a late follow-up in red", () => {
    expect(queueTimeOf(row({}), NOW)).toEqual({ text: "12m", isLate: false });
    expect(queueTimeOf(row({ group: "PREPARATION" }), NOW)).toEqual({ text: "PREP", isLate: false });
    const lateTask = {
      taskId: "t1",
      title: "Follow up",
      kind: "follow_up" as const,
      rule: null,
      dueAt: "2026-10-19T09:00:00Z",
      assignee: { userId: "u1", fullName: null, avatarUrl: null },
    };
    expect(queueTimeOf(row({ group: "FOLLOW_UP", action: lateTask }), NOW)).toEqual({ text: "2D", isLate: true });
    expect(queueTimeOf(row({ group: "ATTENTION" }), NOW).isLate).toBe(true);
  });

  it("reads the very recent as now", () => {
    expect(sinceOf("2026-10-21T11:59:40Z", NOW)).toBe("NOW");
    expect(sinceOf(null, NOW)).toBe("");
  });
});

describe("dueChipOf", () => {
  it("says what is overdue and by how many days", () => {
    expect(dueChipOf("2026-10-20T09:00:00Z", NOW)).toEqual({ text: "OVERDUE · 1 DAY", isLate: true });
    expect(dueChipOf(null, NOW)).toBeNull();
    expect(dueChipOf("2026-10-25T09:00:00Z", NOW)?.text).toMatch(/^DUE 25 OCT · /);
  });
});

describe("composerFootnoteOf", () => {
  it("writes the mockup's sentences for a reply", () => {
    expect(
      composerFootnoteOf({
        isReply: true,
        resolvesReplyTask: true,
        statusLabel: "Interested",
        movesTo: null,
        stopsRunningSequence: false,
      }),
    ).toBe("Sending resolves this reply task. Pipeline stage stays Interested.");
  });

  it("says a reply into a running sequence's thread stops it, and a first email waits for a press", () => {
    expect(
      composerFootnoteOf({ isReply: true, resolvesReplyTask: false, statusLabel: "Contacted", movesTo: null, stopsRunningSequence: true }),
    ).toBe("Sending stops the running sequence. Pipeline stage stays Contacted.");
    expect(
      composerFootnoteOf({ isReply: false, resolvesReplyTask: false, statusLabel: "Identified", movesTo: "Contacted", stopsRunningSequence: true }),
    ).toBe("Moves to Contacted. Nothing is sent until you press Send now or Schedule.");
  });
});

describe("glanceOf", () => {
  const sent = { type: "MESSAGE" as const, at: "2026-10-19T05:00:00Z", message: message({}) };
  const reply = {
    type: "MESSAGE" as const,
    at: "2026-10-20T05:00:00Z",
    message: message({ direction: "INBOUND", kind: "REPLY", status: "RECEIVED" }),
  };
  const later = {
    type: "MESSAGE" as const,
    at: "2026-10-25T05:00:00Z",
    message: message({ kind: "MANUAL", status: "SCHEDULED" }),
  };
  const run = (status: OutreachRun["status"]) =>
    ({ status, sequenceName: "CFO — first approach", sentCount: 1, stepCount: 3 }) as OutreachRun;

  it("counts LinkedIn and WhatsApp notes apart from calls and other notes", () => {
    const noteOf = (id: string, kind: PersonNote["kind"]) => ({
      type: "NOTE" as const,
      at: "2026-10-20T05:00:00Z",
      note: {
        id,
        kind,
        body: "Spoke",
        format: "plain" as const,
        pinned: false,
        projectId: "p1",
        projectTitle: "Group CFO",
        authorUserId: "u1",
        authorName: "Yara Haddad",
        authorAvatarUrl: null,
        createdAt: "2026-10-20T05:00:00Z",
        editedAt: null,
        editedByName: null,
        editable: true,
      },
    });
    const glance = glanceOf(
      [noteOf("n1", "call"), noteOf("n2", "linkedin"), noteOf("n4", "whatsapp"), noteOf("n3", "general")],
      null,
      NOW,
    );
    expect(glance.facts.map((fact) => `${fact.count} ${fact.noun}`)).toEqual([
      "1 call",
      "1 LinkedIn",
      "1 WhatsApp",
      "1 note",
    ]);
  });

  it("counts only what happened, and dates the last touch by the latest of it", () => {
    const glance = glanceOf([later, reply, sent], null, NOW);
    expect(glance.facts.map((fact) => `${fact.count} ${fact.noun}`)).toEqual(["1 sent", "1 reply", "1 scheduled"]);
    expect(glance.lastTouch).toBe("1D ago");
    expect(glance.sequence).toBeNull();
  });

  it("names the sequence only while it is still going", () => {
    expect(glanceOf([], run("ACTIVE"), NOW).sequence).toBe("CFO — first approach · 1 of 3 sent");
    expect(glanceOf([], run("REPLIED"), NOW).sequence).toBeNull();
  });

  it("has nothing to say before anything has happened", () => {
    expect(glanceOf([], null, NOW)).toEqual({ facts: [], lastTouch: null, sequence: null });
  });
});
