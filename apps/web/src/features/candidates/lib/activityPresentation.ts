import type { ReactNode } from "react";
import { ICONS } from "../../../components/layout/Icon";
import type { FieldChange, PersonActivityKind, PersonTimelineEntry } from "../api/types";
import { DOCUMENT_NOUNS, DOORS, NOTE_VERBS, statusLabel, triageStageLabel } from "./candidateActivity";

/**
 * The executive drawer's Activity tab, worked out in data: which kind of event a line is, how it is drawn, who did it
 * and what its card previews. Pure, so every kind of line is tested without a screen.
 */

export type ActivityFamily =
  | "notes"
  | "emails"
  | "meetings"
  | "pipeline"
  | "tasks"
  | "contact"
  | "documents"
  | "profile";

export interface ActivityFamilyOption {
  family: ActivityFamily;
  label: string;
  icon: string;
}

/** The "What happened" menu, in its order: the conversation first, then the work, then the record. */
export const ACTIVITY_FAMILIES: readonly ActivityFamilyOption[] = [
  { family: "notes", label: "Notes", icon: ICONS.fileText },
  { family: "emails", label: "Emails", icon: ICONS.mail },
  { family: "meetings", label: "Meetings", icon: ICONS.calendar },
  { family: "pipeline", label: "Pipeline", icon: ICONS.pipeline },
  { family: "tasks", label: "Tasks", icon: ICONS.checkCircle },
  { family: "contact", label: "Contact", icon: ICONS.phoneCall },
  { family: "documents", label: "Documents", icon: ICONS.file },
  { family: "profile", label: "Profile & AI", icon: ICONS.sparkle },
];

const FAMILY_OF: Record<PersonActivityKind, ActivityFamily> = {
  NOTE_ADDED: "notes",
  NOTE_EDITED: "notes",
  NOTE_REMOVED: "notes",
  NOTE_RESTORED: "notes",
  EMAIL_SENT: "emails",
  EMAIL_REPLIED: "emails",
  OUTREACH_ENROLLED: "emails",
  OUTREACH_STOPPED: "emails",
  MEETING_BOOKED: "meetings",
  MEETING_RESCHEDULED: "meetings",
  MEETING_CANCELLED: "meetings",
  ADDED_TO_POOL: "pipeline",
  MAPPED: "pipeline",
  UNMAPPED: "pipeline",
  STATUS_CHANGED: "pipeline",
  TRIAGE_STATUS_CHANGED: "pipeline",
  CLIENT_VISIBILITY_CHANGED: "pipeline",
  NEXT_ACTION_SET: "tasks",
  TASK_CREATED: "tasks",
  TASK_COMPLETED: "tasks",
  TASK_REOPENED: "tasks",
  TASK_REASSIGNED: "tasks",
  TASK_CANCELLED: "tasks",
  CONTACTS_EDITED: "contact",
  CONTACT_FOUND: "contact",
  DO_NOT_CONTACT_SET: "contact",
  DO_NOT_CONTACT_CLEARED: "contact",
  DOCUMENT_ADDED: "documents",
  DOCUMENT_VERSION_ADDED: "documents",
  DOCUMENT_REMOVED: "documents",
  DOCUMENT_VERSION_REMOVED: "documents",
  PROFILE_EDITED: "profile",
  RESEARCHED: "profile",
  AI_ASSESSED: "profile",
  ASSESSMENT_REVIEWED: "profile",
  UPDATED_FROM_CALL: "profile",
  TAGGED: "profile",
  UNTAGGED: "profile",
  OWNER_CHANGED: "profile",
};

/** A kind a newer server writes still has to draw: it lands with the record, quietly. */
export function familyOf(entry: PersonTimelineEntry): ActivityFamily {
  return FAMILY_OF[entry.kind] ?? "profile";
}

/** The slim rows: changes worth knowing about that carry nothing to read. Everything else is a card with a preview. */
const MINOR_KINDS: ReadonlySet<string> = new Set<PersonActivityKind>([
  "TAGGED",
  "UNTAGGED",
  "OWNER_CHANGED",
  "CONTACTS_EDITED",
  "CONTACT_FOUND",
  "RESEARCHED",
  "OUTREACH_ENROLLED",
  "OUTREACH_STOPPED",
  "CLIENT_VISIBILITY_CHANGED",
  "TRIAGE_STATUS_CHANGED",
  "TASK_COMPLETED",
  "TASK_REOPENED",
  "TASK_CANCELLED",
  "TASK_REASSIGNED",
  "NOTE_REMOVED",
  "DOCUMENT_REMOVED",
  "DOCUMENT_VERSION_REMOVED",
  "ASSESSMENT_REVIEWED",
]);

export function isMinor(entry: PersonTimelineEntry): boolean {
  if (entry.kind === "AI_ASSESSED") return entry.details.scope === "BACKGROUND";
  return MINOR_KINDS.has(entry.kind) || !(entry.kind in FAMILY_OF);
}

/** Where the line starts this position's story: drawn as the history's first card. */
export function isOrigin(entry: PersonTimelineEntry): boolean {
  return entry.kind === "ADDED_TO_POOL" || entry.kind === "MAPPED";
}

export function iconOf(entry: PersonTimelineEntry): string {
  switch (entry.kind) {
    case "NOTE_ADDED":
    case "NOTE_EDITED":
    case "NOTE_RESTORED":
      return entry.details.kind === "CALL"
        ? ICONS.phoneCall
        : entry.details.kind === "MEETING"
          ? ICONS.video
          : entry.details.kind === "EMAIL"
            ? ICONS.mail
            : ICONS.fileText;
    case "NOTE_REMOVED":
      return ICONS.trash;
    case "EMAIL_SENT":
      return entry.details.sequenceId ? ICONS.outreach : ICONS.mail;
    case "EMAIL_REPLIED":
      return ICONS.arrowLeft;
    case "OUTREACH_ENROLLED":
      return ICONS.outreach;
    case "OUTREACH_STOPPED":
      return ICONS.ban;
    case "MEETING_BOOKED":
      return ICONS.calendar;
    case "MEETING_RESCHEDULED":
      return ICONS.reschedule;
    case "MEETING_CANCELLED":
      return ICONS.x;
    case "ADDED_TO_POOL":
    case "MAPPED":
      return ICONS.userPlus;
    case "UNMAPPED":
      return ICONS.userX;
    case "STATUS_CHANGED":
      return ICONS.pipeline;
    case "TRIAGE_STATUS_CHANGED":
      return ICONS.triage;
    case "CLIENT_VISIBILITY_CHANGED":
      return String(entry.details.visible) === "true" ? ICONS.eye : ICONS.eyeOff;
    case "CONTACTS_EDITED":
      return ICONS.mail;
    case "CONTACT_FOUND":
      return Number(entry.details.found ?? "0") > 0 ? ICONS.check : ICONS.searchX;
    case "DO_NOT_CONTACT_SET":
    case "DO_NOT_CONTACT_CLEARED":
      return ICONS.ban;
    case "DOCUMENT_ADDED":
    case "DOCUMENT_VERSION_ADDED":
      return ICONS.file;
    case "DOCUMENT_REMOVED":
    case "DOCUMENT_VERSION_REMOVED":
      return ICONS.trash;
    case "PROFILE_EDITED":
      return ICONS.pencil;
    case "RESEARCHED":
      return ICONS.search;
    case "AI_ASSESSED":
      return ICONS.sparkle;
    case "ASSESSMENT_REVIEWED":
      return ICONS.clipboard;
    case "UPDATED_FROM_CALL":
      return ICONS.mic;
    case "TAGGED":
    case "UNTAGGED":
      return ICONS.tag;
    case "OWNER_CHANGED":
      return ICONS.profile;
    case "NEXT_ACTION_SET":
    case "TASK_CREATED":
    case "TASK_REOPENED":
    case "TASK_REASSIGNED":
      return ICONS.checklist;
    case "TASK_COMPLETED":
      return ICONS.checkCircle;
    case "TASK_CANCELLED":
      return ICONS.x;
  }
  return ICONS.activity;
}

/** Who a line is about doing it: a colleague, the executive themselves, or Uncava acting on its own. */
export type ActivityActor =
  | { kind: "user"; id: string; name: string; avatarUrl: string | null }
  | { kind: "executive" }
  | { kind: "automation"; triggeredBy: string | null }
  | { kind: "unknown" };

/** What set off a status the automation moved, in the words the line says it with. */
const AUTOMATION_CAUSES: Record<string, string> = {
  EMAIL_SENT: "email",
  REPLIED: "reply",
  CALL_BOOKED: "booked call",
};

export function actorOf(entry: PersonTimelineEntry): ActivityActor {
  if (entry.kind === "EMAIL_REPLIED") return { kind: "executive" };
  if (entry.kind === "MEETING_BOOKED" && String(entry.details.viaLink) === "true") return { kind: "executive" };
  if (entry.kind === "STATUS_CHANGED" && entry.details.automation) {
    return { kind: "automation", triggeredBy: entry.actorName };
  }
  if (entry.actorUserId && entry.actorName) {
    return { kind: "user", id: entry.actorUserId, name: entry.actorName, avatarUrl: entry.actorAvatarUrl };
  }
  // A line nobody's request stands behind: Uncava's own scan, a bounce, an automatic background read.
  if (entry.actorUserId == null) return { kind: "automation", triggeredBy: null };
  return { kind: "unknown" };
}

/** "after Dana Aboud's email"; "after the reply" — or nothing when no rule moved it. */
export function automationCauseOf(entry: PersonTimelineEntry): string | null {
  if (entry.kind !== "STATUS_CHANGED" || !entry.details.automation) return null;
  const cause = AUTOMATION_CAUSES[entry.details.automation] ?? "event";
  if (entry.details.automation === "REPLIED") return "after the reply";
  return entry.actorName ? `after ${entry.actorName}'s ${cause}` : `after the ${cause}`;
}

function documentNoun(category: string | undefined): string {
  return DOCUMENT_NOUNS[category ?? "OTHER"] ?? "document";
}

function withArticle(noun: string): string {
  return `${/^[aeiou]/i.test(noun) ? "an" : "a"} ${noun}`;
}

/**
 * What the actor did, as the card's first line says it after their name. A line never adds "on this position"; a
 * position's own milestone names it, which reads the same on the position and on the Candidates page.
 */
export function verbOf(entry: PersonTimelineEntry): string {
  const { details } = entry;
  switch (entry.kind) {
    case "ADDED_TO_POOL":
    case "MAPPED":
      return entry.projectId === null ? "added them to your candidates" : `added them to ${entry.projectTitle ?? "this position"}`;
    case "UNMAPPED":
      return `removed them from ${entry.projectTitle ?? "this position"}`;
    case "STATUS_CHANGED":
      return "moved the status";
    case "TRIAGE_STATUS_CHANGED":
      return `moved them to ${triageStageLabel(details.to)}`;
    case "CLIENT_VISIBILITY_CHANGED":
      return String(details.visible) === "true" ? "made them visible to the client" : "hid them from the client";
    case "NEXT_ACTION_SET":
      return String(details.cleared) === "true" ? "cleared the next action" : "set the next action";
    case "TASK_CREATED":
      return "created a task";
    case "TASK_COMPLETED":
      return "completed a task";
    case "TASK_REOPENED":
      return "reopened a task";
    case "TASK_REASSIGNED":
      return "handed a task over";
    case "TASK_CANCELLED":
      return "cancelled a task";
    case "PROFILE_EDITED":
      return "edited the profile";
    case "UPDATED_FROM_CALL":
      return String(details.reverted) === "true" ? "undid changes from a call" : "applied changes from a call";
    case "CONTACTS_EDITED":
      return "edited the contacts";
    case "CONTACT_FOUND": {
      const what = details.channel === "PHONE" ? "a phone number" : "an email";
      return Number(details.found ?? "0") > 0 ? `found ${what}` : `looked for ${what}`;
    }
    case "RESEARCHED":
      return "researched the profile";
    case "AI_ASSESSED":
      return details.scope === "BACKGROUND" ? "had the AI suggest the background" : "had the AI assess them";
    case "ASSESSMENT_REVIEWED":
      return "reviewed the assessment";
    case "NOTE_ADDED":
      return NOTE_VERBS[details.kind ?? "GENERAL"] ?? NOTE_VERBS.GENERAL;
    case "NOTE_EDITED":
      return "edited a note";
    case "NOTE_REMOVED":
      return "deleted a note";
    case "NOTE_RESTORED":
      return "restored a note";
    case "TAGGED":
      return "tagged them";
    case "UNTAGGED":
      return "removed a tag";
    case "OWNER_CHANGED":
      return details.ownerUserId ? `made ${details.owner ?? "someone"} the owner` : "cleared the owner";
    case "DO_NOT_CONTACT_SET":
      return "marked them do not contact";
    case "DO_NOT_CONTACT_CLEARED":
      return "cleared do not contact";
    case "OUTREACH_ENROLLED":
      return "added them to a sequence";
    case "EMAIL_SENT":
      return details.sequenceId ? "emailed them" : "sent an email";
    case "EMAIL_REPLIED":
      return details.sequence ? "replied to the sequence" : "replied";
    case "OUTREACH_STOPPED":
      return "stopped the sequence";
    case "MEETING_BOOKED":
      return String(details.viaLink) === "true" ? "booked a call through the booking link" : "booked a call";
    case "MEETING_RESCHEDULED":
      return "moved a call";
    case "MEETING_CANCELLED":
      return "cancelled a call";
    case "DOCUMENT_ADDED":
      return `uploaded ${withArticle(documentNoun(details.category))}`;
    case "DOCUMENT_VERSION_ADDED":
      return `uploaded version ${details.version ?? "?"} of the ${documentNoun(details.category)}`;
    case "DOCUMENT_REMOVED":
      return `deleted ${withArticle(documentNoun(details.category))}`;
    case "DOCUMENT_VERSION_REMOVED":
      return `deleted version ${details.version ?? "?"} of the ${documentNoun(details.category)}`;
  }
  return "updated the record";
}

/** A short aside the slim row adds after the verb: the tag, the sequence, a miss. */
export function asideOf(entry: PersonTimelineEntry): string | null {
  const { details } = entry;
  switch (entry.kind) {
    case "TAGGED":
    case "UNTAGGED":
      return details.tag ?? null;
    case "OUTREACH_ENROLLED":
    case "OUTREACH_STOPPED":
      return details.sequence ?? null;
    case "CONTACT_FOUND":
      return Number(details.found ?? "0") > 0 ? null : "none found";
    case "CONTACTS_EDITED":
      return contactCountsOf(details);
    case "TASK_COMPLETED":
    case "TASK_REOPENED":
    case "TASK_CANCELLED":
      return details.title ?? null;
    case "TASK_REASSIGNED":
      return details.assignee ? `to ${details.assignee}` : null;
    case "DOCUMENT_REMOVED":
    case "DOCUMENT_VERSION_REMOVED":
      return details.document ?? null;
  }
  return null;
}

function contactCountsOf(details: Record<string, string>): string | null {
  const emails = Number(details.emails);
  const phones = Number(details.phones);
  if (Number.isNaN(emails) || Number.isNaN(phones) || (details.emails == null && details.phones == null)) return null;
  const plural = (count: number, noun: string) => `${count} ${noun}${count === 1 ? "" : "s"}`;
  return `${plural(emails, "email")}, ${plural(phones, "phone")} on file`;
}

/** A note's last edit, which leaves no line of its own: the note's one line says it. */
export interface NoteEdit {
  at: string;
  byName: string | null;
}

/** What a card shows under its first line; the dialog then shows the rest. */
export type ActivityPreview =
  | { type: "note"; excerpt: string | null; noteKind: string; aboutThePerson: boolean; edited: NoteEdit | null }
  | { type: "status"; from: string | null; to: string; cause: string | null }
  | { type: "changes"; changes: FieldChange[]; hidden: number }
  | { type: "fields"; labels: string[] }
  | { type: "meeting"; startsAt: string; previousStartsAt: string | null; cancelled: boolean }
  | { type: "email"; label: string; detail: string | null }
  | { type: "document"; title: string | null; noun: string; version: string | null }
  | { type: "task"; title: string; dueAt: string | null; assignee: string | null }
  | { type: "origin"; door: string | null; mapped: boolean }
  | { type: "assessment"; label: string };

/** How many changes a card lists before "+N more". */
export const CHANGES_SHOWN = 2;

export type TaskState = "open" | "done" | "cancelled";

const TASK_STATE_OF: Partial<Record<PersonActivityKind, TaskState>> = {
  TASK_CREATED: "open",
  TASK_REOPENED: "open",
  TASK_REASSIGNED: "open",
  TASK_COMPLETED: "done",
  TASK_CANCELLED: "cancelled",
};

/**
 * Where each task stands now, read off its newest line in a history sent newest first, so the card that created a
 * task shows it ticked once it is done. A task's lines are this position's, and the history is re-read on every task
 * write, so the answer is as fresh as the tab.
 */
export function taskStatesOf(entries: readonly PersonTimelineEntry[]): Map<string, TaskState> {
  const states = new Map<string, TaskState>();
  for (const entry of entries) {
    const state = TASK_STATE_OF[entry.kind];
    const taskId = entry.details.taskId;
    if (state && taskId && !states.has(taskId)) states.set(taskId, state);
  }
  return states;
}

export function previewOf(entry: PersonTimelineEntry): ActivityPreview | null {
  const { details } = entry;
  switch (entry.kind) {
    case "NOTE_ADDED":
    case "NOTE_EDITED":
    case "NOTE_RESTORED":
      return {
        type: "note",
        excerpt: entry.noteExcerpt,
        noteKind: details.kind ?? "GENERAL",
        aboutThePerson: entry.projectId === null,
        edited: entry.noteEditedAt ? { at: entry.noteEditedAt, byName: entry.noteEditedByName } : null,
      };
    case "STATUS_CHANGED":
      return {
        type: "status",
        from: details.from ? statusLabel(details.from, details.fromLabel) : null,
        to: statusLabel(details.to, details.toLabel),
        cause: automationCauseOf(entry),
      };
    case "PROFILE_EDITED":
    case "UPDATED_FROM_CALL": {
      const changes = entry.changes ?? [];
      if (changes.length > 0) {
        return { type: "changes", changes: changes.slice(0, CHANGES_SHOWN), hidden: Math.max(0, changes.length - CHANGES_SHOWN) };
      }
      const labels = (details.fields ?? "").split(",").filter(Boolean).map(fieldLabel);
      return labels.length > 0 ? { type: "fields", labels } : null;
    }
    case "MEETING_BOOKED":
    case "MEETING_RESCHEDULED":
    case "MEETING_CANCELLED":
      return details.startsAt
        ? {
            type: "meeting",
            startsAt: details.startsAt,
            previousStartsAt: details.previousStartsAt ?? null,
            cancelled: entry.kind === "MEETING_CANCELLED",
          }
        : null;
    case "EMAIL_SENT":
      return {
        type: "email",
        label: details.sequence ? `Sequence · ${details.sequence}` : "Written by hand",
        detail: details.step ? `Step ${details.step}` : null,
      };
    case "EMAIL_REPLIED":
      return {
        type: "email",
        label: details.sequence ? `Reply to ${details.sequence}` : "Reply",
        detail: null,
      };
    case "DOCUMENT_ADDED":
    case "DOCUMENT_VERSION_ADDED":
      return {
        type: "document",
        title: details.document ?? null,
        noun: documentNoun(details.category),
        version: details.version ?? null,
      };
    case "TASK_CREATED":
    case "NEXT_ACTION_SET":
      return details.title || details.text
        ? { type: "task", title: details.title ?? details.text, dueAt: details.dueAt ?? null, assignee: details.assignee ?? null }
        : null;
    case "ADDED_TO_POOL":
    case "MAPPED":
      return { type: "origin", door: doorOf(details.door), mapped: entry.kind === "MAPPED" };
    case "AI_ASSESSED":
      return details.scope === "BACKGROUND" ? null : { type: "assessment", label: "Scored against this brief" };
  }
  return null;
}

export function doorOf(door: string | undefined): string | null {
  return door ? (DOORS[door] ?? null) : null;
}

const FIELD_LABELS: Record<string, string> = {
  fullName: "Name",
  title: "Title",
  seniority: "Seniority",
  location: "Location",
  nationality: "Nationality",
  gender: "Gender",
  yearsExperience: "Years of experience",
  linkedinUrl: "LinkedIn",
  summary: "About",
  currency: "Currency",
  baseSalary: "Base salary",
  bonus: "Bonus",
  allowances: "Allowances",
  longTermIncentive: "Long-term incentive",
  noticePeriod: "Notice period",
  languages: "Languages",
  career: "Career history",
  motivation: "Motivation",
  expectedPackage: "Expected package",
  openToRelocate: "Open to relocate",
  task: "A task",
};

/** A field's name as the Career tab writes it; a key a newer server sends reads as words. */
export function fieldLabel(field: string): string {
  const known = FIELD_LABELS[field];
  if (known) return known;
  const words = field.replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Runs of quick minor changes by one person, folded; three or more within ten minutes make a fold. */
export type ActivityItem =
  | { type: "entry"; entry: PersonTimelineEntry }
  | { type: "fold"; key: string; entries: PersonTimelineEntry[] };

export const FOLD_MIN = 3;
const FOLD_WINDOW_MS = 10 * 60_000;

export function foldRuns(entries: readonly PersonTimelineEntry[]): ActivityItem[] {
  const items: ActivityItem[] = [];
  let index = 0;
  while (index < entries.length) {
    const first = entries[index];
    let end = index;
    if (isMinor(first)) {
      while (end + 1 < entries.length && foldsWith(entries[end], entries[end + 1], first)) end++;
    }
    if (end - index + 1 >= FOLD_MIN) {
      items.push({ type: "fold", key: `fold-${first.id}`, entries: entries.slice(index, end + 1) });
    } else {
      for (let each = index; each <= end; each++) items.push({ type: "entry", entry: entries[each] });
    }
    index = end + 1;
  }
  return items;
}

function foldsWith(previous: PersonTimelineEntry, next: PersonTimelineEntry, first: PersonTimelineEntry): boolean {
  if (!isMinor(next) || next.actorUserId !== first.actorUserId || next.actorUserId == null) return false;
  const gap = new Date(previous.occurredAt).getTime() - new Date(next.occurredAt).getTime();
  return !Number.isNaN(gap) && gap >= 0 && gap <= FOLD_WINDOW_MS;
}

/** The "Who did it" menu's people: one row per actor, colleagues first, then the executive, then Uncava. */
export interface ActivityPerson {
  key: string;
  actor: ActivityActor;
}

export function actorKeyOf(entry: PersonTimelineEntry): string {
  const actor = actorOf(entry);
  switch (actor.kind) {
    case "user":
      return `user:${actor.id}`;
    case "executive":
      return "executive";
    case "automation":
      return "automation";
    default:
      return "unknown";
  }
}

export function peopleOf(entries: readonly PersonTimelineEntry[]): ActivityPerson[] {
  const seen = new Map<string, ActivityActor>();
  for (const entry of entries) {
    const key = actorKeyOf(entry);
    if (!seen.has(key)) seen.set(key, actorOf(entry));
  }
  const rank = (actor: ActivityActor) =>
    actor.kind === "user" ? 0 : actor.kind === "executive" ? 1 : actor.kind === "automation" ? 2 : 3;
  return [...seen.entries()]
    .map(([key, actor]) => ({ key, actor }))
    .sort((left, right) => rank(left.actor) - rank(right.actor));
}

/** The person-wide timeline's third filter: each position a line was made on, and the person themselves. */
export interface ActivityPosition {
  key: string;
  label: string;
}

const ABOUT_THE_PERSON = "person";

export function positionKeyOf(entry: PersonTimelineEntry): string {
  return entry.projectId ?? ABOUT_THE_PERSON;
}

/** Positions in the order the history last touched them, newest first, then the person's own changes. */
export function positionsOf(entries: readonly PersonTimelineEntry[]): ActivityPosition[] {
  const seen = new Map<string, string>();
  for (const entry of entries) {
    if (entry.projectId && !seen.has(entry.projectId)) seen.set(entry.projectId, entry.projectTitle ?? "A position");
  }
  const positions = [...seen.entries()].map(([key, label]) => ({ key, label }));
  return entries.some((entry) => entry.projectId === null)
    ? [...positions, { key: ABOUT_THE_PERSON, label: "About the person" }]
    : positions;
}

export function familiesOf(entries: readonly PersonTimelineEntry[]): ActivityFamilyOption[] {
  const present = new Set(entries.map(familyOf));
  return ACTIVITY_FAMILIES.filter((option) => present.has(option.family));
}

/** "Thu 30 Oct" in the viewer's zone; null for a value that is not a time. */
export function shortDate(isoInstant: string | null | undefined): string | null {
  if (!isoInstant) return null;
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return null;
  return at.toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" }).replace(",", "");
}

/** "Thu 30 Oct, 15:00" in the viewer's zone; null for a value that is not a time. */
export function shortDateTime(isoInstant: string | null | undefined): string | null {
  const day = shortDate(isoInstant);
  if (!day || !isoInstant) return null;
  return `${day}, ${new Date(isoInstant).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`;
}

/** "Tue 27 Oct 2026, 14:20 GMT+4" — the full stamp a tooltip and a dialog give, zone and all. */
export function fullDateTime(isoInstant: string): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  return at
    .toLocaleString("en-GB", {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      timeZoneName: "short",
    })
    .replace(",", "");
}

/** The card's clock: "12 min ago" within the hour, the time of day otherwise — the day is the header's. */
export function cardTimeOf(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  const minutes = Math.floor((now.getTime() - at.getTime()) / 60_000);
  if (minutes >= 0 && minutes < 1) return "just now";
  if (minutes >= 1 && minutes < 60) return `${minutes} min ago`;
  return at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

/** The executive the drawer is about, as the history names them when they acted themselves. */
export interface ActivityExecutive {
  fullName: string;
  /** Their photo or initials, from whichever read the drawer holds: a position's candidate or the workspace's person. */
  face: (size: "sm" | "md" | "lg", className?: string) => ReactNode;
}

/** Whose history a timeline reads: one position's executive, or the person across every position. */
export type ActivityScope =
  | { kind: "position"; projectId: string; candidateId: string }
  | { kind: "person"; personId: string };

/** The actor's name as the line opens with it. */
export function actorNameOf(actor: ActivityActor, executive: ActivityExecutive): string {
  switch (actor.kind) {
    case "user":
      return actor.name;
    case "executive":
      return executive.fullName;
    case "automation":
      return "Automation";
    default:
      return "Someone";
  }
}

/** The second line under a name in a dialog's header, or a face's tooltip. */
export function actorRoleOf(actor: ActivityActor): string {
  switch (actor.kind) {
    case "user":
      return "Your team";
    case "executive":
      return "The executive";
    case "automation":
      return "Uncava, automatically";
    default:
      return "Nobody recorded who";
  }
}
