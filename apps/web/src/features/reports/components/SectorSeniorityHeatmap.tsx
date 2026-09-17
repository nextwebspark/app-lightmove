import { cn } from "../../../lib/cn";
import type { SeniorityLevel } from "../api/types";
import type { HeatRow } from "../lib/marketStats";
import { RAMP_BG, RAMP_GROUND_LABEL_FROM } from "../lib/ramp";

const HATCH =
  "bg-[repeating-linear-gradient(135deg,var(--color-u-surface)_0_6px,var(--color-u-bg)_6px_12px)]";

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
  const columns = { gridTemplateColumns: `96px repeat(${sectors.length}, minmax(0, 1fr))` };
  return (
    <div className="mt-3.5 overflow-x-auto">
      <div className="grid min-w-[520px] gap-[5px]" style={columns}>
        <div />
        {sectors.map((sector) => (
          <div
            key={sector}
            className="self-end break-words pb-1 text-center font-u-num text-[9.5px] font-bold capitalize leading-tight tracking-[0.04em] text-u-text3"
          >
            {sector}
          </div>
        ))}
        {rows.map((row) => (
          <HeatmapRow key={row.level} row={row} onSelect={onSelect} />
        ))}
      </div>
    </div>
  );
}

function HeatmapRow({ row, onSelect }: { row: HeatRow; onSelect: (sector: string, level: SeniorityLevel) => void }) {
  return (
    <>
      <div className="flex items-center text-[11px] font-semibold text-u-text2">{row.level}</div>
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
              "grid h-11 place-items-center rounded-[7px] font-u-num text-[13px] transition-[transform,box-shadow] duration-100",
              isEmpty
                ? cn(HATCH, "border border-dashed border-u-border-strong font-normal text-u-text3 hover:shadow-[inset_0_0_0_2px_var(--color-u-border-strong)]")
                : cn(
                    "font-bold hover:scale-105 hover:shadow-[inset_0_0_0_2px_var(--color-u-accent)]",
                    RAMP_BG[cell.stop],
                    cell.stop >= RAMP_GROUND_LABEL_FROM ? "text-u-bg" : "text-u-text",
                  ),
            )}
          >
            {isEmpty ? "·" : cell.count}
          </button>
        );
      })}
    </>
  );
}
