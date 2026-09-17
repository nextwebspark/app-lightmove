import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

/** The surface every card of the report sits on. Dashed marks a card that names something not built. */
export function ReportPanel({ dashed, children }: { dashed?: boolean; children: ReactNode }) {
  return (
    <div
      className={cn(
        "mt-4 rounded-[11px] border border-u-border bg-u-surface px-4 py-[18px] shadow-u-e1 sm:px-6 sm:py-[22px]",
        dashed && "border-dashed",
      )}
    >
      {children}
    </div>
  );
}

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
}: {
  title: string;
  caption?: ReactNode;
  action?: ReactNode;
  note?: ReactNode;
  children: ReactNode;
}) {
  return (
    <ReportPanel>
      <div className="mb-1 flex flex-wrap items-start justify-between gap-3.5">
        <div>
          <div className="text-[15px] font-bold">{title}</div>
          {caption && <div className="mt-[3px] text-xs text-u-text3">{caption}</div>}
        </div>
        {action && <div className="flex flex-wrap items-center gap-2.5">{action}</div>}
      </div>
      {children}
      {note && (
        <div className="mt-3.5 border-t border-dashed border-u-border-strong pt-[13px] text-xs italic leading-[1.65] text-u-text2 [&_b]:font-bold [&_b]:not-italic [&_b]:text-u-text">
          {note}
        </div>
      )}
    </ReportPanel>
  );
}
