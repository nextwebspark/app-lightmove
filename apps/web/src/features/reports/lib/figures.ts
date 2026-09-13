/** The report's small formatting vocabulary — shared so every chapter states a figure the same way. */

/** 1100 → "$1,100K". Compensation is carried in USD thousands, the unit a comp conversation uses. */
export function formatMoneyK(thousands: number): string {
  return `$${Math.round(thousands).toLocaleString("en-US")}K`;
}

/** "2026-07-21" → "21 Jul". Chart labels and findings, where the year is the mandate's own. */
export function formatShortDate(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
}

/** 38 → "38th", 1 → "1st", 12 → "12th". */
export function ordinal(n: number): string {
  const rem = n % 100;
  if (rem >= 11 && rem <= 13) return `${n}th`;
  const suffix = ["th", "st", "nd", "rd"][n % 10] ?? "th";
  return `${n}${suffix}`;
}

export function percent(part: number, whole: number): number {
  return whole === 0 ? 0 : Math.round((part / whole) * 100);
}

/** Days between two ISO dates, signed. */
export function daysBetween(fromIso: string, toIso: string): number {
  const from = new Date(`${fromIso}T00:00:00`).getTime();
  const to = new Date(`${toIso}T00:00:00`).getTime();
  return Math.round((to - from) / 86_400_000);
}

export function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00`);
  d.setDate(d.getDate() + days);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
