import { formatTimeAgo } from "../../../lib/format";
import type { ApproachKind, ApproachPosition, ApproachTouch, RecentApproach } from "../api/types";
import { outcomeLabel } from "./noteOutcomes";

/** One thing a screen says, and the position it opens when the reader may open it. */
export interface ApproachLine {
  text: string;
  href: string | null;
}

const LOGGED: Partial<Record<ApproachKind, string>> = {
  CALL_LOGGED: "logged a call",
  LINKEDIN_LOGGED: "logged a LinkedIn message",
  WHATSAPP_LOGGED: "logged a WhatsApp message",
  MEETING_LOGGED: "logged a meeting",
  EMAIL_LOGGED: "logged an email",
};

function whenOf(at: string, now: Date): string {
  return new Date(at).getTime() > now.getTime() ? "scheduled" : formatTimeAgo(at, now);
}

/** A logged call or WhatsApp message says how it went: "called (left voicemail)", not "logged a call". */
function loggedOf(touch: ApproachTouch): string {
  if (touch.kind === "CALL_LOGGED" && touch.outcome) return `called (${outcomeLabel(touch.outcome).toLowerCase()})`;
  if (touch.kind === "WHATSAPP_LOGGED" && touch.outcome === "sent") return "sent a WhatsApp message";
  return LOGGED[touch.kind] ?? "approached";
}

function whatOf(touch: ApproachTouch): string {
  const who = touch.byYou ? "You" : (touch.byName ?? "A former member");
  switch (touch.kind) {
    case "REPLIED": {
      const channel = touch.outcome === "replied" ? " on WhatsApp" : "";
      return touch.byYou ? `Replied to you${channel}` : `Replied to ${who}${channel}`;
    }
    case "IN_SEQUENCE":
      return touch.byYou ? "In your sequence" : `In ${who}'s sequence`;
    case "EMAILED":
      return `${who} emailed`;
    default:
      return `${who} ${loggedOf(touch)}`;
  }
}

function hrefOf(projectId: string | null): string | null {
  return projectId ? `/projects/${projectId}` : null;
}

/** "Sara Al-Mansour emailed · 12 days ago · Head of Credit Risk": who, how, when, where. Never the words. */
export function touchLine(touch: ApproachTouch, now: Date = new Date()): ApproachLine {
  const where = touch.samePosition ? "this position" : touch.positionHidden ? "another position" : touch.positionTitle;
  return {
    text: [whatOf(touch), whenOf(touch.at, now), where].filter(Boolean).join(" · "),
    href: touch.samePosition ? null : hrefOf(touch.projectId),
  };
}

/** A position the reader cannot open is "another position" and its stage, nothing more. */
export function positionLine(position: ApproachPosition): ApproachLine {
  if (!position.visible) {
    const text = position.inProcess
      ? `In process on another position · ${position.stageLabel}`
      : "Out of process on another position";
    return { text, href: null };
  }
  const title = position.positionTitle ?? "another position";
  const href = hrefOf(position.projectId);
  if (position.placed) return { text: `Placed on ${title}`, href };
  if (!position.inProcess) return { text: `${position.statusLabel} on ${title}`, href };
  const by = position.addedByName ? ` · added by ${position.addedByName}` : "";
  return { text: `In process on ${title} · ${position.statusLabel}${by}`, href };
}

/** Every line a screen says, loudest first: a reply, the window's touches, in process elsewhere, then the quiet ones. */
export function approachLines(approach: RecentApproach, now: Date = new Date()): ApproachLine[] {
  const recent = approach.touches.map((touch) => touchLine(touch, now));
  const inProcess = approach.elsewhere.filter((position) => position.inProcess).map(positionLine);
  const outOfProcess = approach.elsewhere.filter((position) => !position.inProcess).map(positionLine);
  const older = approach.lastApproach ? [touchLine(approach.lastApproach, now)] : [];
  return [
    ...recent,
    ...inProcess,
    ...older.map((line) => ({ ...line, text: `Last approached: ${line.text}` })),
    ...outOfProcess,
  ];
}

/** Whether a screen should say anything at all. */
export function hasApproach(approach: RecentApproach | null | undefined): approach is RecentApproach {
  return !!approach && approach.tier !== "NONE";
}

/** Amber: going ahead asks first, and a bulk list leaves the person unticked. */
export function isCaution(approach: RecentApproach | null | undefined): approach is RecentApproach {
  return approach?.tier === "CAUTION";
}

/**
 * What was confirmed: who, how and where, never when. A new teammate, kind or position asks again; a poll, or a
 * scheduled email going out, does not.
 */
export function approachKeyOf(approach: RecentApproach | null | undefined): string | null {
  if (!isCaution(approach)) return null;
  const touches = approach.touches.map(
    (touch) => `${touch.kind}:${touch.byUserId}:${touch.projectId ?? (touch.positionHidden ? "elsewhere" : "none")}`,
  );
  const positions = approach.elsewhere
    .filter((position) => position.inProcess)
    .map((position) => `${position.projectId ?? position.stageLabel}:${position.placed}`);
  return [...new Set([...touches, ...positions])].sort().join("|");
}
