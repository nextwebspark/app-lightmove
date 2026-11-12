import {
  DATE_FORMATS,
  formatDate,
  formatFull,
  formatClock,
  isOfferedTimeZone,
  offsetLabelOf,
  TIME_FORMATS,
  timeZoneOptionLabel,
  timeZonesByOffset,
  zoneCityLabel,
  type DateFormatStyle,
  type DateTimePreferences,
  type TimeFormatStyle,
} from "../../../lib/datetime";
import type { ListboxOption } from "../../../components/ui/ListboxSelect";

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

/** The countries each zone is the clock of, so a search for "UAE" or "Saudi" finds it. */
const ZONE_COUNTRIES: Record<string, string> = {
  UTC: "GMT Greenwich",
  "Asia/Dubai": "United Arab Emirates UAE Sharjah",
  "Asia/Muscat": "Oman",
  "Asia/Riyadh": "Saudi Arabia KSA Dammam",
  "Asia/Qatar": "Qatar",
  "Asia/Kuwait": "Kuwait",
  "Asia/Bahrain": "Bahrain",
  "Africa/Cairo": "Egypt",
  "Asia/Amman": "Jordan",
  "Asia/Beirut": "Lebanon",
  "Asia/Baghdad": "Iraq",
  "Asia/Tehran": "Iran",
  "Europe/Istanbul": "Turkey Türkiye",
  "Africa/Casablanca": "Morocco",
  "Africa/Lagos": "Nigeria",
  "Africa/Nairobi": "Kenya",
  "Africa/Johannesburg": "South Africa",
  "Europe/London": "United Kingdom UK Britain England",
  "Europe/Paris": "France",
  "Europe/Berlin": "Germany",
  "Europe/Athens": "Greece",
  "Europe/Moscow": "Russia",
  "Asia/Karachi": "Pakistan",
  "Asia/Kolkata": "India",
  "Asia/Dhaka": "Bangladesh",
  "Asia/Bangkok": "Thailand",
  "Asia/Singapore": "Singapore",
  "Asia/Hong_Kong": "Hong Kong",
  "Asia/Shanghai": "China",
  "Asia/Tokyo": "Japan",
  "Asia/Seoul": "South Korea",
  "Australia/Perth": "Australia",
  "Australia/Sydney": "Australia",
  "Pacific/Auckland": "New Zealand",
  "Pacific/Honolulu": "Hawaii United States USA",
  "America/Anchorage": "Alaska United States USA",
  "America/Los_Angeles": "United States USA",
  "America/Denver": "United States USA",
  "America/Chicago": "United States USA",
  "America/New_York": "United States USA",
  "America/Mexico_City": "Mexico",
  "America/Sao_Paulo": "Brazil",
};

/** The time zone list, west to east; a zone saved before the list stays offered, marked, so the picker is never blank. */
export function timeZoneSelectOptions(saved: string | null, now: Date = new Date()): ListboxOption<string>[] {
  const options: ListboxOption<string>[] = timeZonesByOffset(now).map((offered) => ({
    value: offered.zone,
    label: timeZoneOptionLabel(offered, now),
    keywords: keywordsOf(offered.zone, now),
  }));
  if (saved && !isOfferedTimeZone(saved)) {
    options.push({ value: saved, label: timeZoneLabelOf(saved, now), detail: "no longer offered", keywords: keywordsOf(saved, now) });
  }
  return options;
}

/** The zone's id, its countries and its short offset ("GMT+4"), so "+4" finds Dubai as "(GMT+04:00)" alone would not. */
function keywordsOf(zone: string, now: Date): string {
  return `${zone.replace(/[/_]/g, " ")} ${ZONE_COUNTRIES[zone] ?? ""} ${offsetLabelOf(now, zone)}`;
}

/** "(GMT+04:00) Dubai, Abu Dhabi" for a listed zone, or the city and offset of one saved before the list. */
export function timeZoneLabelOf(zone: string, now: Date = new Date()): string {
  return timeZoneOptionLabel({ zone, label: zoneCityLabel(zone) }, now);
}

/** Each format as today's date reads in it, the pattern beside it: "04 Dec 2026" · "DD MMM YYYY". */
export function dateFormatSelectOptions(now: Date = new Date()): ListboxOption<DateFormatStyle>[] {
  return DATE_FORMATS.map((format) => ({
    value: format,
    label: formatDate(now, { dateFormat: format }),
    detail: DATE_FORMAT_PATTERNS[format],
  }));
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
