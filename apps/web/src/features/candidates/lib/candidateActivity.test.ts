import { describe, expect, it } from "vitest";
import type { PersonTimelineEntry } from "../api/types";
import { timelineLines } from "./candidateActivity";

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
});
