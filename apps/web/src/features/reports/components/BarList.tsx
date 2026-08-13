import type { Breakdown } from "../api/types";

/**
 * The report's horizontal bar rows: label, a track scaled to the largest row, and the raw count.
 * Scaled to the leader rather than to the total, so a long tail of small sectors is still readable.
 */
export function BarList({ caption, rows }: { caption: string; rows: Breakdown[] }) {
  const largest = rows.reduce((max, row) => Math.max(max, row.count), 0);
  return (
    <div>
      <div className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        {caption}
      </div>
      {rows.map((row) => (
        <div key={row.label} className="flex items-center gap-2.5 py-[5px]">
          <span className="w-[132px] flex-none truncate text-xs text-text2" title={row.label}>
            {row.label}
          </span>
          <span className="h-2 flex-1 overflow-hidden rounded-[4px] bg-line-soft">
            <span
              className="block h-2 rounded-[4px] bg-sky"
              style={{ width: `${largest === 0 ? 0 : Math.round((row.count / largest) * 100)}%` }}
            />
          </span>
          <span className="w-9 flex-none text-right font-mono text-[11.5px] text-text2">
            {row.count}
          </span>
        </div>
      ))}
    </div>
  );
}
