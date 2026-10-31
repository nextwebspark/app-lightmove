import type { ReactNode } from "react";

/** A labelled section of the panel: the label and a hint on one line, then the field. */
export function FieldSection({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <section className="mt-[18px]">
      <div className="mb-[7px] flex items-baseline justify-between gap-2">
        <h2 className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">{label}</h2>
        {hint && <span className="text-[10.5px] text-u-text3">{hint}</span>}
      </div>
      {children}
    </section>
  );
}
