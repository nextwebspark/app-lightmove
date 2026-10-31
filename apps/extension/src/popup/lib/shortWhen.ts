/** A note's time as the web app's cards print it: "Today 14:05", "12 Oct", or "12 Oct 2025" from another year. */
export function shortWhen(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  if (Number.isNaN(at.getTime())) return "—";
  if (at.toDateString() === now.toDateString()) {
    return `Today ${at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`;
  }
  return at.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    ...(at.getFullYear() === now.getFullYear() ? {} : { year: "numeric" }),
  });
}
