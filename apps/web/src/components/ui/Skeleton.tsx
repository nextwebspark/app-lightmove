import { useEffect, useState, type ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * The gray pulsing placeholder shown while a query is in flight, so a page never flashes its empty
 * state before the data has actually arrived. Caller sets width/height.
 */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn("animate-pulse rounded bg-u-raised", className)} />;
}

/* Widths cycle deterministically rather than randomly — a re-render must not reshuffle the bars. */
const BAR_WIDTHS = ["w-24", "w-16", "w-28", "w-12", "w-20"];

/**
 * A loading stand-in shaped like the lists — the real header row over pulsing bars, and card-shaped
 * blocks below `md` where those lists render cards, so the swap to data does not jump.
 */
export function TableSkeleton({ columns, rows = 6 }: { columns: string[]; rows?: number }) {
  const th =
    "whitespace-nowrap border-b border-u-border-strong px-3 py-[9px] text-left font-mono text-[10.5px] " +
    "font-semibold uppercase tracking-[0.12em] text-u-text3";

  return (
    <div role="status" aria-label="Loading">
      <div className="flex flex-col gap-2.5 md:hidden">
        {Array.from({ length: rows }, (_, rowIndex) => (
          <div key={rowIndex} className="flex flex-col gap-2.5 rounded-[10px] border border-u-border-strong p-3.5">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-3.5 w-48" />
            <Skeleton className="h-3 w-32" />
          </div>
        ))}
      </div>

      <div className="hidden overflow-x-auto md:block">
      <table className="w-full min-w-[820px] border-collapse">
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
                <td key={column} className="border-b border-u-border px-3 py-[15px]">
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
    </div>
  );
}

/** How long a read may take before a placeholder is drawn: a fast one should never flash. */
export const SKELETON_DELAY_MS = 200;

/** Announces the wait at once, and draws its shape only once the wait outlasts {@link SKELETON_DELAY_MS}. */
export function DelayedSkeleton({ className, children }: { className?: string; children: ReactNode }) {
  const [shown, setShown] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setShown(true), SKELETON_DELAY_MS);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div role="status" aria-label="Loading" className={className}>
      {shown && children}
    </div>
  );
}

const LINE_WIDTHS = ["w-full", "w-5/6", "w-2/3", "w-3/4"];

/** A section or a list still loading: a few lines of text. */
export function LinesSkeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <DelayedSkeleton className={cn("flex flex-col gap-2.5", className)}>
      {Array.from({ length: lines }, (_, index) => (
        <Skeleton key={index} className={cn("h-3", LINE_WIDTHS[index % LINE_WIDTHS.length])} />
      ))}
    </DelayedSkeleton>
  );
}

/** A settings page's list of cards still loading. */
export function CardsSkeleton({ cards = 3, className }: { cards?: number; className?: string }) {
  return (
    <DelayedSkeleton className={cn("flex flex-col gap-3", className)}>
      {Array.from({ length: cards }, (_, index) => (
        <div key={index} className="flex items-center gap-3 rounded-[10px] border border-u-border p-4">
          <Skeleton className="size-8 flex-none rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className={cn("h-3", BAR_WIDTHS[index % BAR_WIDTHS.length])} />
            <Skeleton className="h-2.5 w-40" />
          </div>
        </div>
      ))}
    </DelayedSkeleton>
  );
}

/** A record drawer still loading: the header's avatar and name, then two sections of lines. */
export function DrawerSkeleton() {
  return (
    <DelayedSkeleton className="flex flex-col gap-6 p-5">
      <div className="flex items-center gap-3">
        <Skeleton className="size-11 flex-none rounded-full" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-3 w-56" />
        </div>
      </div>
      {[4, 3].map((lines, section) => (
        <div key={section} className="flex flex-col gap-2.5">
          <Skeleton className="h-2.5 w-20" />
          {Array.from({ length: lines }, (_, index) => (
            <Skeleton key={index} className={cn("h-3", LINE_WIDTHS[index % LINE_WIDTHS.length])} />
          ))}
        </div>
      ))}
    </DelayedSkeleton>
  );
}
