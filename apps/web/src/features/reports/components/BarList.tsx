import { cn } from "../../../lib/cn";

export interface BarRow {
  key: string;
  label: string;
  count: number;
  /** Token background class; the leader is emphasised, the tail recedes. */
  fillClass?: string;
  title?: string;
}

/**
 * Horizontal bar rows: label, a track scaled to the largest row, and the raw count. Scaled to the
 * leader rather than to the total, so a long tail of small rows is still readable. Rows become
 * buttons when a caller wants a row to open something.
 */
export function BarList({ rows, onSelect }: { rows: BarRow[]; onSelect?: (row: BarRow) => void }) {
  const largest = rows.reduce((max, row) => Math.max(max, row.count), 0);
  return (
    <div>
      {rows.map((row) => {
        const width = largest === 0 ? 0 : Math.max(Math.round((row.count / largest) * 100), 2);
        const content = (
          <>
            <span className="w-[92px] flex-none truncate text-left text-xs text-u-text2 sm:w-[110px]">{row.label}</span>
            <span className="h-2 flex-1 overflow-hidden rounded-[4px] bg-u-border">
              <span
                className={cn("block h-2 rounded-e-[4px]", row.fillClass ?? "bg-u-chart-1")}
                style={{ width: `${width}%` }}
              />
            </span>
            <span className="w-9 flex-none text-right font-u-num text-[11.5px] text-u-text2">{row.count}</span>
          </>
        );
        return onSelect ? (
          <button
            key={row.key}
            type="button"
            title={row.title}
            onClick={() => onSelect(row)}
            className="flex w-full items-center gap-3 rounded-md px-1 py-[5px] transition hover:bg-u-surface"
          >
            {content}
          </button>
        ) : (
          <div key={row.key} title={row.title} className="flex items-center gap-3 px-1 py-[5px]">
            {content}
          </div>
        );
      })}
    </div>
  );
}
