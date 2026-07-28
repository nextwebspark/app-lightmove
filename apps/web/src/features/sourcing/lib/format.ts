/** "$18M" from 18000000 — the drawer and card's compact revenue rendering. Null-safe. */
export function formatUsdCompact(value: number | null): string | null {
  if (value === null) return null;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}
