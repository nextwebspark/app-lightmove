import type { MandateProgress } from "../api/types";

/**
 * A mandate's assignment progress: how much of its universe is researched, and — once that is done —
 * how far it has worked the people it found. Two segments on one track, so a search reads as one
 * journey rather than two unrelated percentages.
 *
 * <p>The bar is decoration; the caption beside it carries the figure, which is what a screen reader
 * and a narrow column both need.
 */
export function PhaseBar({ progress, className = "" }: { progress: MandateProgress; className?: string }) {
  const engaging = progress.activePhase === "ENGAGE";
  const label = engaging
    ? `Engage · ${progress.engagePercent}%`
    : `Map · ${progress.mapPercent}%`;

  return (
    <span className={`block min-w-0 ${className}`}>
      <span className="flex h-1.5 overflow-hidden rounded-full bg-line-soft" aria-hidden="true">
        <span className="bg-green transition-[width]" style={{ width: `${progress.mapPercent}%` }} />
        {engaging && (
          // Scaled into what the map segment left, so the two never add up past the track.
          <span
            className="bg-amber transition-[width]"
            style={{ width: `${(100 - progress.mapPercent) * (progress.engagePercent / 100)}%` }}
          />
        )}
      </span>
      <span
        className={`mt-1 block whitespace-nowrap font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${
          engaging ? "text-amber" : "text-green"
        }`}
      >
        {label}
      </span>
    </span>
  );
}
