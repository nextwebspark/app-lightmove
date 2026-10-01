import type { ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useRadioGroupKeys } from "./useRadioGroupKeys";

export interface TabOption<TValue extends string> {
  value: TValue;
  label: string;
  icon?: ReactNode;
  /** A small count beside the label; nothing is drawn for zero. */
  count?: number;
}

/**
 * A row of tabs as assistive tech expects them: one tab stop on the chosen tab, the arrows move the
 * choice, and each tab names the panel it controls. The panel is the caller's — give it
 * `tabPanelProps` with the same `idPrefix` and value.
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

  return (
    <div ref={keys.ref} role="tablist" aria-label={label} onKeyDown={keys.onKeyDown} className={cn("flex gap-4", className)}>
      {tabs.map((tab) => {
        const selected = tab.value === value;
        return (
          <button
            key={tab.value}
            type="button"
            role="tab"
            id={`${idPrefix}-tab-${tab.value}`}
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel-${tab.value}`}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.value)}
            className={cn(
              "-mb-px flex items-center gap-1.5 border-b-2 pb-2 text-[13px] font-semibold",
              selected ? "border-u-accent text-u-text" : "border-transparent text-u-text3 hover:text-u-text2",
            )}
          >
            {tab.icon}
            {tab.label}
            {tab.count !== undefined && tab.count > 0 && (
              <span className="rounded-full bg-u-raised px-1.5 font-mono text-[10.5px] text-u-text3">{tab.count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
