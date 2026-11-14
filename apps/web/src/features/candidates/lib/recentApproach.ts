import { formatTimeAgo } from "../../../lib/format";
import type { ApproachKind, ApproachPosition, ApproachTouch, RecentApproach } from "../api/types";

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

function whatOf(touch: ApproachTouch): string {
  const who = touch.byYou ? "You" : (touch.byName ?? "A former member");
  switch (touch.kind) {
    case "REPLIED":
      return touch.byYou ? "Replied to you" : `Replied to ${who}`;
    case "IN_SEQUENCE":
      return touch.byYou ? "In your sequence" : `In ${who}'s sequence`;
    case "EMAILED":
      return `${who} emailed`;
    default:
      return `${who} ${LOGGED[touch.kind] ?? "approached"}`;
  }
}

/** "Sara Al-Mansour emailed · 12 days ago · Head of Credit Risk": who, how, when, where. Never the words. */
export function touchLine(touch: ApproachTouch, now: Date = new Date()): string {
  const where = touch.samePosition ? "this position" : touch.positionTitle;
  return [whatOf(touch), whenOf(touch.at, now), where].filter(Boolean).join(" · ");
}

export function positionLine(position: ApproachPosition): string {
  if (position.placed) return `Placed on ${position.positionTitle}`;
  if (!position.inProcess) return `${position.statusLabel} on ${position.positionTitle}`;
  const by = position.addedByName ? ` · added by ${position.addedByName}` : "";
  return `In process on ${position.positionTitle} · ${position.statusLabel}${by}`;
}

/** Every line a screen says, loudest first: a reply, the window's touches, in process elsewhere, then the quiet ones. */
export function approachLines(approach: RecentApproach, now: Date = new Date()): string[] {
  const recent = approach.touches.map((touch) => touchLine(touch, now));
  const inProcess = approach.elsewhere.filter((position) => position.inProcess).map(positionLine);
  const outOfProcess = approach.elsewhere.filter((position) => !position.inProcess).map(positionLine);
  const older = approach.lastApproach ? [`Last approached: ${touchLine(approach.lastApproach, now)}`] : [];
  return [...recent, ...inProcess, ...older, ...outOfProcess];
}

/** Whether a screen should say anything at all. */
export function hasApproach(approach: RecentApproach | null | undefined): approach is RecentApproach {
  return !!approach && approach.tier !== "NONE";
}

/** Amber: the warning a bulk list leaves unticked. */
export function isCaution(approach: RecentApproach | null | undefined): boolean {
  return approach?.tier === "CAUTION";
}
