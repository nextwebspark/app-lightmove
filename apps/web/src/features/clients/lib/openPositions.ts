export function openPositionsLabel(count: number): string {
  return `${count} open ${count === 1 ? "position" : "positions"}`;
}
