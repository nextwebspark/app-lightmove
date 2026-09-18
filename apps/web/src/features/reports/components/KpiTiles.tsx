import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

/**
 * What kind of figure a tile holds: the chapter's lead stat, a supporting one, a risk to watch, or a
 * strength. The kind, not the state — a risk tile stays copper on a healthy mandate, so the row
 * reads the same from one report to the next and the figure itself carries the news.
 */
export type KpiTone = "lead" | "plain" | "alarm" | "positive";

const TONE: Record<KpiTone, { surface: string; label: string; figure: string; sub: string }> = {
  lead: { surface: "bg-u-raised", label: "text-u-text3", figure: "text-u-accent", sub: "text-u-text2" },
  plain: { surface: "bg-u-surface", label: "text-u-text3", figure: "text-u-text", sub: "text-u-text2" },
  alarm: {
    surface: "bg-u-signal-tint",
    label: "text-u-signal",
    figure: "text-u-signal",
    sub: "text-[color:color-mix(in_srgb,var(--color-u-signal)_80%,var(--color-u-text2))]",
  },
  positive: {
    surface: "bg-u-direct-tint",
    label: "text-u-direct",
    figure: "text-u-direct",
    sub: "text-[color:color-mix(in_srgb,var(--color-u-direct)_80%,var(--color-u-text2))]",
  },
};

export interface KpiTileProps {
  label: string;
  value: ReactNode;
  /** The small unit or denominator beside the figure — "/42", "/wk", "%". */
  unit?: string;
  sub?: ReactNode;
  tone?: KpiTone;
}

export function KpiTile({ label, value, unit, sub, tone = "plain" }: KpiTileProps) {
  const style = TONE[tone];
  return (
    <div className={cn("min-w-0 rounded-[10px] px-[18px] py-4", style.surface)}>
      <div className={cn("min-h-[24px] text-[9.5px] font-bold uppercase leading-[1.25] tracking-[0.07em]", style.label)}>{label}</div>
      <div
        className={cn(
          "mt-2 break-words font-u-num text-[22px] font-extrabold leading-[1.2] tracking-[-0.01em] sm:text-[27px]",
          style.figure,
        )}
      >
        {value}
        {unit && <span className="ms-0.5 text-sm font-semibold opacity-60">{unit}</span>}
      </div>
      {sub && <div className={cn("mt-1.5 text-[11px] leading-[1.5]", style.sub)}>{sub}</div>}
    </div>
  );
}

/** Four across on a desktop, two on a phone; `columns={2}` is the pair a card sets side by side. */
export function KpiTileRow({
  children,
  columns = 4,
  className,
}: {
  children: ReactNode;
  columns?: 2 | 4;
  className?: string;
}) {
  return (
    <div className={cn("mt-[22px] grid grid-cols-2 gap-3", columns === 4 && "lg:grid-cols-4", className)}>{children}</div>
  );
}
