import { useSyncExternalStore } from "react";

/** The date shapes on offer — the API's `DateFormatStyle`. */
export type DateFormatStyle = "DD_MMM_YYYY" | "MMM_D_YYYY" | "DD_MM_YYYY" | "MM_DD_YYYY" | "YYYY_MM_DD";

/** The two clocks — the API's `TimeFormatStyle`. */
export type TimeFormatStyle = "H24" | "H12";

export const DATE_FORMATS: readonly DateFormatStyle[] = [
  "DD_MMM_YYYY",
  "MMM_D_YYYY",
  "DD_MM_YYYY",
  "MM_DD_YYYY",
  "YYYY_MM_DD",
];

export const TIME_FORMATS: readonly TimeFormatStyle[] = ["H24", "H12"];

export const STANDARD_DATE_FORMAT: DateFormatStyle = "DD_MMM_YYYY";
export const STANDARD_TIME_FORMAT: TimeFormatStyle = "H24";

/** How every date and time reads: the signed-in person's own choices, else their workspace's. */
export interface DateTimePreferences {
  timeZone: string;
  dateFormat: DateFormatStyle;
  timeFormat: TimeFormatStyle;
}

/** The computer's zone and the standard formats, until a session says otherwise (a public page, a test). */
export function fallbackPreferences(): DateTimePreferences {
  return { timeZone: browserZone(), dateFormat: STANDARD_DATE_FORMAT, timeFormat: STANDARD_TIME_FORMAT };
}

let current: DateTimePreferences = fallbackPreferences();
const listeners = new Set<() => void>();

/** Set by `AuthProvider` with every user it adopts, before that user's first render; null on sign-out. */
export function setDateTimePreferences(next: DateTimePreferences | null): void {
  const settled = next ?? fallbackPreferences();
  if (
    settled.timeZone === current.timeZone &&
    settled.dateFormat === current.dateFormat &&
    settled.timeFormat === current.timeFormat
  ) {
    return;
  }
  current = settled;
  listeners.forEach((listener) => listener());
}

export function getDateTimePreferences(): DateTimePreferences {
  return current;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** For a component that must draw again when the preferences move. */
export function useDateTimePreferences(): DateTimePreferences {
  return useSyncExternalStore(subscribe, getDateTimePreferences, getDateTimePreferences);
}

/** The zone this computer's clock is set to. */
export function computerTimeZone(): string {
  return browserZone();
}

function browserZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}
