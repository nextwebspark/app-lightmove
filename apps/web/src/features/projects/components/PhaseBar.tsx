import type { MandateProgress, ProjectType } from "../api/types";

/**
 * How much of the map is the whole journey for a search — the same 60/40 split its mapping target is
 * drafted at, so the bar and the timeline tell one story. A mapping-only mandate owes no shortlist, so
 * its map is the whole bar.
 */
const MAP_SHARE_OF_SEARCH = 60;

/**
 * A mandate's assignment progress: how much of its universe is researched, and — once that is done —
 * how far it has worked the people it found. Two segments on one track, so a search reads as one
 * journey rather than two unrelated percentages.
 *
 * <p>The bar is decoration; the caption beside it carries the figure, which is what a screen reader
 * and a narrow column both need.
 */
export function PhaseBar({
  progress,
  projectType,
  className = "",
}: {
  progress: MandateProgress;
  projectType: ProjectType;
  className?: string;
}) {
  const engaging = progress.activePhase === "ENGAGE";
  const mapShare = projectType === "EXECUTIVE_SEARCH" ? MAP_SHARE_OF_SEARCH : 100;

  const mapWidth = (mapShare * progress.mapPercent) / 100;
  const engageWidth = engaging ? ((100 - mapShare) * progress.engagePercent) / 100 : 0;

  const label = engaging ? `Engage · ${progress.engagePercent}%` : `Map · ${progress.mapPercent}%`;

  return (
    <span className={`block min-w-0 ${className}`}>
      <span className="flex h-1.5 overflow-hidden rounded-full bg-line-soft" aria-hidden="true">
        <span className="bg-green transition-[width]" style={{ width: `${mapWidth}%` }} />
        <span className="bg-amber transition-[width]" style={{ width: `${engageWidth}%` }} />
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
