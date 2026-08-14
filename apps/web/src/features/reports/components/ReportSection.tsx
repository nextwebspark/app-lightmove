import type { ReactNode } from "react";

/**
 * One numbered section of the report (Project.dc.html, Reports screen): the sky ordinal, the mono
 * eyebrow, a finding as the heading, and a line of explanation above the section's own charts.
 */
export function ReportSection({
  id,
  ordinal,
  eyebrow,
  heading,
  lede,
  children,
}: {
  id: string;
  ordinal: string;
  eyebrow: string;
  heading: ReactNode;
  lede?: string;
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-6 border-t border-line pt-[34px] first:border-0 first:pt-0">
      <div className="mb-3 flex items-center gap-[9px]">
        <span className="font-mono text-[10px] font-bold tracking-[0.06em] text-sky">{ordinal}</span>
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
          {eyebrow}
        </span>
      </div>
      <h3 className="max-w-[760px] text-[17px] font-semibold leading-[1.45]">{heading}</h3>
      {lede && <p className="mt-2 max-w-[760px] text-[13px] leading-[1.6] text-text2">{lede}</p>}
      <div className="mt-5">{children}</div>
    </section>
  );
}
