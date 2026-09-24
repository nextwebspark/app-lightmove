import type { ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useRadioGroupKeys } from "./useRadioGroupKeys";

export interface SegmentedOption<TValue extends string> {
  value: TValue;
  label: string;
  icon?: ReactNode;
}

/** `uncava` is the Reports palette, where the chosen option is a small solid-accent button. */
export type SegmentedVariant = "default" | "uncava";

const GROUP_CLASS: Record<SegmentedVariant, string> = {
  default: "rounded-[6px] border-line bg-panel p-0.5",
  uncava: "gap-0.5 rounded-[8px] border-u-border-strong bg-u-bg p-[3px]",
};

const OPTION_CLASS: Record<SegmentedVariant, { base: string; selected: string; idle: string }> = {
  default: {
    base: "rounded-[4px] px-2.5 py-1.5 text-[12.5px] font-medium",
    selected: "bg-amber-dim text-text",
    idle: "text-text3 hover:text-text",
  },
  uncava: {
    base: "rounded-[6px] px-3 py-1.5 text-[11.5px] font-semibold",
    selected: "bg-u-accent-solid text-white",
    idle: "text-u-text2 hover:text-u-text",
  },
};

/**
 * A row of mutually exclusive choices — Table | Map — as one control. A radio group to assistive
 * tech, because that is what it is: one value, several buttons, exactly one pressed.
 *
 * <p>Which is why the keyboard is the radio group's and not a row of buttons': one tab stop on the
 * chosen option, and the arrows move the choice. A reader told "radio, 1 of 2" reaches for the arrow
 * keys, and a shared primitive that ignores them teaches the next screen to ignore them too.
 */
export function SegmentedControl<TValue extends string>({
  label,
  options,
  value,
  onChange,
  variant = "default",
  className,
}: {
  /** Names the group for a screen reader; the buttons carry their own labels. */
  label: string;
  options: readonly SegmentedOption<TValue>[];
  value: TValue;
  onChange: (value: TValue) => void;
  variant?: SegmentedVariant;
  className?: string;
}) {
  const keys = useRadioGroupKeys(
    options.map((option) => option.value),
    value,
    onChange,
  );

  return (
    <div
      ref={keys.ref}
      role="radiogroup"
      aria-label={label}
      onKeyDown={keys.onKeyDown}
      className={cn("inline-flex flex-none items-center border", GROUP_CLASS[variant], className)}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(option.value)}
            className={cn(
              "inline-flex cursor-pointer items-center gap-1.5 whitespace-nowrap font-sans transition",
              OPTION_CLASS[variant].base,
              selected ? OPTION_CLASS[variant].selected : OPTION_CLASS[variant].idle,
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
