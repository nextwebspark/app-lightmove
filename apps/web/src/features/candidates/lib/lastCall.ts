import { dayKeyOf, formatInstantDate, formatInstantDateTime, formatShortDate } from "../../../lib/datetime";
import type { LastCall } from "../api/types";
import { outcomeLabel } from "./noteOutcomes";

function dayOf(at: string, now: Date): string | null {
  const sameYear = dayKeyOf(new Date(at)).slice(0, 4) === dayKeyOf(now).slice(0, 4);
  return sameYear ? formatShortDate(at) : formatInstantDate(at);
}

/** "Last call 14 Sep · No answer · Sara Al-Mansour": when, how it went and who logged it. An older year names itself. */
export function lastCallLine(call: LastCall, now: Date = new Date()): string {
  const outcome = call.outcome ? outcomeLabel(call.outcome) : null;
  return [`Last call ${dayOf(call.at, now) ?? ""}`.trim(), outcome, call.byName ?? "A former member"]
    .filter(Boolean)
    .join(" · ");
}

export function lastCallTitle(call: LastCall): string {
  const when = formatInstantDateTime(call.at);
  const what = "The newest call anyone on the team logged with this person, on any position";
  return when ? `${when} · ${what}` : what;
}
