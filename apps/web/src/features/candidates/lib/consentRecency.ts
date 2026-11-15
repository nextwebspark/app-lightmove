import { formatInstantDate } from "../../../lib/datetime";
import type { ConsentStatus } from "../api/types";

export const DAY_MS = 86_400_000;
/** `PersonConsentService.RECENTLY`: a person asked, or who said no, is not asked again for 14 days. */
const RECENTLY_DAYS = 14;

export interface RecentAnswerOrAsk {
  kind: "declined" | "withdrawn" | "asked";
  /** The day it happened, as the app writes a date. */
  on: string;
  /** The first day they may be asked again. */
  againFrom: string;
}

/** Why a consent cannot be asked about again yet: a recent no, or a recent request. Null when it can. */
export function recentAnswerOrAskOf(
  consent: { status: ConsentStatus | null; answeredAt: string | null; requestedAt: string | null },
  now: number,
): RecentAnswerOrAsk | null {
  const recently = (instant: string | null): instant is string =>
    !!instant && now - Date.parse(instant) < RECENTLY_DAYS * DAY_MS;
  const held = (kind: RecentAnswerOrAsk["kind"], instant: string): RecentAnswerOrAsk => ({
    kind,
    on: formatInstantDate(instant) ?? "",
    againFrom: formatInstantDate(new Date(Date.parse(instant) + RECENTLY_DAYS * DAY_MS).toISOString()) ?? "",
  });
  if ((consent.status === "declined" || consent.status === "withdrawn") && recently(consent.answeredAt)) {
    return held(consent.status, consent.answeredAt);
  }
  if (recently(consent.requestedAt)) return held("asked", consent.requestedAt);
  return null;
}
