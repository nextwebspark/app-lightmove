import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

/**
 * A chart's frame: title and caption on the left, the controls that scope it on the right, the chart
 * in the body, and an optional note under a hairline that says what the chart means rather than
 * what it shows.
 */
export function ReportCard({
  title,
  caption,
  action,
  note,
  children,
  className,
}: {
  title: string;
  caption?: ReactNode;
  action?: ReactNode;
  note?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("mt-3.5 rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-4", className)}>
      <div className="flex flex-wrap items-start justify-between gap-3.5">
        <div>
          <div className="text-[13.5px] font-semibold">{title}</div>
          {caption && <div className="mt-[3px] font-mono text-[11px] text-text3">{caption}</div>}
        </div>
        {action && <div className="flex flex-wrap items-center gap-2">{action}</div>}
      </div>
      {children}
      {note && (
        <div className="mt-3.5 border-t border-line pt-3 text-xs leading-[1.6] text-text2 [&_b]:font-semibold [&_b]:text-text">
          {note}
        </div>
      )}
    </div>
  );
}

export interface LegendItem {
  label: string;
  /** Token background class for the swatch. */
  swatchClass: string;
  shape?: "square" | "dot" | "line" | "dashed";
}

/** Always present for two or more series, so identity never rests on colour alone. */
export function ChartLegend({ items, className }: { items: LegendItem[]; className?: string }) {
  return (
    <div className={cn("mt-3 flex flex-wrap gap-4", className)}>
      {items.map((item) => (
        <span key={item.label} className="inline-flex items-center gap-1.5 text-[11px] text-text2">
          <i
            aria-hidden
            className={cn(
              "inline-block flex-none",
              item.shape === "line" || item.shape === "dashed" ? "h-0.5 w-3.5 rounded-[1px]" : "size-[9px]",
              item.shape === "dot" ? "rounded-full" : "rounded-[2px]",
              item.shape === "dashed" ? "[mask-image:repeating-linear-gradient(90deg,#000_0_4px,transparent_4px_7px)]" : "",
              item.swatchClass,
            )}
          />
          {item.label}
        </span>
      ))}
    </div>
  );
}
