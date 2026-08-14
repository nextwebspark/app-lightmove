/** Tiny display helpers shared across features — the roster, drawers and tables all speak these. */

/** "ADMIN" → "Admin". */
export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

/** "2026-09-15" → "15 Sep 2026", the mockups' date shape. */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return "—";
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

/** 450000 → "450K", the compact money shape the hero and the report band both show. */
export function abbreviateAmount(value: number): string {
  return value >= 1000 ? `${Math.round(value / 1000)}K` : String(value);
}

/** "Sara Al-Mansour" → "SA". */
export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}
