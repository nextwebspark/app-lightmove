import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * The read-only half of a detail drawer: a mono section heading, then prose or a two-column label/value grid,
 * shared by the company and executive panels.
 */

export function DrawerSection({
  title,
  action,
  children,
}: {
  title: string;
  /** A control belonging to the heading rather than the body — a save, a count, a link. */
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="border-b border-u-border py-4 last:border-b-0">
      <div className="mb-2.5 flex items-baseline justify-between gap-3">
        <h3 className="font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-u-text3">
          {title}
        </h3>
        {action}
      </div>
      {children}
    </section>
  );
}

/** A two-column grid of {@link DetailTile}s, which is how both mockup panels lay out their figures. */
export function DetailGrid({ wide = false, children }: { wide?: boolean; children: ReactNode }) {
  const grid = <div className={cn("grid grid-cols-2 gap-x-3 gap-y-2.5", wide && "@xl:grid-cols-4")}>{children}</div>;
  return wide ? <div className="@container">{grid}</div> : grid;
}

/**
 * One labelled figure. An em dash rather than an empty tile for a fact nobody has established — a
 * blank box reads as a rendering fault, and "not known" is itself worth showing on a research screen.
 */
export function DetailTile({
  label,
  value,
  full,
  badge,
}: {
  label: string;
  value: ReactNode;
  /** Spans both columns, for a value that is a sentence rather than a figure. */
  full?: boolean;
  /** A small marker beside the label — an AI-inferred flag, say — rather than part of the value. */
  badge?: ReactNode;
}) {
  const empty = value === null || value === undefined || value === "";
  return (
    <div className={cn("min-w-0", full && "col-span-2")}>
      <div className="mb-1 flex items-center gap-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
        {label}
        {badge}
      </div>
      <div
        className={cn(
          "truncate rounded-[7px] border border-u-border bg-u-raised px-2.5 py-[7px] font-mono text-[13px]",
          empty ? "text-u-text3" : "text-u-text",
        )}
      >
        {empty ? "—" : value}
      </div>
    </div>
  );
}

/** A pill — a stage, a source, a status. The same shape the grids draw, so the two agree on sight. */
export function DetailPill({ label, className }: { label: string; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-[5px] px-[7px] py-[2px] font-mono text-[9.5px] font-bold uppercase tracking-[0.06em]",
        className ?? "bg-u-border text-u-text2",
      )}
    >
      {label}
    </span>
  );
}

/** A titled block of a details rail, under a hairline: Outreach's Candidate details, the record page's Details. */
export function DetailBlock({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-2 border-t border-u-border pt-4">
      <div className="flex items-center">
        <h3 className="type-micro-label text-u-text3">{title}</h3>
        {aside && <span className="ms-auto">{aside}</span>}
      </div>
      {children}
    </section>
  );
}

/** One label and its value in a {@link DetailBlock}. */
export function DetailRow({ label, mono = false, children }: { label: string; mono?: boolean; children: ReactNode }) {
  return (
    <div className="flex gap-3 text-note">
      <span className="w-24 flex-none text-u-text3">{label}</span>
      <div className={cn("min-w-0 flex-1 text-u-text2", mono && "font-mono")}>{children}</div>
    </div>
  );
}
