import { wallClockOf } from "../../../lib/datetime/zone";

/** "Good morning, Yara." by the hour in the person's own zone, the one the date beside it reads in. */
export function greetingOf(firstName: string | undefined, now: Date = new Date(), timeZone?: string): string {
  // Not `now.getHours()`: that is the browser's clock, hours from a pinned zone, so the evening greeted the morning.
  const hour = Number(wallClockOf(now, timeZone).time.slice(0, 2));
  const part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  return firstName ? `${part}, ${firstName}.` : `${part}.`;
}
