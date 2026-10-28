import { describe, expect, it } from "vitest";
import type { PersonActivityKind, PersonTimelineEntry } from "../api/types";
import {
  ACTIVITY_FAMILIES,
  actorKeyOf,
  actorOf,
  asideOf,
  automationCauseOf,
  cardTimeOf,
  familiesOf,
  familyOf,
  fieldLabel,
  foldRuns,
  iconOf,
  isMinor,
  peopleOf,
  previewOf,
  shortDate,
  shortDateTime,
  toneOf,
  verbOf,
} from "./activityPresentation";

const line = (
  id: number,
  kind: PersonActivityKind,
  overrides: Partial<PersonTimelineEntry> = {},
): PersonTimelineEntry => ({
  id,
  kind,
  occurredAt: "2026-10-27T09:10:00Z",
  actorUserId: "dana",
  actorName: "Dana Aboud",
  actorAvatarUrl: null,
  personId: "person-1",
  personName: "Fatima Al Mazrouei",
  projectId: "p1",
  projectTitle: "Group CFO",
  details: {},
  noteExcerpt: null,
  changes: [],
  ...overrides,
});

const ALL_KINDS: PersonActivityKind[] = [
  "ADDED_TO_POOL", "MAPPED", "UNMAPPED", "STATUS_CHANGED", "TRIAGE_STATUS_CHANGED", "NEXT_ACTION_SET",
  "CLIENT_VISIBILITY_CHANGED", "PROFILE_EDITED", "CONTACTS_EDITED", "CONTACT_FOUND", "RESEARCHED", "AI_ASSESSED",
  "ASSESSMENT_REVIEWED", "UPDATED_FROM_CALL", "NOTE_ADDED", "NOTE_EDITED", "NOTE_REMOVED", "NOTE_RESTORED", "TAGGED",
  "UNTAGGED", "OWNER_CHANGED", "DO_NOT_CONTACT_SET", "DO_NOT_CONTACT_CLEARED", "OUTREACH_ENROLLED", "EMAIL_SENT",
  "EMAIL_REPLIED", "OUTREACH_STOPPED", "MEETING_BOOKED", "MEETING_RESCHEDULED", "MEETING_CANCELLED", "DOCUMENT_ADDED",
  "DOCUMENT_VERSION_ADDED", "DOCUMENT_REMOVED", "DOCUMENT_VERSION_REMOVED", "TASK_CREATED", "TASK_COMPLETED",
  "TASK_REOPENED", "TASK_REASSIGNED", "TASK_CANCELLED",
];

describe("activity presentation", () => {
  it("gives every kind the server writes a family, a verb and a glyph of its own — never the fallback", () => {
    for (const kind of ALL_KINDS) {
      const entry = line(1, kind);
      expect(ACTIVITY_FAMILIES.map((option) => option.family), kind).toContain(familyOf(entry));
      expect(verbOf(entry), kind).not.toBe("updated the record");
      expect(iconOf(entry), kind).toBeTruthy();
    }
  });

  it("draws a call log with a handset, a logged email with an envelope and a meeting with a camera", async () => {
    const { ICONS } = await import("../../../components/layout/Icon");
    expect(iconOf(line(1, "NOTE_ADDED", { details: { kind: "CALL" } }))).toBe(ICONS.phoneCall);
    expect(iconOf(line(1, "NOTE_ADDED", { details: { kind: "EMAIL" } }))).toBe(ICONS.mail);
    expect(iconOf(line(1, "NOTE_ADDED", { details: { kind: "MEETING" } }))).toBe(ICONS.video);
    expect(iconOf(line(1, "NOTE_ADDED", { details: { kind: "GENERAL" } }))).toBe(ICONS.fileText);
  });

  it("draws a kind a newer server writes as a quiet record line rather than failing", () => {
    const unknown = line(1, "SOMETHING_NEW" as PersonActivityKind);
    expect(familyOf(unknown)).toBe("profile");
    expect(verbOf(unknown)).toBe("updated the record");
    expect(isMinor(unknown)).toBe(true);
    expect(previewOf(unknown)).toBeNull();
    expect(toneOf(unknown)).toBe("ai");
  });

  it("files the families the way the filter names them", () => {
    expect(familyOf(line(1, "EMAIL_REPLIED"))).toBe("emails");
    expect(familyOf(line(1, "OUTREACH_STOPPED"))).toBe("emails");
    expect(familyOf(line(1, "MEETING_CANCELLED"))).toBe("meetings");
    expect(familyOf(line(1, "STATUS_CHANGED"))).toBe("pipeline");
    expect(familyOf(line(1, "TASK_REASSIGNED"))).toBe("tasks");
    expect(familyOf(line(1, "DO_NOT_CONTACT_SET"))).toBe("contact");
    expect(familyOf(line(1, "TAGGED"))).toBe("profile");
  });

  it("makes content events cards and quick changes slim rows; a background AI run is a row, an assessment a card", () => {
    expect(isMinor(line(1, "NOTE_ADDED"))).toBe(false);
    expect(isMinor(line(1, "EMAIL_SENT"))).toBe(false);
    expect(isMinor(line(1, "PROFILE_EDITED"))).toBe(false);
    expect(isMinor(line(1, "TAGGED"))).toBe(true);
    expect(isMinor(line(1, "CONTACT_FOUND"))).toBe(true);
    expect(isMinor(line(1, "AI_ASSESSED", { details: { scope: "BACKGROUND" } }))).toBe(true);
    expect(isMinor(line(1, "AI_ASSESSED", { details: { scope: "ASSESSMENT" } }))).toBe(false);
  });

  it("marks what took something away in the danger tone", () => {
    for (const kind of ["DO_NOT_CONTACT_SET", "UNMAPPED", "MEETING_CANCELLED", "NOTE_REMOVED", "TASK_CANCELLED"] as const) {
      expect(toneOf(line(1, kind)), kind).toBe("danger");
    }
    expect(toneOf(line(1, "DO_NOT_CONTACT_CLEARED"))).toBe("neutral");
    expect(toneOf(line(1, "MEETING_BOOKED"))).toBe("meet");
  });

  describe("who did it", () => {
    it("is the colleague who acted, with their picture", () => {
      expect(actorOf(line(1, "NOTE_ADDED", { actorAvatarUrl: "https://example.com/dana.png" }))).toEqual({
        kind: "user",
        id: "dana",
        name: "Dana Aboud",
        avatarUrl: "https://example.com/dana.png",
      });
    });

    it("is the executive for their own reply and for a call they booked through the link", () => {
      expect(actorOf(line(1, "EMAIL_REPLIED", { actorUserId: null, actorName: null }))).toEqual({ kind: "executive" });
      expect(actorOf(line(1, "MEETING_BOOKED", { details: { viaLink: "true" } }))).toEqual({ kind: "executive" });
      expect(actorOf(line(1, "MEETING_BOOKED", { details: { viaLink: "false" } })).kind).toBe("user");
    });

    it("is the automation for a status a rule moved, naming whose act set it off", () => {
      const moved = line(1, "STATUS_CHANGED", { details: { automation: "EMAIL_SENT", to: "contacted" } });
      expect(actorOf(moved)).toEqual({ kind: "automation", triggeredBy: "Dana Aboud" });
      expect(automationCauseOf(moved)).toBe("after Dana Aboud's email");
      expect(automationCauseOf(line(1, "STATUS_CHANGED", { actorUserId: null, actorName: null, details: { automation: "REPLIED" } })))
        .toBe("after the reply");
      expect(automationCauseOf(line(1, "STATUS_CHANGED", { actorName: null, details: { automation: "CALL_BOOKED" } })))
        .toBe("after the booked call");
      expect(automationCauseOf(line(1, "STATUS_CHANGED", { details: { automation: "SOMETHING_NEW" } })))
        .toBe("after Dana Aboud's event");
      expect(automationCauseOf(line(1, "STATUS_CHANGED"))).toBeNull();
    });

    it("is the automation for a line nobody's request stands behind", () => {
      expect(actorOf(line(1, "OUTREACH_STOPPED", { actorUserId: null, actorName: null }))).toEqual({
        kind: "automation",
        triggeredBy: null,
      });
    });

    it("is unknown when a line names a user whose name is gone", () => {
      expect(actorOf(line(1, "TAGGED", { actorName: null }))).toEqual({ kind: "unknown" });
    });

    it("lists colleagues first, then the executive, then Uncava, once each", () => {
      const people = peopleOf([
        line(1, "EMAIL_REPLIED", { actorUserId: null, actorName: null }),
        line(2, "OUTREACH_STOPPED", { actorUserId: null, actorName: null }),
        line(3, "NOTE_ADDED"),
        line(4, "TAGGED", { actorUserId: "omar", actorName: "Omar Khalil" }),
        line(5, "NOTE_ADDED"),
      ]);
      expect(people.map((person) => person.key)).toEqual(["user:dana", "user:omar", "executive", "automation"]);
      expect(actorKeyOf(line(9, "NOTE_ADDED"))).toBe("user:dana");
    });
  });

  describe("verbs", () => {
    it("never says 'on this position': the drawer already is this position", () => {
      for (const kind of ALL_KINDS) {
        expect(verbOf(line(1, kind)), kind).not.toMatch(/on this position/);
      }
    });

    it("words notes, documents, contacts and outreach as a researcher reads them", () => {
      expect(verbOf(line(1, "NOTE_ADDED", { details: { kind: "CALL" } }))).toBe("logged a call");
      expect(verbOf(line(1, "NOTE_ADDED", { details: { kind: "BOGUS" } }))).toBe("wrote a note");
      expect(verbOf(line(1, "DOCUMENT_ADDED", { details: { category: "ASSESSMENT" } }))).toBe("uploaded an assessment");
      expect(verbOf(line(1, "DOCUMENT_VERSION_ADDED", { details: { category: "CV", version: "3" } })))
        .toBe("uploaded version 3 of the CV");
      expect(verbOf(line(1, "CONTACT_FOUND", { details: { channel: "PHONE", found: "0" } }))).toBe("looked for a phone number");
      expect(verbOf(line(1, "CONTACT_FOUND", { details: { channel: "EMAIL", found: "1" } }))).toBe("found an email");
      expect(verbOf(line(1, "EMAIL_SENT", { details: { sequenceId: "s", step: "2" } }))).toBe("emailed them");
      expect(verbOf(line(1, "EMAIL_SENT", { details: { manual: "true" } }))).toBe("sent an email");
      expect(verbOf(line(1, "OWNER_CHANGED", { details: { ownerUserId: "x", owner: "Yara Haddad" } })))
        .toBe("made Yara Haddad the owner");
      expect(verbOf(line(1, "OWNER_CHANGED"))).toBe("cleared the owner");
      expect(verbOf(line(1, "UPDATED_FROM_CALL", { details: { reverted: "true" } }))).toBe("undid changes from a call");
    });

    it("adds the short aside a slim row needs, and none where nothing is known", () => {
      expect(asideOf(line(1, "TAGGED", { details: { tag: "Referral" } }))).toBe("Referral");
      expect(asideOf(line(1, "TAGGED"))).toBeNull();
      expect(asideOf(line(1, "CONTACT_FOUND", { details: { found: "0" } }))).toBe("none found");
      expect(asideOf(line(1, "CONTACTS_EDITED", { details: { emails: "1", phones: "2" } }))).toBe("1 email, 2 phones on file");
      expect(asideOf(line(1, "CONTACTS_EDITED", { details: { emails: "x", phones: "2" } }))).toBeNull();
      expect(asideOf(line(1, "CONTACTS_EDITED"))).toBeNull();
      expect(asideOf(line(1, "TASK_REASSIGNED", { details: { assignee: "Omar Khalil" } }))).toBe("to Omar Khalil");
    });
  });

  describe("previews", () => {
    it("shows a note's opening words, or that it was deleted", () => {
      expect(previewOf(line(1, "NOTE_ADDED", { noteExcerpt: "Open to a move", projectId: null })))
        .toEqual({ type: "note", excerpt: "Open to a move", noteKind: "GENERAL", aboutThePerson: true });
      expect(previewOf(line(1, "NOTE_ADDED", { noteExcerpt: null }))).toMatchObject({ type: "note", excerpt: null });
    });

    it("shows a status move by the labels it was written with, or the built-in label of an older line", () => {
      expect(previewOf(line(1, "STATUS_CHANGED", { details: { from: "a", to: "b", fromLabel: "First call", toLabel: "Met" } })))
        .toEqual({ type: "status", from: "First call", to: "Met", cause: null });
      expect(previewOf(line(1, "STATUS_CHANGED", { details: { to: "engaged" } })))
        .toMatchObject({ type: "status", from: null, to: "Engaged" });
    });

    it("lists two changes and counts the rest; an older edit with only field names lists those", () => {
      const changes = [
        { field: "noticePeriod", from: "1 month", to: "3 months" },
        { field: "baseSalary", from: "AED 85,000", to: "AED 92,000" },
        { field: "summary", from: null, to: "Led the IPO" },
      ];
      expect(previewOf(line(1, "PROFILE_EDITED", { changes }))).toEqual({
        type: "changes",
        changes: changes.slice(0, 2),
        hidden: 1,
      });
      expect(previewOf(line(1, "UPDATED_FROM_CALL", { details: { fields: "noticePeriod,brandNewField" } })))
        .toEqual({ type: "fields", labels: ["Notice period", "Brand new field"] });
      expect(previewOf(line(1, "PROFILE_EDITED", { changes: undefined }))).toBeNull();
    });

    it("shows a call's time, the time it moved from, and whether it was cancelled; none without a time", () => {
      expect(previewOf(line(1, "MEETING_RESCHEDULED", { details: { startsAt: "2026-10-30T11:00:00Z", previousStartsAt: "2026-10-29T11:00:00Z" } })))
        .toEqual({ type: "meeting", startsAt: "2026-10-30T11:00:00Z", previousStartsAt: "2026-10-29T11:00:00Z", cancelled: false });
      expect(previewOf(line(1, "MEETING_CANCELLED", { details: { startsAt: "2026-10-30T11:00:00Z" } })))
        .toMatchObject({ cancelled: true });
      expect(previewOf(line(1, "MEETING_BOOKED"))).toBeNull();
    });

    it("previews emails, documents, tasks, the origin and an assessment", () => {
      expect(previewOf(line(1, "EMAIL_SENT", { details: { sequence: "CFO approach", step: "1" } })))
        .toEqual({ type: "email", label: "Sequence · CFO approach", detail: "Step 1" });
      expect(previewOf(line(1, "EMAIL_SENT"))).toEqual({ type: "email", label: "Written by hand", detail: null });
      expect(previewOf(line(1, "DOCUMENT_ADDED", { details: { category: "CV" } })))
        .toEqual({ type: "document", title: null, noun: "CV", version: null });
      expect(previewOf(line(1, "TASK_CREATED", { details: { title: "Prepare questions", dueAt: "2026-10-30T10:00:00Z" } })))
        .toEqual({ type: "task", title: "Prepare questions", dueAt: "2026-10-30T10:00:00Z", assignee: null });
      expect(previewOf(line(1, "TASK_CREATED"))).toBeNull();
      expect(previewOf(line(1, "MAPPED", { details: { door: "EXTENSION" } })))
        .toEqual({ type: "origin", door: "Captured with the plugin", mapped: true });
      expect(previewOf(line(1, "ADDED_TO_POOL", { details: { door: "SOMEWHERE_NEW" } })))
        .toEqual({ type: "origin", door: null, mapped: false });
      expect(previewOf(line(1, "AI_ASSESSED", { details: { scope: "BACKGROUND" } }))).toBeNull();
    });
  });

  describe("folding", () => {
    const quick = (id: number, minute: number, overrides: Partial<PersonTimelineEntry> = {}) =>
      line(id, "TAGGED", { occurredAt: `2026-10-27T09:${String(minute).padStart(2, "0")}:00Z`, ...overrides });

    it("folds three or more quick changes by one person within ten minutes", () => {
      const items = foldRuns([quick(3, 14), quick(2, 12), quick(1, 10)]);
      expect(items).toHaveLength(1);
      expect(items[0]).toMatchObject({ type: "fold", key: "fold-3" });
    });

    it("leaves two alone, a gap past ten minutes, another person, and a card between them", () => {
      expect(foldRuns([quick(2, 12), quick(1, 10)]).every((item) => item.type === "entry")).toBe(true);
      expect(foldRuns([quick(3, 40), quick(2, 20), quick(1, 10)]).every((item) => item.type === "entry")).toBe(true);
      expect(
        foldRuns([quick(3, 14), quick(2, 12, { actorUserId: "omar", actorName: "Omar Khalil" }), quick(1, 10)])
          .every((item) => item.type === "entry"),
      ).toBe(true);
      expect(
        foldRuns([quick(3, 14), line(2, "NOTE_ADDED", { occurredAt: "2026-10-27T09:12:00Z" }), quick(1, 10)])
          .every((item) => item.type === "entry"),
      ).toBe(true);
    });

    it("never folds what nobody did, nor lines with unreadable times", () => {
      const automatic = { actorUserId: null, actorName: null };
      expect(foldRuns([quick(3, 14, automatic), quick(2, 12, automatic), quick(1, 10, automatic)])
        .every((item) => item.type === "entry")).toBe(true);
      expect(foldRuns([quick(3, 14, { occurredAt: "nonsense" }), quick(2, 12), quick(1, 10)])
        .every((item) => item.type === "entry")).toBe(true);
    });

    it("keeps the order it was given, a fold standing where its first line stood", () => {
      const items = foldRuns([line(9, "NOTE_ADDED", { occurredAt: "2026-10-27T10:00:00Z" }), quick(3, 14), quick(2, 12), quick(1, 10), line(0, "EMAIL_SENT")]);
      expect(items.map((item) => (item.type === "fold" ? item.key : item.entry.id))).toEqual([9, "fold-3", 0]);
    });
  });

  it("offers only the families the history holds, in the menu's order", () => {
    expect(familiesOf([line(1, "TAGGED"), line(2, "EMAIL_SENT"), line(3, "NOTE_ADDED")]).map((option) => option.label))
      .toEqual(["Notes", "Emails", "Profile & AI"]);
    expect(familiesOf([])).toEqual([]);
  });

  it("times a card in minutes within the hour and by the clock after it", () => {
    const now = new Date("2026-10-28T11:20:00Z");
    expect(cardTimeOf("2026-10-28T11:19:40Z", now)).toEqual({ text: "just now", isRecent: true });
    expect(cardTimeOf("2026-10-28T11:06:00Z", now)).toEqual({ text: "14 min ago", isRecent: true });
    expect(cardTimeOf("2026-10-28T09:00:00Z", now).isRecent).toBe(false);
    expect(cardTimeOf("not a time", now)).toEqual({ text: "—", isRecent: false });
    // A clock a little ahead of the viewer's reads as a time, never as "-3 min ago".
    expect(cardTimeOf("2026-10-28T11:23:00Z", now).isRecent).toBe(false);
  });

  it("names fields as the Career tab does, and words an unknown key", () => {
    expect(fieldLabel("baseSalary")).toBe("Base salary");
    expect(fieldLabel("openToRelocate")).toBe("Open to relocate");
    expect(fieldLabel("boardSeats")).toBe("Board seats");
  });

  it("names an addition with no position as one to the candidates", () => {
    expect(verbOf(line(1, "ADDED_TO_POOL", { projectId: null }))).toBe("added them to your candidates");
    expect(verbOf(line(1, "ADDED_TO_POOL"))).toBe("added them to this position");
  });

  it("formats a day the same way for a header and a card", () => {
    expect(shortDate("2026-10-30T11:00:00Z")).toMatch(/^\w{3} 30 Oct$/);
    expect(shortDate(null)).toBeNull();
    expect(shortDate("nope")).toBeNull();
    expect(shortDateTime("2026-10-30T11:00:00Z")?.startsWith(shortDate("2026-10-30T11:00:00Z") ?? "x")).toBe(true);
  });

  it("refuses to format what is not a time", () => {
    expect(shortDateTime(null)).toBeNull();
    expect(shortDateTime("")).toBeNull();
    expect(shortDateTime("nope")).toBeNull();
    expect(shortDateTime("2026-10-30T11:00:00Z")).toMatch(/Oct/);
  });
});
