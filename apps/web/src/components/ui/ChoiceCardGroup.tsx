import type { ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useRadioGroupKeys } from "./useRadioGroupKeys";

export interface ChoiceCardOption<TValue extends string> {
  value: TValue;
  title: string;
  body: string;
  icon?: ReactNode;
}

/**
 * A choice made between a few described cards — Standard or Confidential, Mapping or Search. The
 * same radio group as {@link SegmentedControl}, for options that need a sentence each.
 */
export function ChoiceCardGroup<TValue extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: {
  /** Names the group for a screen reader. */
  label: string;
  options: readonly ChoiceCardOption<TValue>[];
  value: TValue;
  onChange: (value: TValue) => void;
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
      className={cn("grid grid-cols-1 gap-3 sm:grid-cols-2", className)}
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
              "flex items-start justify-between gap-4 rounded-[10px] border px-4 py-3.5 text-start transition",
              selected ? "border-u-accent bg-u-accent-tint" : "border-u-border-strong bg-u-bg hover:border-u-text3",
            )}
          >
            <span className="min-w-0">
              {option.icon && <span className="mb-2 block text-u-accent">{option.icon}</span>}
              <span className={cn("block type-heading", selected ? "text-u-text" : "text-u-text2")}>
                {option.title}
              </span>
              <span className="mt-1 block text-note text-u-text3">{option.body}</span>
            </span>
            <span
              aria-hidden="true"
              className={cn(
                "mt-1 size-3 flex-none rounded-full border",
                selected ? "border-u-accent bg-u-accent" : "border-u-border-strong bg-transparent",
              )}
            />
          </button>
        );
      })}
    </div>
  );
}
