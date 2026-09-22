/** The report's small formatting vocabulary — shared so every chapter states a figure the same way. */

/** 1,100,000 in USD → "USD 1.1M"; 320,000 → "USD 320K". Whole units in; the compact figure a finding reads. */
export function formatCompactMoney(currency: string, amount: number): string {
  if (amount >= 1_000_000) return `${currency} ${trim(amount / 1_000_000)}M`;
  if (amount >= 1_000) return `${currency} ${Math.round(amount / 1_000)}K`;
  return `${currency} ${amount.toLocaleString("en-US")}`;
}

function trim(value: number): string {
  return value.toFixed(value >= 10 ? 0 : 1).replace(/\.0$/, "");
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

/** Shared with the projects screens, which measure the same windows. */
export { addDays, daysBetween } from "../../../lib/dates";
