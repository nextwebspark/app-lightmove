import { Fragment, useEffect, useState, type ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useRadioGroupKeys } from "./useRadioGroupKeys";

export interface TabOption<TValue extends string> {
  value: TValue;
  label: string;
  icon?: ReactNode;
  /** A small count beside the label; nothing is drawn for zero. */
  count?: number;
  /** `accent` asks for attention, as a waiting suggestion does. */
  countTone?: "muted" | "accent";
  /** A rule after this tab, setting it apart from the ones that follow. */
  dividerAfter?: boolean;
}

/**
 * A row of tabs as assistive tech expects them: one tab stop on the chosen tab, the arrows move the
 * choice, and each tab names the panel it controls. The panel is the caller's — give it
 * `tabPanelProps` with the same `idPrefix` and value.
 *
 * <p>A row wider than its box scrolls sideways rather than wrapping — a second line of tabs moves the
 * panel under it every time the drawer is narrower — with the clipped edge faded and the chosen tab
 * kept in view.
 */
export function TabList<TValue extends string>({
  label,
  idPrefix,
  tabs,
  value,
  onChange,
  className,
}: {
  label: string;
  idPrefix: string;
  tabs: readonly TabOption<TValue>[];
  value: TValue;
  onChange: (value: TValue) => void;
  className?: string;
}) {
  const keys = useRadioGroupKeys(
    tabs.map((tab) => tab.value),
    value,
    onChange,
  );
  const row = keys.ref;
  const [clipped, setClipped] = useState({ start: false, end: false });

  useEffect(() => {
    const box = row.current;
    if (!box) return;
    const measure = () =>
      setClipped({
        start: box.scrollLeft > 1,
        end: box.scrollLeft + box.clientWidth < box.scrollWidth - 1,
      });
    measure();
    box.addEventListener("scroll", measure, { passive: true });
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(measure);
    observer?.observe(box);
    return () => {
      box.removeEventListener("scroll", measure);
      observer?.disconnect();
    };
  }, [row]);

  useEffect(() => {
    row.current
      ?.querySelector<HTMLElement>('[aria-selected="true"]')
      ?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
  }, [row, value]);

  return (
    <div
      ref={row}
      role="tablist"
      aria-label={label}
      onKeyDown={keys.onKeyDown}
      className={cn(
        "flex gap-4 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
        clipped.end && !clipped.start && "[mask-image:linear-gradient(to_right,black_calc(100%-32px),transparent)]",
        clipped.start && !clipped.end && "[mask-image:linear-gradient(to_left,black_calc(100%-32px),transparent)]",
        clipped.start &&
          clipped.end &&
          "[mask-image:linear-gradient(to_right,transparent,black_32px,black_calc(100%-32px),transparent)]",
        className,
      )}
    >
      {tabs.map((tab) => {
        const selected = tab.value === value;
        return (
          <Fragment key={tab.value}>
            <button
              type="button"
              role="tab"
              id={`${idPrefix}-tab-${tab.value}`}
              aria-selected={selected}
              aria-controls={`${idPrefix}-panel-${tab.value}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(tab.value)}
              className={cn(
                "-mb-px flex flex-none items-center gap-1 whitespace-nowrap border-b-2 pb-2 text-[13px] font-semibold transition-colors",
                selected ? "border-u-accent text-u-text" : "border-transparent text-u-text3 hover:text-u-text2",
              )}
            >
              {tab.icon}
              {tab.label}
              {tab.count !== undefined && tab.count > 0 && (
                <span
                  className={cn(
                    "rounded-full px-1.5 font-mono text-[10.5px]",
                    tab.countTone === "accent" ? "bg-u-accent-solid font-semibold text-white" : "bg-u-raised text-u-text3",
                  )}
                >
                  {tab.count}
                </span>
              )}
            </button>
            {tab.dividerAfter && <span aria-hidden="true" className="mb-2 w-px flex-none self-stretch bg-u-border" />}
          </Fragment>
        );
      })}
    </div>
  );
}
