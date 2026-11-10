import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";

/**
 * One chapter of the report: the question it answers, a line of explanation, the computed finding,
 * then the chapter's own tiles and charts.
 *
 * <p>The question is fixed and the <i>finding</i> is computed, so they are two elements rather than
 * one — the reader sees what was asked before what the rows answered, and a mandate with nothing to
 * report still has a heading.
 */
export function ReportSection({
  question,
  lede,
  finding,
  findingLabel,
  action,
  children,
}: {
  question: string;
  lede?: ReactNode;
  /** The computed finding. Omitted where the chapter states it in the lede instead. */
  finding?: ReactNode;
  /** The banner's own eyebrow — "At the current pace", "At these settings". */
  findingLabel?: string;
  /** The chapter's own acts, at the heading's right — wrapping under it on a narrow screen. */
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="animate-fade-up">
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
        <div className="min-w-0 flex-[1_1_320px]">
          <h1 className="text-[23px] font-bold leading-[1.25] tracking-[-0.01em] sm:text-[29px]">{question}</h1>
          {lede && (
            <p className="mt-3.5 max-w-[680px] text-[15.5px] leading-[1.7] text-u-text2 [&_b]:font-semibold [&_b]:text-u-text">
              {lede}
            </p>
          )}
        </div>
        {action && <div className="flex flex-wrap gap-3">{action}</div>}
      </div>
      {finding && (
        <ReportFinding label={findingLabel} className="mt-[26px]">
          {finding}
        </ReportFinding>
      )}
      {children}
    </section>
  );
}

/** The computed finding's banner: an eyebrow saying under what it holds, then the sentence. */
export function ReportFinding({
  label,
  aside,
  className,
  children,
}: {
  label?: string;
  /** Drawn at the eyebrow's right, such as the margin the finding rests on. */
  aside?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("rounded-[11px] bg-u-raised px-6 py-5 shadow-u-e1", className)}>
      {(label || aside) && (
        <div className="mb-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-1.5">
          {label && <div className="text-[9.5px] font-bold uppercase tracking-[0.1em] text-u-text3">{label}</div>}
          {aside}
        </div>
      )}
      <div className="text-[15px] leading-[1.65]">{children}</div>
    </div>
  );
}

/** The figure a finding leads with, in the accent so the eye lands on it before the prose. */
export function Figure({ children }: { children: ReactNode }) {
  return <b className="font-bold text-u-accent">{children}</b>;
}
