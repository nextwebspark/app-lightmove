import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

/** How loudly a finding reads. `alarm` is for a figure the reader has to act on, not merely note. */
export type FindingTone = "info" | "alarm";

const BANNER_TONE: Record<FindingTone, string> = {
  info: "border-u-border-strong bg-u-raised",
  alarm: "border-u-signal/40 bg-u-signal-tint",
};

/**
 * One numbered chapter of the report: the sky ordinal and mono eyebrow, the question the chapter
 * answers, a line of explanation, then the chapter's own tiles and charts.
 *
 * <p>The question is fixed and the <i>finding</i> is computed, so they are two elements rather than
 * one — the reader sees what was asked before what the rows answered, and a mandate with nothing to
 * report still has a heading. The finding sits in its own tinted block for the same reason the
 * source mockup gave it one: it is the only sentence on the page that changes with the data.
 */
export function ReportSection({
  id,
  ordinal,
  eyebrow,
  question,
  lede,
  finding,
  findingLabel,
  findingTone = "info",
  children,
}: {
  id: string;
  ordinal: string;
  eyebrow: string;
  question: string;
  lede?: ReactNode;
  /** The computed finding. Omitted where the chapter states it in the lede instead. */
  finding?: ReactNode;
  /** The banner's own eyebrow — "At the current pace", "At these settings". */
  findingLabel?: string;
  findingTone?: FindingTone;
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-6 border-t border-line pt-[34px] first:border-0 first:pt-0">
      <div className="mb-3 flex items-center gap-[9px]">
        <span className="font-u-num text-[10px] font-medium tracking-[0.06em] text-u-accent">{ordinal}</span>
        <span className="text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">{eyebrow}</span>
      </div>
      <h3 className="max-w-[760px] text-[17px] font-semibold leading-[1.45]">{question}</h3>
      {lede && <p className="mt-2 max-w-[760px] text-[13px] leading-[1.6] text-text2">{lede}</p>}
      {finding && (
        <div className={cn("mt-4 max-w-[760px] rounded-[12px] border px-5 py-4", BANNER_TONE[findingTone])}>
          {findingLabel && (
            <div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
              {findingLabel}
            </div>
          )}
          <div className="text-[14.5px] leading-[1.65] text-u-text">{finding}</div>
        </div>
      )}
      <div className="mt-5">{children}</div>
    </section>
  );
}

/** The figure a finding leads with, in the accent so the eye lands on it before the prose. */
export function Figure({ children }: { children: ReactNode }) {
  return <span className="font-semibold text-u-accent">{children}</span>;
}
