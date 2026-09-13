import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

export interface KpiTileProps {
  label: string;
  value: ReactNode;
  /** The small unit or denominator beside the figure — "/ 42", "/ wk", "%". */
  unit?: string;
  sub?: ReactNode;
  /** Token class for the figure: `text-red` for a risk, `text-green` for a good one. */
  valueClass?: string;
  className?: string;
}

/** One chapter figure. The row wraps to as many columns as fit, so four tiles stack on a phone. */
export function KpiTile({ label, value, unit, sub, valueClass, className }: KpiTileProps) {
  return (
    <div className={cn("rounded-[10px] border border-line-soft bg-panel2 px-[15px] py-3.5", className)}>
      <div className={cn("text-[21px] font-bold tracking-[-0.01em]", valueClass)}>
        {value}
        {unit && <small className="ms-0.5 font-mono text-[11px] font-medium text-text3">{unit}</small>}
      </div>
      <div className="mt-[7px] font-mono text-[9px] font-semibold uppercase tracking-[0.08em] text-text3">
        {label}
      </div>
      {sub && <div className="mt-1 text-[11px] text-text2">{sub}</div>}
    </div>
  );
}

export function KpiTileRow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(136px,1fr))]", className)}>
      {children}
    </div>
  );
}
