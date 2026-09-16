import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

/**
 * What a tile's fill says about its figure: the headline stat, a supporting one, or one the reader
 * has to act on. The source mockup carried this in cream and rust; here it is UNCAVA's tinted
 * surfaces, which invert with the theme, so a tile means the same thing in light and dark.
 */
export type KpiTone = "plain" | "accent" | "alarm";

const TONE_SURFACE: Record<KpiTone, string> = {
  plain: "border-u-border bg-u-raised",
  accent: "border-u-accent-ring bg-u-accent-tint",
  alarm: "border-u-signal/40 bg-u-signal-tint",
};

const TONE_FIGURE: Record<KpiTone, string> = {
  plain: "text-u-text",
  accent: "text-u-accent",
  alarm: "text-u-signal",
};

export interface KpiTileProps {
  label: string;
  value: ReactNode;
  /** The small unit or denominator beside the figure — "/ 42", "/ wk", "%". */
  unit?: string;
  sub?: ReactNode;
  tone?: KpiTone;
  /** Overrides the tone's own figure colour, for a tile whose fill and figure disagree. */
  valueClass?: string;
  className?: string;
}

/** One chapter figure. The row wraps to as many columns as fit, so four tiles stack on a phone. */
export function KpiTile({ label, value, unit, sub, tone = "plain", valueClass, className }: KpiTileProps) {
  return (
    <div className={cn("rounded-[12px] border px-[15px] py-3.5", TONE_SURFACE[tone], className)}>
      <div className={cn("font-u-num text-[22px] font-medium tracking-[-0.02em]", valueClass ?? TONE_FIGURE[tone])}>
        {value}
        {unit && <small className="ms-0.5 text-[11px] font-normal text-u-text3">{unit}</small>}
      </div>
      <div className="mt-[7px] text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">{label}</div>
      {sub && <div className="mt-1 text-[11px] text-u-text2">{sub}</div>}
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
