import { cn } from "../../../lib/cn";
import type { SeniorityLevel } from "../api/types";
import type { HeatRow } from "../lib/marketStats";

/**
 * Sector × seniority on UNCAVA's five-stop sequential ramp. An empty cell is hatched rather than
 * blank so "nobody here yet" reads as a gap and not a rendering fault. Every cell is a button into
 * its slice.
 *
 * <p>The ramp runs pale-to-deep in light and deep-to-pale in dark — either way a fuller pocket sits
 * further from the page ground. That inverts which stops need a light label, so the label takes
 * `text-u-bg`: the ground colour is always the one that reads against the far end of the scale.
 */
export function SectorSeniorityHeatmap({
  sectors,
  rows,
  onSelect,
}: {
  sectors: string[];
  rows: HeatRow[];
  onSelect: (sector: string, level: SeniorityLevel) => void;
}) {
  const columns = { gridTemplateColumns: `92px repeat(${sectors.length}, minmax(0, 1fr))` };
  return (
    <div className="mt-3.5 overflow-x-auto">
      <div className="min-w-[520px]">
        <div className="mb-[5px] grid gap-[5px]" style={columns}>
          <div />
          {sectors.map((sector) => (
            <div
              key={sector}
              className="self-end pb-0.5 text-center text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3"
            >
              {sector}
            </div>
          ))}
        </div>
        {rows.map((row) => (
          <div key={row.level} className="mb-[5px] grid gap-[5px]" style={columns}>
            <div className="flex items-center text-xs font-medium text-u-text2">{row.level}</div>
            {row.cells.map((cell) => {
              const isEmpty = cell.count === 0;
              return (
                <button
                  key={cell.sector}
                  type="button"
                  onClick={() => onSelect(cell.sector, cell.level)}
                  aria-label={`${cell.sector} · ${cell.level}: ${cell.count} executives`}
                  title={`${cell.sector} · ${cell.level} — ${isEmpty ? "no executive yet" : `${cell.count} executives`}`}
                  className={cn(
                    "grid h-[42px] place-items-center rounded-[8px] font-u-num text-sm font-medium transition hover:outline hover:outline-[1.5px] hover:outline-u-accent",
                    isEmpty
                      ? "border border-dashed border-u-border-strong bg-[repeating-linear-gradient(135deg,var(--color-u-surface)_0_6px,var(--color-u-raised)_6px_12px)] font-normal text-u-text3"
                      : stopOf(cell.intensity) >= 4
                        ? "text-u-bg hover:scale-[1.04]"
                        : "text-u-text hover:scale-[1.04]",
                  )}
                  style={isEmpty ? undefined : { background: `var(--color-u-seq-${stopOf(cell.intensity)})` }}
                >
                  {isEmpty ? "·" : cell.count}
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}

/** Which of the ramp's five stops a cell lands on. Intensity is a share of the fullest pocket. */
function stopOf(intensity: number): number {
  return Math.min(5, Math.max(1, Math.ceil(intensity * 5)));
}
