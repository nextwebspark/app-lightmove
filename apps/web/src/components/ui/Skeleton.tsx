import { cn } from "../../lib/cn";

/**
 * The gray pulsing placeholder shown while a query is in flight, so a page never flashes its empty
 * state before the data has actually arrived. Caller sets width/height.
 */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn("animate-pulse rounded bg-panel2", className)} />;
}

/* Widths cycle deterministically rather than randomly — a re-render must not reshuffle the bars. */
const BAR_WIDTHS = ["w-24", "w-16", "w-28", "w-12", "w-20"];

/**
 * A loading stand-in shaped like the list tables: the page's real header row (so the swap to data
 * doesn't jump) over rows of pulsing bars, on the same cell metrics as ProjectsTable/ClientsTable.
 */
export function TableSkeleton({ columns, rows = 6 }: { columns: string[]; rows?: number }) {
  const th =
    "whitespace-nowrap border-b border-line px-3 py-[9px] text-left font-mono text-[10.5px] " +
    "font-semibold uppercase tracking-[0.12em] text-text3";

  return (
    <div role="status" aria-label="Loading">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column} className={th}>
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }, (_, rowIndex) => (
            <tr key={rowIndex}>
              {columns.map((column, columnIndex) => (
                <td key={column} className="border-b border-line-soft px-3 py-[15px]">
                  <Skeleton
                    className={`h-3.5 ${BAR_WIDTHS[(rowIndex + columnIndex) % BAR_WIDTHS.length]}`}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
