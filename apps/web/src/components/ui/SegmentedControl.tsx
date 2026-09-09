import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

export interface SegmentedOption<TValue extends string> {
  value: TValue;
  label: string;
  icon?: ReactNode;
}

/**
 * A row of mutually exclusive choices — Table | Map — as one control. A radio group to assistive
 * tech, because that is what it is: one value, several buttons, exactly one pressed.
 */
export function SegmentedControl<TValue extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: {
  /** Names the group for a screen reader; the buttons carry their own labels. */
  label: string;
  options: readonly SegmentedOption<TValue>[];
  value: TValue;
  onChange: (value: TValue) => void;
  className?: string;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn(
        "inline-flex flex-none items-center rounded-[6px] border border-line bg-panel p-0.5",
        className,
      )}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[4px] px-2.5 py-1.5 font-sans text-[12.5px] font-medium transition",
              selected ? "bg-amber-dim text-text" : "text-text3 hover:text-text",
            )}
          >
            {option.icon}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
