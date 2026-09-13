import { cn } from "../../../lib/cn";
import type { Feasibility } from "../lib/diversityStats";

/**
 * One square per qualifying executive, grouped by level. Countable rather than abstract: "23 of 51
 * at C-Suite" becomes twenty-three squares a reader can see, not a bar they have to measure. Levels
 * outside the chosen scope are drawn but recede.
 */
export function NationalityDots({ feasibility }: { feasibility: Feasibility }) {
  return (
    <div className="flex flex-col gap-3.5">
      {feasibility.byLevel.map((row) => (
        <div key={row.level}>
          <div className="mb-1.5 flex justify-between">
            <span className="text-[11.5px] font-semibold text-text2">{row.level}</span>
            <span className="font-mono text-[11px] font-semibold">{row.count}</span>
          </div>
          <div className="flex min-h-[11px] flex-wrap gap-1" aria-label={`${row.count} at ${row.level}`}>
            {row.count === 0 ? (
              <span className="text-[10.5px] italic text-text3">none mapped</span>
            ) : (
              Array.from({ length: row.count }, (_, i) => (
                <span key={i} className={cn("size-[11px] rounded-[3px]", row.isInScope ? "bg-sky" : "bg-line")} />
              ))
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
