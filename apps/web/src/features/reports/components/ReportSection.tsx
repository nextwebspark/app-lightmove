import type { ReactNode } from "react";

/**
 * One chapter of the report: the question it answers, a line of explanation, the computed finding,
 * then the chapter's own tiles and charts.
 *
 * <p>The question is fixed and the <i>finding</i> is computed, so they are two elements rather than
 * one — the reader sees what was asked before what the rows answered, and a mandate with nothing to
 * report still has a heading.
 */
export function ReportSection({
  eyebrow,
  question,
  lede,
  finding,
  findingLabel,
  children,
}: {
  eyebrow: string;
  question: string;
  lede?: ReactNode;
  /** The computed finding. Omitted where the chapter states it in the lede instead. */
  finding?: ReactNode;
  /** The banner's own eyebrow — "At the current pace", "At these settings". */
  findingLabel?: string;
  children: ReactNode;
}) {
  return (
    <section className="animate-fade-up">
      <div className="text-[11px] font-bold uppercase tracking-[0.1em] text-u-text3">{eyebrow}</div>
      <h1 className="mt-2.5 text-[23px] font-bold leading-[1.25] tracking-[-0.01em] sm:text-[29px]">{question}</h1>
      {lede && (
        <p className="mt-3.5 max-w-[680px] text-[15.5px] leading-[1.7] text-u-text2 [&_b]:font-semibold [&_b]:text-u-text">
          {lede}
        </p>
      )}
      {finding && (
        <div className="mt-[26px] rounded-[11px] border border-u-border-strong bg-u-raised px-6 py-5 shadow-u-e1">
          {findingLabel && (
            <div className="mb-2 text-[9.5px] font-bold uppercase tracking-[0.1em] text-u-text3">{findingLabel}</div>
          )}
          <div className="text-[15px] leading-[1.65]">{finding}</div>
        </div>
      )}
      {children}
    </section>
  );
}

/** The figure a finding leads with, in the accent so the eye lands on it before the prose. */
export function Figure({ children }: { children: ReactNode }) {
  return <b className="font-bold text-u-accent">{children}</b>;
}
