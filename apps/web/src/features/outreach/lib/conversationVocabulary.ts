import type { PersonNoteKind } from "../../candidates/api/types";
import { timelineLines } from "../../candidates/lib/candidateActivity";
import type { ConversationEvent, ConversationGroup, ConversationRow } from "../api/conversationApi";
import type { OutreachRun } from "../api/runApi";
import { isLive, STOP_NOTES } from "./runVocabulary";

/** The tone a mockup line is drawn in: `Outreach.dc.html`'s accent, green, red, signal and neutral. */
export type ConversationTone = "accent" | "green" | "red" | "signal" | "neutral";

export const TONE_TEXT: Record<ConversationTone, string> = {
  accent: "text-u-accent",
  green: "text-u-direct",
  red: "text-u-offlimits",
  signal: "text-u-signal",
  neutral: "text-u-text3",
};

export const TONE_DOT: Record<ConversationTone, string> = {
  accent: "bg-u-accent",
  green: "bg-u-direct",
  red: "bg-u-offlimits",
  signal: "bg-u-signal",
  neutral: "bg-u-text3",
};

/** What a To do row is waiting on, in the order the To do filter lists them. */
export const QUEUE_GROUPS: readonly { group: ConversationGroup; label: string; tone: ConversationTone }[] = [
  { group: "NEEDS_REPLY", label: "Needs reply", tone: "green" },
  { group: "FOLLOW_UP", label: "Follow-ups", tone: "signal" },
  { group: "PREPARATION", label: "Before first email", tone: "neutral" },
  { group: "ATTENTION", label: "Delivery problems", tone: "red" },
];

export function toneOfGroup(group: ConversationGroup | null): ConversationTone {
  return QUEUE_GROUPS.find((entry) => entry.group === group)?.tone ?? "neutral";
}

/**
 * The time chip on a queue row, as the mockup draws it: how long ago they wrote, when a follow-up is due (red once
 * it is late), PREP for work before a first email, or how long something has been wrong.
 */
export function queueTimeOf(row: ConversationRow, now: Date = new Date()): { text: string; isLate: boolean } {
  switch (row.group) {
    case "PREPARATION":
      return { text: "PREP", isLate: false };
    case "FOLLOW_UP": {
      const due = row.action?.dueAt ? new Date(row.action.dueAt) : null;
      if (!due) return { text: sinceOf(row.latest?.at ?? row.lastActivityAt, now), isLate: false };
      return { text: dueLabelOf(due, now), isLate: due.getTime() < now.getTime() };
    }
    case "ATTENTION":
      return { text: sinceOf(row.latest?.at ?? row.lastActivityAt, now), isLate: true };
    case "NEEDS_REPLY":
    case null:
      return { text: sinceOf(row.latest?.at ?? row.lastActivityAt, now), isLate: false };
  }
}

/** "NOW", "12m", "3h", "2D" — the queue's short since. */
export function sinceOf(isoInstant: string | null, now: Date = new Date()): string {
  if (!isoInstant) return "";
  const minutes = Math.floor((now.getTime() - new Date(isoInstant).getTime()) / 60_000);
  if (minutes < 1) return "NOW";
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.floor(hours / 24)}D`;
}

/** "11:00" for later today, "1D" once a day late, "07 OCT" further out. */
function dueLabelOf(due: Date, now: Date): string {
  if (due.getTime() < now.getTime()) {
    const days = Math.floor((now.getTime() - due.getTime()) / 86_400_000);
    return days < 1 ? clockOf(due) : `${days}D`;
  }
  return sameDay(due, now) ? clockOf(due) : shortDateOf(due);
}

/** "DUE TODAY · 18:00", "DUE 07 OCT · 17:00", "OVERDUE · 1 DAY" — the next-action strip's chip. */
export function dueChipOf(isoInstant: string | null, now: Date = new Date()): { text: string; isLate: boolean } | null {
  if (!isoInstant) return null;
  const due = new Date(isoInstant);
  if (due.getTime() < now.getTime()) {
    const days = Math.max(1, Math.floor((now.getTime() - due.getTime()) / 86_400_000));
    return { text: `OVERDUE · ${days} ${days === 1 ? "DAY" : "DAYS"}`, isLate: true };
  }
  const day = sameDay(due, now) ? "TODAY" : shortDateOf(due);
  return { text: `DUE ${day} · ${clockOf(due)}`, isLate: false };
}

export interface EventCardFace {
  tag: string;
  tone: ConversationTone;
  title: string;
  context: string | null;
}

const NOTE_TITLES: Record<PersonNoteKind, string> = {
  general: "Note",
  call: "Call note",
  meeting: "Meeting note",
  email: "Email note",
  linkedin: "LinkedIn note",
};

/** What a history card says about one event: its tag, its tone, its title and the line beneath, as the mockup draws them. */
export function eventFaceOf(event: ConversationEvent, firstName: string, projectId: string): EventCardFace {
  switch (event.type) {
    case "MESSAGE": {
      const message = event.message;
      const context =
        message.sequenceName && message.stepNumber
          ? `${message.sequenceName} · Step ${message.stepNumber} of ${message.stepCount ?? message.stepNumber}`
          : "One-off email";
      if (message.direction === "INBOUND") {
        return message.kind === "BOUNCE"
          ? { tag: "DELIVERY", tone: "red", title: "Email bounced", context: null }
          : { tag: "SYNCED EMAIL", tone: "green", title: `${firstName} replied`, context: null };
      }
      if (message.status === "FAILED") {
        const reason = message.failReason ? STOP_NOTES[message.failReason] : null;
        return { tag: "DELIVERY", tone: "red", title: "Email not sent", context: reason };
      }
      if (message.status === "SCHEDULED") {
        return { tag: "SCHEDULED", tone: "signal", title: message.subject ?? "Email", context };
      }
      return { tag: "SENT EMAIL", tone: "accent", title: message.subject ?? "Email", context };
    }
    case "NOTE": {
      const author = event.note.authorName ?? "a teammate";
      return {
        tag: "HUMAN NOTE",
        tone: "accent",
        title: `${NOTE_TITLES[event.note.kind]} added by ${author}`,
        context: "Private team note · not shared with client",
      };
    }
    case "MEETING":
      return {
        tag: "MEETING",
        tone: "green",
        title: event.meeting.title ?? "Meeting",
        context: event.meeting.viaLink ? "Booked through the booking link" : null,
      };
    case "ACTIVITY": {
      const [line] = timelineLines([event.activity], projectId);
      return { tag: "AUTOMATION", tone: "signal", title: `${line.actorName} ${line.text}`, context: line.detail };
    }
  }
}

/** The sentences under the composer, as the mockup writes them, for what pressing Send would do. */
export function composerFootnoteOf({
  isReply,
  resolvesReplyTask,
  statusLabel,
  movesTo,
  stopsRunningSequence,
}: {
  isReply: boolean;
  resolvesReplyTask: boolean;
  statusLabel: string;
  movesTo: string | null;
  stopsRunningSequence: boolean;
}): string {
  const sentences: string[] = [];
  if (resolvesReplyTask) sentences.push("Sending resolves this reply task.");
  if (stopsRunningSequence && isReply) sentences.push("Sending stops the running sequence.");
  sentences.push(movesTo ? `Moves to ${movesTo}.` : `Pipeline stage stays ${statusLabel}.`);
  if (!isReply) sentences.push("Nothing is sent until you press Send now or Schedule.");
  return sentences.join(" ");
}

/** "28 SEP — TODAY": the history's span, oldest to newest. */
export function historyRangeOf(events: readonly ConversationEvent[], now: Date = new Date()): string | null {
  const instants = events.map((event) => new Date(event.at)).filter((at) => !Number.isNaN(at.getTime()));
  if (instants.length === 0) return null;
  const oldest = new Date(Math.min(...instants.map((at) => at.getTime())));
  const newest = new Date(Math.max(...instants.map((at) => at.getTime())));
  const label = (at: Date) => (sameDay(at, now) ? "TODAY" : shortDateOf(at));
  const from = label(oldest);
  const to = label(newest);
  return from === to ? from : `${from} — ${to}`;
}

export interface GlanceFact {
  count: number;
  noun: string;
  tone: ConversationTone;
}

export interface HistoryGlance {
  facts: GlanceFact[];
  /** "2D ago", "just now"; null before anything has happened. */
  lastTouch: string | null;
  /** The sequence still going, as "Executive intro · 1 of 3 sent". */
  sequence: string | null;
}

/** The line over the history: what has passed between the team and the person so far, counted from what it shows. */
export function glanceOf(
  events: readonly ConversationEvent[],
  run: OutreachRun | null,
  now: Date = new Date(),
): HistoryGlance {
  let sent = 0;
  let scheduled = 0;
  let replies = 0;
  let bounces = 0;
  let meetings = 0;
  let calls = 0;
  let linkedin = 0;
  let notes = 0;
  let latest: { at: string; time: number } | null = null;
  for (const event of events) {
    if (event.type === "MESSAGE") {
      const { direction, kind, status } = event.message;
      if (direction === "INBOUND") {
        if (kind === "BOUNCE") bounces++;
        else replies++;
      } else if (status === "SENT") sent++;
      else if (status === "SCHEDULED") scheduled++;
    }
    if (event.type === "MEETING") meetings++;
    if (event.type === "NOTE") {
      if (event.note.kind === "call") calls++;
      else if (event.note.kind === "linkedin") linkedin++;
      else notes++;
    }
    const at = new Date(event.at).getTime();
    const isPast = !Number.isNaN(at) && at <= now.getTime();
    const isWaiting = event.type === "MESSAGE" && event.message.status === "SCHEDULED";
    if (isPast && !isWaiting && (latest === null || at > latest.time)) latest = { at: event.at, time: at };
  }
  const counted: GlanceFact[] = [
    { count: sent, noun: "sent", tone: "accent" },
    { count: replies, noun: replies === 1 ? "reply" : "replies", tone: "green" },
    { count: bounces, noun: bounces === 1 ? "bounce" : "bounces", tone: "red" },
    { count: scheduled, noun: "scheduled", tone: "signal" },
    { count: meetings, noun: meetings === 1 ? "meeting" : "meetings", tone: "green" },
    { count: calls, noun: calls === 1 ? "call" : "calls", tone: "accent" },
    { count: linkedin, noun: "LinkedIn", tone: "accent" },
    { count: notes, noun: notes === 1 ? "note" : "notes", tone: "neutral" },
  ];
  const since = latest === null ? null : sinceOf(latest.at, now);
  return {
    facts: counted.filter((fact) => fact.count > 0),
    lastTouch: since === null ? null : since === "NOW" ? "just now" : `${since} ago`,
    sequence:
      run && isLive(run) ? `${run.sequenceName ?? "Sequence"} · ${run.sentCount} of ${run.stepCount} sent` : null,
  };
}

/** "TODAY · 09:18" or "28 SEP · 14:02", a card's own time. */
export function cardTimeOf(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "";
  return `${sameDay(at, now) ? "TODAY" : shortDateOf(at)} · ${clockOf(at)}`;
}

/** "05 OCT 2026" — a key date. */
export function keyDateOf(isoInstant: string | null): string {
  if (!isoInstant) return "—";
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  return `${shortDateOf(at)} ${at.getFullYear()}`;
}

function clockOf(at: Date): string {
  return at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

// Spelled out rather than asked of Intl: en-GB's short September is "Sept", which the mockup never draws.
const MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];

function shortDateOf(at: Date): string {
  return `${String(at.getDate()).padStart(2, "0")} ${MONTHS[at.getMonth()]}`;
}

function sameDay(left: Date, right: Date): boolean {
  return left.toDateString() === right.toDateString();
}
