import { cn } from "../../../lib/cn";
import type { SeniorityLevel } from "../api/types";
import type { HeatRow } from "../lib/marketStats";

/**
 * Sector × seniority, one hue light-to-dark by count. An empty cell is hatched rather than blank so
 * "nobody here yet" reads as a gap, not a rendering fault. Every cell is a button into its slice.
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
              className="self-end pb-0.5 text-center font-mono text-[9px] font-semibold uppercase tracking-[0.08em] text-text3"
            >
              {sector}
            </div>
          ))}
        </div>
        {rows.map((row) => (
          <div key={row.level} className="mb-[5px] grid gap-[5px]" style={columns}>
            <div className="flex items-center text-xs font-medium text-text2">{row.level}</div>
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
                    "grid h-[42px] place-items-center rounded-[7px] font-mono text-sm font-bold transition hover:outline hover:outline-[1.5px] hover:outline-sky",
                    isEmpty
                      ? "border border-dashed border-line bg-[repeating-linear-gradient(135deg,var(--color-panel2)_0_6px,var(--color-panel)_6px_12px)] font-normal text-text3"
                      : cell.intensity > 0.5
                        ? "text-white hover:scale-[1.04]"
                        : "text-text hover:scale-[1.04]",
                  )}
                  style={
                    isEmpty
                      ? undefined
                      : { background: `color-mix(in srgb, var(--color-sky) ${Math.round(14 + cell.intensity * 76)}%, var(--color-panel))` }
                  }
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
