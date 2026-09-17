import { cn } from "../../../lib/cn";

export interface BarRow {
  key: string;
  label: string;
  count: number;
  title?: string;
}

/**
 * Horizontal bar rows: label, a bar on a sunken track scaled to the largest row, and the raw count.
 * Scaled to the leader rather than to the total, so a long tail of small rows is still readable, and
 * only the leader takes the accent. Rows become buttons when a caller wants a row to open something.
 */
export function BarList({ rows, onSelect }: { rows: BarRow[]; onSelect?: (row: BarRow) => void }) {
  const largest = rows.reduce((max, row) => Math.max(max, row.count), 0);
  return (
    <div className="flex flex-col gap-2">
      {rows.map((row, index) => {
        const width = largest === 0 ? 0 : Math.max((row.count / largest) * 100, 3);
        const content = (
          <>
            <span className="w-[92px] flex-none truncate text-left text-xs text-u-text2">{row.label}</span>
            <span className="h-3.5 flex-1 overflow-hidden rounded-[4px] bg-u-sunken">
              <span
                className={cn(
                  "block h-full rounded-[4px] transition-[width] duration-300",
                  index === 0 ? "bg-u-accent" : "bg-u-border-strong",
                )}
                style={{ width: `${width}%` }}
              />
            </span>
            <span className="w-[34px] flex-none text-right font-u-num text-[11.5px] text-u-text2">{row.count}</span>
          </>
        );
        return onSelect ? (
          <button
            key={row.key}
            type="button"
            title={row.title}
            onClick={() => onSelect(row)}
            className="-mx-1 flex items-center gap-3 rounded-md px-1 py-0.5 transition hover:bg-u-raised"
          >
            {content}
          </button>
        ) : (
          <div key={row.key} title={row.title} className="flex items-center gap-3">
            {content}
          </div>
        );
      })}
    </div>
  );
}
