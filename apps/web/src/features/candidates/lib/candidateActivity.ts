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

/** Why a sequence ended short, as its `reason` detail names it; a consultant's own Stop needs no reason. */
const STOP_REASONS: Record<string, string> = {
  DO_NOT_CONTACT: "Marked do not contact",
  LEFT_THE_RUNNING: "No longer in the running",
  UNMAPPED: "Removed from the position",
  ADDRESS_REMOVED: "The address was removed",
  MAILBOX_INACTIVE: "The sender's mailbox was disconnected",
  MAILBOX_MOVED: "The sender reconnected their mailbox",
  BOOKING_LINK_UNAVAILABLE: "The sender's booking link had no page behind it",
  SEND_FAILED: "The mail service refused the email",
  SEND_UNCERTAIN: "A send may not have gone through, so nothing more was sent",
  BOUNCED: "The address bounced",
  BOOKED: "A call was booked",
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
  { withPerson = false }: { withPerson?: boolean } = {},
): TimelineLine[] {
  return entries.map((entry) => {
    const person = withPerson ? (entry.personName ?? "someone") : null;
    const { text, detail } = phraseOf(entry, currentProjectId, person);
    return {
      key: entry.id,
      actorName: actorNameOf(entry),
      text,
      detail,
      occurredAt: entry.occurredAt,
    };
  });
}

/**
 * {@code person} is the subject's name in the workspace feed, where a line has to say whom it is
 * about; in a person's own drawer it is null and the line leaves them out.
 */
function phraseOf(
  entry: PersonTimelineEntry,
  currentProjectId: string | null,
  person: string | null,
): { text: string; detail: string | null } {
  const here = entry.projectId !== null && entry.projectId === currentProjectId;
  const where = here ? "this position" : (entry.projectTitle ?? "a position");
  const { details } = entry;
  const door = details.door ? (DOORS[details.door] ?? null) : null;
  const them = person ? ` ${person}` : "";
  const onThem = person ? ` on ${person}` : "";
  const forThem = person ? ` for ${person}` : "";

  switch (entry.kind) {
    case "ADDED_TO_POOL":
      return {
        text: here ? `added${them} to this position` : `added${them} to the candidates from ${where}`,
        detail: door,
      };
    case "MAPPED":
      return {
        text: `added${them} to ${where}`,
        detail: [door, "Already in your candidates, so added rather than duplicated"]
          .filter(Boolean)
          .join(" · "),
      };
    case "UNMAPPED":
      return { text: `removed${them} from ${where}`, detail: null };
    case "STATUS_CHANGED":
      return {
        text: `marked${them} ${statusLabel(details.to)} on ${where}`,
        detail: details.from ? `${statusLabel(details.from)} → ${statusLabel(details.to)}` : null,
      };
    case "PROFILE_EDITED":
      return {
        text: person ? `edited${them}` : "edited the profile",
        detail: details.backgroundConfirmed === "true" ? "Background confirmed" : null,
      };
    case "CONTACTS_EDITED":
      return { text: person ? `edited the contacts of ${person}` : "edited the contacts", detail: null };
    case "CONTACT_FOUND": {
      const what = details.channel === "PHONE" ? "a phone number" : "an email";
      const found = Number(details.found ?? "0");
      return found > 0
        ? { text: `found ${what}${forThem} through ${vendorOf(details.via) ?? "ContactOut"}`, detail: null }
        : { text: `looked for ${what}${forThem}`, detail: "None found" };
    }
    case "RESEARCHED":
      return {
        text: person ? `researched ${person}` : "researched the profile",
        detail: vendorOf(details.vendor) ? `Through ${vendorOf(details.vendor)}` : null,
      };
    case "AI_ASSESSED":
      return { text: `ran AI deep enrich${onThem}`, detail: here ? "Scored against this brief" : null };
    case "NOTE_ADDED":
      return {
        text: `${NOTE_VERBS[details.kind ?? "GENERAL"] ?? NOTE_VERBS.GENERAL}${onThem}${aboutOf(entry, here)}`,
        detail: entry.noteExcerpt,
      };
    case "NOTE_EDITED":
      return { text: `edited a note${onThem}${aboutOf(entry, here)}`, detail: entry.noteExcerpt };
    case "NOTE_REMOVED":
      return { text: `deleted a note${onThem}${aboutOf(entry, here)}`, detail: null };
    case "TAGGED":
      return { text: `tagged${them} ${details.tag ?? "with a tag"}`, detail: null };
    case "UNTAGGED":
      return {
        text: person ? `removed ${details.tag ?? "a tag"} from ${person}` : `removed the tag ${details.tag ?? ""}`.trim(),
        detail: null,
      };
    case "OWNER_CHANGED":
      return details.ownerUserId
        ? { text: `made ${details.owner ?? "someone"} the owner${person ? ` of ${person}` : ""}`, detail: null }
        : { text: `cleared the owner${person ? ` of ${person}` : ""}`, detail: null };
    case "DO_NOT_CONTACT_SET":
      return { text: `marked${them} do not contact`, detail: null };
    case "DO_NOT_CONTACT_CLEARED":
      return { text: `cleared do not contact${onThem}`, detail: null };
    case "OUTREACH_ENROLLED":
      return {
        text: `added${them} to ${details.sequence ? `the sequence ${details.sequence}` : "a sequence"} on ${where}`,
        detail: null,
      };
    case "EMAIL_SENT":
      return {
        text: `emailed${them} on ${where}`,
        detail: [details.sequence, details.step ? `step ${details.step}` : null].filter(Boolean).join(", ") || null,
      };
    case "EMAIL_REPLIED":
      return { text: `replied to ${details.sequence ? `the sequence ${details.sequence}` : "a sequence"} on ${where}`, detail: null };
    case "OUTREACH_STOPPED":
      return {
        text: `stopped ${details.sequence ? `the sequence ${details.sequence}` : "a sequence"}${forThem} on ${where}`,
        detail: details.reason ? (STOP_REASONS[details.reason] ?? null) : null,
      };
    case "MEETING_BOOKED":
      return {
        text: isBookedThroughLink(entry)
          ? `booked a call through the booking link on ${where}`
          : `booked a call${forThem} on ${where}`,
        detail: details.startsAt ? callTimeOf(details.startsAt) : null,
      };
    case "DOCUMENT_ADDED":
      return { text: `uploaded ${documentOf(details.category, "a")}${onThem}`, detail: details.document ?? null };
    case "DOCUMENT_VERSION_ADDED":
      return {
        text: `uploaded version ${details.version ?? "?"} of ${documentOf(details.category, "the")}${onThem}`,
        detail: details.document ?? null,
      };
    case "DOCUMENT_VERSION_REMOVED":
      return {
        text: `deleted version ${details.version ?? "?"} of ${documentOf(details.category, "the")}${onThem}`,
        detail: details.document ?? null,
      };
    case "DOCUMENT_REMOVED":
      return { text: `deleted ${documentOf(details.category, "a")}${onThem}`, detail: null };
  }
}

function isBookedThroughLink(entry: PersonTimelineEntry): boolean {
  return entry.kind === "MEETING_BOOKED" && String(entry.details.viaLink) === "true";
}

/** "Thu 2 Oct, 14:00", in the viewer's zone. */
function callTimeOf(isoInstant: string): string | null {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return null;
  const day = at.toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" }).replace(",", "");
  return `${day}, ${at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`;
}

/** A reply, or a call booked through the link, is the executive's own act; a stop nobody pressed is Uncava's. */
function actorNameOf(entry: PersonTimelineEntry): string {
  if (entry.kind === "EMAIL_REPLIED" || isBookedThroughLink(entry)) return entry.personName ?? "They";
  if (entry.kind === "OUTREACH_STOPPED" && entry.actorName === null) return "Uncava";
  return entry.actorName ?? "Someone";
}

const DOCUMENT_NOUNS: Record<string, string> = {
  CV: "CV",
  COVER_LETTER: "cover letter",
  REFERENCE: "reference",
  CERTIFICATE: "certificate",
  ASSESSMENT: "assessment",
  OTHER: "document",
};

/** The line names the category; the document's own title is the detail, and only while it exists. */
function documentOf(category: string | undefined, article: "a" | "the"): string {
  const noun = DOCUMENT_NOUNS[category ?? "OTHER"] ?? "document";
  return `${article === "a" && /^[aeiou]/.test(noun) ? "an" : article} ${noun}`;
}

/**
 * A person's latest line as the Candidates grid's Last activity cell reads it: what was done, without
 * the actor, sentence-cased; and who did it, when — "Today 10:12" or "22 Sep".
 */
export function lastActivityOf(entry: PersonTimelineEntry, now: Date = new Date()): { text: string; meta: string } {
  const { text } = phraseOf(entry, null, null);
  return {
    text: text.charAt(0).toUpperCase() + text.slice(1),
    meta: `${entry.actorName ?? "Someone"} · ${shortWhen(entry.occurredAt, now)}`,
  };
}

export function shortWhen(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  if (at.toDateString() === now.toDateString()) {
    return `Today ${at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`;
  }
  return at.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    ...(at.getFullYear() === now.getFullYear() ? {} : { year: "numeric" }),
  });
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
