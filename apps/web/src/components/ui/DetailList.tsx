import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * The read-only half of a detail drawer, in the shape the mockups' two panels share: an uppercase
 * mono section heading, and beneath it either prose or a two-column grid of label/value tiles.
 *
 * <p>Shared rather than written twice because the company panel and the executive panel are the same
 * furniture around different facts — and a panel that laid its labels out slightly differently from
 * the one beside it would read as a different product.
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
    <section className="border-b border-line-soft py-4 last:border-b-0">
      <div className="mb-2.5 flex items-baseline justify-between gap-3">
        <h3 className="font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-text3">
          {title}
        </h3>
        {action}
      </div>
      {children}
    </section>
  );
}

/** A two-column grid of {@link DetailTile}s, which is how both mockup panels lay out their figures. */
export function DetailGrid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-2 gap-x-3 gap-y-2.5">{children}</div>;
}

/**
 * One labelled figure. An em dash rather than an empty tile for a fact nobody has established — a
 * blank box reads as a rendering fault, and "not known" is itself worth showing on a research screen.
 */
export function DetailTile({
  label,
  value,
  full,
}: {
  label: string;
  value: ReactNode;
  /** Spans both columns, for a value that is a sentence rather than a figure. */
  full?: boolean;
}) {
  const empty = value === null || value === undefined || value === "";
  return (
    <div className={cn("min-w-0", full && "col-span-2")}>
      <div className="mb-1 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-text3">
        {label}
      </div>
      <div
        className={cn(
          "truncate rounded-[7px] border border-line-soft bg-panel2 px-2.5 py-[7px] font-mono text-[13px]",
          empty ? "text-text3" : "text-text",
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
        className ?? "bg-line-soft text-text2",
      )}
    >
      {label}
    </span>
  );
}
