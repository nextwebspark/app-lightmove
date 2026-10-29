import {
  DATE_FORMATS,
  formatDate,
  formatFull,
  formatClock,
  isOfferedTimeZone,
  TIME_FORMATS,
  timeZoneOptionLabel,
  timeZonesByOffset,
  zoneCityLabel,
  type DateFormatStyle,
  type DateTimePreferences,
  type TimeFormatStyle,
} from "../../../lib/datetime";
import type { SelectOption } from "./profileOptions";

/** What the Date & time pickers offer — the same fixed sets on Settings → General and Settings → Profile. */

export const DATE_FORMAT_PATTERNS: Record<DateFormatStyle, string> = {
  DD_MMM_YYYY: "DD MMM YYYY",
  MMM_D_YYYY: "MMM D, YYYY",
  DD_MM_YYYY: "DD/MM/YYYY",
  MM_DD_YYYY: "MM/DD/YYYY",
  YYYY_MM_DD: "YYYY-MM-DD",
};

export const TIME_FORMAT_NAMES: Record<TimeFormatStyle, string> = {
  H24: "24-hour",
  H12: "12-hour",
};

/** The time zone list, west to east; a zone saved before the list stays offered, marked, so the select is never blank. */
export function timeZoneSelectOptions(saved: string | null, now: Date = new Date()): SelectOption[] {
  const options = timeZonesByOffset(now).map((offered) => ({
    value: offered.zone,
    label: timeZoneOptionLabel(offered, now),
  }));
  if (saved && !isOfferedTimeZone(saved)) {
    options.push({ value: saved, label: `${timeZoneLabelOf(saved, now)} · no longer offered` });
  }
  return options;
}

/** "(GMT+04:00) Dubai, Abu Dhabi" for a listed zone, or the city and offset of one saved before the list. */
export function timeZoneLabelOf(zone: string, now: Date = new Date()): string {
  return timeZoneOptionLabel({ zone, label: zoneCityLabel(zone) }, now);
}

/** Each format as today's date reads in it, the pattern beside it: "04 Dec 2026 · DD MMM YYYY". */
export function dateFormatSelectOptions(now: Date = new Date()): { value: DateFormatStyle; label: string }[] {
  return DATE_FORMATS.map((format) => ({ value: format, label: dateFormatLabelOf(format, now) }));
}

export function dateFormatLabelOf(format: DateFormatStyle, now: Date = new Date()): string {
  return `${formatDate(now, { dateFormat: format })} · ${DATE_FORMAT_PATTERNS[format]}`;
}

/** "14:30 · 24-hour", "2:30 PM · 12-hour". */
export function timeFormatSelectOptions(): { value: TimeFormatStyle; label: string }[] {
  return TIME_FORMATS.map((format) => ({ value: format, label: timeFormatLabelOf(format) }));
}

export function timeFormatLabelOf(format: TimeFormatStyle): string {
  return `${formatClock("14:30", { timeFormat: format })} · ${TIME_FORMAT_NAMES[format]}`;
}

/** "Tue, 04 Dec 2026, 14:30 GMT+4 · Dubai, Abu Dhabi" — this moment as the choice being made would show it. */
export function previewOf(preferences: DateTimePreferences, now: Date = new Date()): string {
  return `${formatFull(now, preferences)} · ${zoneCityLabel(preferences.timeZone)}`;
}
