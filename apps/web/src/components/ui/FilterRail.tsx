import type { ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * A screen's filter rail: a static column beside the results from `lg`, and below it an overlay over
 * them with a scrim, because a ~300px rail beside a table does not fit a phone.
 */
export function FilterRail({
  label,
  onClose,
  className,
  children,
}: {
  label: string;
  onClose: () => void;
  /** Width overrides; every one must keep its `lg:` prefix where the default has one. */
  className?: string;
  children: ReactNode;
}) {
  return (
    <>
      <div className="fixed inset-0 z-[90] bg-u-scrim lg:hidden" onClick={onClose} />
      <div
        role="region"
        aria-label={label}
        className={cn(
          "flex flex-col border-u-border bg-u-surface",
          "fixed inset-y-0 start-0 z-[95] w-[min(300px,86vw)] border-e shadow-u-e3",
          "lg:static lg:z-auto lg:w-[21%] lg:min-w-[280px] lg:max-w-[330px] lg:shrink-0 lg:shadow-none",
          className,
        )}
      >
        <div className="flex items-center justify-between border-b border-u-border px-4 py-2.5 lg:hidden">
          <span className="text-eyebrow font-semibold uppercase tracking-[0.08em] text-u-text3">{label}</span>
          <button
            type="button"
            onClick={onClose}
            aria-label="Hide filters"
            className="flex size-8 items-center justify-center rounded-[6px] text-u-text3 transition hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={16} />
          </button>
        </div>
        {children}
      </div>
    </>
  );
}

/** The toolbar switch that shows and hides a {@link FilterRail}, with the count of axes in force. */
export function FilterRailToggle({
  open,
  onToggle,
  badge,
}: {
  open: boolean;
  onToggle: () => void;
  badge?: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] p-2 text-note text-u-text3 transition hover:bg-u-surface hover:text-u-text"
    >
      <Icon d="M3 4h18l-7 8v6l-4 2v-8L3 4Z" size={14} className="flex-none" />
      {open ? "Hide Filters" : "Show Filters"}
      {badge}
    </button>
  );
}
