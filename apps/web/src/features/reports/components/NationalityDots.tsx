import { cn } from "../../../lib/cn";
import type { Feasibility } from "../lib/diversityStats";

const STAGGER_MS = 12;
const MAX_DELAY_MS = 300;

/**
 * One square per qualifying executive, grouped by level. Countable rather than abstract: "23 of 51
 * at C-Suite" becomes twenty-three squares a reader can see, not a bar they have to measure. Levels
 * outside the chosen scope are drawn but recede.
 *
 * <p>Every row here holds somebody, so an empty one means the requirement excluded them all rather
 * than that the level is unmapped — {@link feasibility} leaves an unmapped level out entirely.
 */
export function NationalityDots({ feasibility }: { feasibility: Feasibility }) {
  return (
    <div className="flex flex-col gap-4">
      {feasibility.byLevel.map((row) => (
        <div key={row.level}>
          <div className="mb-[7px] flex justify-between">
            <span className="text-[11.5px] font-semibold text-u-text2">{row.level}</span>
            <span className="font-u-num text-[11px] font-bold">{row.count}</span>
          </div>
          <div className="flex min-h-3.5 flex-wrap gap-1" aria-label={`${row.count} at ${row.level}`}>
            {row.count === 0 ? (
              <span className="text-[10.5px] italic text-u-text3">none qualifying</span>
            ) : (
              Array.from({ length: row.count }, (_, i) => (
                <span
                  // Keyed on the count so a changed filter replays the arrival rather than reusing the squares.
                  key={`${i}-${row.count}`}
                  className={cn(
                    "size-[11px] animate-dot-pop rounded-[3px]",
                    row.isInScope ? "bg-u-accent" : "bg-u-border-strong opacity-60",
                  )}
                  style={{ animationDelay: `${Math.min(i * STAGGER_MS, MAX_DELAY_MS)}ms` }}
                />
              ))
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
