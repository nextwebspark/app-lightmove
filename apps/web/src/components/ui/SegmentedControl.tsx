import { useRef, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "../../lib/cn";

export interface SegmentedOption<TValue extends string> {
  value: TValue;
  label: string;
  icon?: ReactNode;
}

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
  className,
}: {
  /** Names the group for a screen reader; the buttons carry their own labels. */
  label: string;
  options: readonly SegmentedOption<TValue>[];
  value: TValue;
  onChange: (value: TValue) => void;
  className?: string;
}) {
  const groupRef = useRef<HTMLDivElement>(null);

  const step = (event: KeyboardEvent<HTMLDivElement>) => {
    const forward = event.key === "ArrowRight" || event.key === "ArrowDown";
    const back = event.key === "ArrowLeft" || event.key === "ArrowUp";
    if (!forward && !back) return;
    event.preventDefault();
    const index = options.findIndex((option) => option.value === value);
    const next = options[(index + (forward ? 1 : -1) + options.length) % options.length];
    onChange(next.value);
    // The chosen option is the tab stop, so the focus follows the choice as the pattern requires.
    groupRef.current?.querySelectorAll("button")[options.indexOf(next)]?.focus();
  };

  return (
    <div
      ref={groupRef}
      role="radiogroup"
      aria-label={label}
      onKeyDown={step}
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
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(option.value)}
            className={cn(
              "inline-flex cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-[4px] px-2.5 py-1.5 font-sans text-[12.5px] font-medium transition",
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
