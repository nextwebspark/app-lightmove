import type { ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * A drawer section that folds. The heading keeps {@link DrawerSection}'s typography so a panel that
 * mixes the two still reads as one; what it adds is a chevron, a count, and a one-line summary that
 * stands in for the body while it is folded — so a closed section still answers the question a
 * reader would have opened it for.
 *
 * <p>The body stays mounted while folded, animated shut with the grid-rows technique so nothing is
 * measured, and made inert so a folded control is neither tabbed into nor read out.
 */
export function CollapsibleSection({
  id,
  title,
  count,
  summary,
  action,
  open,
  onToggle,
  children,
}: {
  id: string;
  title: string;
  /** A small figure beside the title — how many posts, how many columns. */
  count?: number | string;
  /** What the folded header shows in place of the body. Ignored while open. */
  summary?: ReactNode;
  /** A control belonging to the heading rather than the body; sits outside the toggle. */
  action?: ReactNode;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  const bodyId = `${id}-section-body`;
  return (
    <section className="group border-b border-line-soft last:border-b-0">
      <div className="flex items-center gap-2">
        <button
          type="button"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={onToggle}
          className="-mx-2 flex min-w-0 flex-1 items-center gap-2 rounded-md px-2 py-3.5 text-left transition hover:bg-panel2"
        >
          <Icon
            d={ICONS.chevronDown}
            size={13}
            className={cn(
              "flex-none text-text3 transition-transform duration-200 motion-reduce:transition-none",
              !open && "-rotate-90",
            )}
          />
          <span className="flex-none font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-text3">
            {title}
          </span>
          {count !== undefined && (
            <span className="flex-none rounded-[4px] bg-panel2 px-1.5 py-px font-mono text-[9.5px] font-semibold text-text3">
              {count}
            </span>
          )}
          {!open && summary && (
            <span className="min-w-0 truncate ps-1 font-mono text-[11.5px] text-text3">
              {summary}
            </span>
          )}
        </button>
        {action && <div className="flex-none">{action}</div>}
      </div>
      <div
        id={bodyId}
        aria-hidden={!open}
        inert={!open}
        className="grid transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none"
        style={{ gridTemplateRows: open ? "1fr" : "0fr" }}
      >
        <div className="min-h-0 overflow-hidden">
          <div className="pb-4">{children}</div>
        </div>
      </div>
    </section>
  );
}
