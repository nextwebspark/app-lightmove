import type { CandidateStatus, PersonTimelineEntry } from "../api/types";
import { candidateStatusStyle } from "./candidateVocabulary";

/**
 * A person's history as sentences: what the actor did, and a second line of detail. Worded as the
 * Position and Candidates mockups word it, and from the viewer's side — a line about the position the
 * drawer was opened from says "this position", any other names it.
 */
export interface TimelineLine {
  key: number;
  actorName: string;
  text: string;
  detail: string | null;
  occurredAt: string;
}

/** The door a line's `door` detail names, as the server writes it (an enum name). */
const DOORS: Record<string, string> = {
  MANUAL: "Added by hand",
  CSV: "Imported from a spreadsheet",
  EXTENSION: "Captured with the plugin",
  AI_SOURCED: "Found by Find executives",
  PEOPLE_SEARCH: "Filed from a people search",
};

const VENDORS: Record<string, string> = {
  BRIGHTDATA: "Bright Data",
  CONTACTOUT: "ContactOut",
  HARVESTAPI: "HarvestAPI",
};

const NOTE_VERBS: Record<string, string> = {
  GENERAL: "wrote a note",
  CALL: "logged a call",
  MEETING: "logged a meeting",
  EMAIL: "logged an email",
};

export function timelineLines(
  entries: readonly PersonTimelineEntry[],
  currentProjectId: string | null,
): TimelineLine[] {
  return entries.map((entry) => {
    const { text, detail } = phraseOf(entry, currentProjectId);
    return {
      key: entry.id,
      actorName: entry.actorName ?? "Someone",
      text,
      detail,
      occurredAt: entry.occurredAt,
    };
  });
}

function phraseOf(
  entry: PersonTimelineEntry,
  currentProjectId: string | null,
): { text: string; detail: string | null } {
  const here = entry.projectId !== null && entry.projectId === currentProjectId;
  const where = here ? "this position" : (entry.projectTitle ?? "a position");
  const { details } = entry;
  const door = details.door ? (DOORS[details.door] ?? null) : null;

  switch (entry.kind) {
    case "ADDED_TO_POOL":
      return {
        text: here ? "added to this position" : `added to the candidates from ${where}`,
        detail: door,
      };
    case "MAPPED":
      return {
        text: `added to ${where}`,
        detail: [door, "Already in your candidates, so added rather than duplicated"]
          .filter(Boolean)
          .join(" · "),
      };
    case "UNMAPPED":
      return { text: `removed from ${where}`, detail: null };
    case "STATUS_CHANGED":
      return {
        text: `marked ${statusLabel(details.to)} on ${where}`,
        detail: details.from ? `${statusLabel(details.from)} → ${statusLabel(details.to)}` : null,
      };
    case "PROFILE_EDITED":
      return {
        text: "edited the profile",
        detail: details.backgroundConfirmed === "true" ? "Background confirmed" : null,
      };
    case "CONTACTS_EDITED":
      return { text: "edited the contacts", detail: null };
    case "CONTACT_FOUND": {
      const what = details.channel === "PHONE" ? "a phone number" : "an email";
      const found = Number(details.found ?? "0");
      return found > 0
        ? { text: `found ${what} through ${vendorOf(details.via) ?? "ContactOut"}`, detail: null }
        : { text: `looked for ${what}`, detail: "None found" };
    }
    case "RESEARCHED":
      return {
        text: "researched the profile",
        detail: vendorOf(details.vendor) ? `Through ${vendorOf(details.vendor)}` : null,
      };
    case "AI_ASSESSED":
      return { text: "ran AI deep enrich", detail: here ? "Scored against this brief" : null };
    case "NOTE_ADDED":
      return {
        text: `${NOTE_VERBS[details.kind ?? "GENERAL"] ?? NOTE_VERBS.GENERAL}${aboutOf(entry, here)}`,
        detail: entry.noteExcerpt,
      };
    case "NOTE_EDITED":
      return { text: `edited a note${aboutOf(entry, here)}`, detail: entry.noteExcerpt };
    case "NOTE_REMOVED":
      return { text: `deleted a note${aboutOf(entry, here)}`, detail: null };
  }
}

function aboutOf(entry: PersonTimelineEntry, here: boolean): string {
  if (entry.projectId === null && entry.projectTitle === null) return "";
  return here ? " about this position" : ` about ${entry.projectTitle ?? "a position"}`;
}

function statusLabel(value: string | undefined): string {
  return value ? candidateStatusStyle(value as CandidateStatus).label : "—";
}

function vendorOf(value: string | undefined): string | null {
  return value ? (VENDORS[value] ?? null) : null;
}
