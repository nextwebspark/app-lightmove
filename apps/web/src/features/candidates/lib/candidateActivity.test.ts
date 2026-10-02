import { describe, expect, it } from "vitest";
import type { PersonTimelineEntry } from "../api/types";
import { lastActivityOf, timelineLines } from "./candidateActivity";

const THIS = "project-this";

function entry(overrides: Partial<PersonTimelineEntry>): PersonTimelineEntry {
  return {
    id: 1,
    kind: "ADDED_TO_POOL",
    occurredAt: "2026-09-30T10:00:00Z",
    actorUserId: "user-1",
    actorName: "Sara Al-Mansour",
    actorAvatarUrl: null,
    personId: "person-1",
    personName: "Fatima Al Mazrouei",
    projectId: THIS,
    projectTitle: "Chief Financial Officer",
    details: {},
    noteExcerpt: null,
    ...overrides,
  };
}

function lineOf(overrides: Partial<PersonTimelineEntry>) {
  return timelineLines([entry(overrides)], THIS)[0];
}

describe("timelineLines", () => {
  it("speaks of the drawer's own position as this position, and names any other", () => {
    expect(lineOf({ kind: "ADDED_TO_POOL", details: { door: "MANUAL" } })).toMatchObject({
      text: "added to this position",
      detail: "Added by hand",
    });
    expect(
      lineOf({ kind: "ADDED_TO_POOL", projectId: "other", projectTitle: "Head of Credit Risk" }).text,
    ).toBe("added to the candidates from Head of Credit Risk");
  });

  it("says a mapping found someone already known rather than duplicating them", () => {
    expect(lineOf({ kind: "MAPPED", details: { door: "EXTENSION" } })).toMatchObject({
      text: "added to this position",
      detail: "Captured with the plugin · Already in your candidates, so added rather than duplicated",
    });
  });

  it("reads a status move in the grid's own labels", () => {
    expect(
      lineOf({ kind: "STATUS_CHANGED", details: { from: "contacted", to: "engaged" } }),
    ).toMatchObject({ text: "marked Engaged on this position", detail: "Contacted → Engaged" });
  });

  it("phrases each kind of note by what it records, with its opening words", () => {
    expect(
      lineOf({ kind: "NOTE_ADDED", details: { kind: "CALL" }, noteExcerpt: "Open to a move." }),
    ).toMatchObject({ text: "logged a call about this position", detail: "Open to a move." });
    expect(lineOf({ kind: "NOTE_REMOVED", details: { kind: "GENERAL" } })).toMatchObject({
      text: "deleted a note about this position",
      detail: null,
    });
  });

  it("tells a contact found from a lookup that found nothing", () => {
    expect(
      lineOf({ kind: "CONTACT_FOUND", details: { channel: "EMAIL", found: "1", via: "CONTACTOUT" } })
        .text,
    ).toBe("found an email through ContactOut");
    expect(
      lineOf({ kind: "CONTACT_FOUND", details: { channel: "PHONE", found: "0" } }),
    ).toMatchObject({ text: "looked for a phone number", detail: "None found" });
  });

  it("names an actor nobody can be found for as someone", () => {
    expect(lineOf({ actorName: null }).actorName).toBe("Someone");
  });

  it("reads the team's own facts about a person: tags, owner and do not contact", () => {
    expect(lineOf({ kind: "TAGGED", projectId: null, details: { tagId: "t1", tag: "Open to work" } }).text).toBe(
      "tagged Open to work",
    );
    expect(lineOf({ kind: "UNTAGGED", projectId: null, details: { tag: "Passive" } }).text).toBe(
      "removed the tag Passive",
    );
    expect(lineOf({ kind: "OWNER_CHANGED", details: { ownerUserId: "u2", owner: "Sara" } }).text).toBe(
      "made Sara the owner",
    );
    expect(lineOf({ kind: "OWNER_CHANGED", details: {} }).text).toBe("cleared the owner");
    expect(lineOf({ kind: "DO_NOT_CONTACT_SET" }).text).toBe("marked do not contact");
    expect(lineOf({ kind: "OUTREACH_ENROLLED", details: { sequence: "CFO first approach" } }).text).toBe(
      "added to the sequence CFO first approach on this position",
    );
  });

  it("tells an outreach run as it went: each email, the reply, and why it stopped", () => {
    expect(lineOf({ kind: "EMAIL_SENT", details: { sequence: "CFO first approach", step: "2" } })).toMatchObject({
      actorName: "Sara Al-Mansour",
      text: "emailed on this position",
      detail: "CFO first approach, step 2",
    });
    expect(lineOf({ kind: "EMAIL_REPLIED", actorName: null, details: { sequence: "CFO first approach" } }))
      .toMatchObject({ actorName: "Fatima Al Mazrouei", text: "replied to the sequence CFO first approach on this position" });
    expect(lineOf({ kind: "OUTREACH_STOPPED", actorName: null, details: { sequence: "CFO", reason: "BOUNCED" } }))
      .toMatchObject({ actorName: "Uncava", text: "stopped the sequence CFO on this position", detail: "The address bounced" });
    expect(lineOf({ kind: "OUTREACH_STOPPED", details: { sequence: "CFO", reason: "MANUAL" } })).toMatchObject({
      actorName: "Sara Al-Mansour",
      detail: null,
    });
  });

  it("names a document by its category, and by its title only while it exists", () => {
    expect(
      lineOf({ kind: "DOCUMENT_ADDED", details: { category: "CV", document: "Jane Doe CV", version: "1" } }),
    ).toMatchObject({ text: "uploaded a CV", detail: "Jane Doe CV" });
    expect(lineOf({ kind: "DOCUMENT_VERSION_ADDED", details: { category: "COVER_LETTER", version: "3" } })).toMatchObject(
      { text: "uploaded version 3 of the cover letter", detail: null },
    );
    expect(lineOf({ kind: "DOCUMENT_REMOVED", details: { category: "REFERENCE" } }).text).toBe(
      "deleted a reference",
    );
  });

  it("names whom a line is about in the workspace feed", () => {
    const feed = (overrides: Partial<PersonTimelineEntry>) =>
      timelineLines([entry(overrides)], null, { withPerson: true })[0].text;
    expect(feed({ kind: "MAPPED", projectTitle: "Head of Credit Risk" })).toBe(
      "added Fatima Al Mazrouei to Head of Credit Risk",
    );
    expect(feed({ kind: "STATUS_CHANGED", details: { to: "engaged" } })).toBe(
      "marked Fatima Al Mazrouei Engaged on Chief Financial Officer",
    );
    expect(feed({ kind: "UNTAGGED", details: { tag: "Passive" } })).toBe("removed Passive from Fatima Al Mazrouei");
    expect(feed({ kind: "DO_NOT_CONTACT_CLEARED" })).toBe("cleared do not contact on Fatima Al Mazrouei");
  });

  it("reads a grid's Last activity without its actor, sentence-cased, and who did it when", () => {
    const now = new Date("2026-09-30T18:00:00Z");
    expect(lastActivityOf(entry({ kind: "STATUS_CHANGED", details: { to: "engaged" } }), now)).toEqual({
      text: "Marked Engaged on Chief Financial Officer",
      meta: expect.stringMatching(/^Sara Al-Mansour · Today \d{2}:\d{2}$/),
    });
  });
});
