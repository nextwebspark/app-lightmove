import type { InputHTMLAttributes, ReactNode } from "react";
import { cn } from "../../../lib/cn";
import { Icon, ICONS } from "../../../components/layout/Icon";

const INLINE =
  "w-full border-b border-transparent bg-transparent py-1 font-mono text-[13.5px] font-medium text-text outline-none transition " +
  "hover:border-line focus:border-sky";

/**
 * The template editor's field kit — what the Position wizard's own kit left behind when the brief
 * moved to the UNCAVA palette. The editor keeps the app's amber tokens, as every Settings screen does.
 */

/** The section heading pattern the editor repeats: 15px title + a quiet mono aside. */
export function SectionHeading({ title, aside }: { title: string; aside?: string }) {
  return (
    <div className="mb-3 flex items-baseline gap-2">
      <span className="text-[15px] font-semibold text-text">{title}</span>
      {aside && <span className="font-mono text-[11.5px] text-text3">{aside}</span>}
    </div>
  );
}

/** The underline-on-hover inline input — borderless until pointed at, sky underline when focused. */
export function InlineInput({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={cn(INLINE, className)} />;
}

/** The uppercase column heading the editor's grid tables use. */
export function ColumnLabel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "font-mono text-[10px] font-semibold uppercase tracking-[0.08em] text-text3",
        className,
      )}
    >
      {children}
    </span>
  );
}

/**
 * The segmented button group the editor uses wherever a choice is short and worth seeing all of at
 * once — annual/monthly, required/preferred, monthly/yearly.
 */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  label,
  accent = "sky",
  size = "md",
  className,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
  accent?: "sky" | "amber" | "red";
  /** "sm" for the grid tables, where two buttons share a 150px column and must not wrap. */
  size?: "sm" | "md";
  className?: string;
}) {
  const active: Record<"sky" | "amber" | "red", string> = {
    sky: "border-sky bg-sky-dim text-sky",
    amber: "border-amber-btn bg-amber-dim text-amber",
    red: "border-red bg-red-dim text-red",
  };
  return (
    <div role="group" aria-label={label} className={cn("flex flex-wrap gap-1.5", className)}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
          className={cn(
            "rounded-lg border font-semibold transition",
            size === "sm" ? "px-2 py-1 text-[11px]" : "px-3 py-1.5 text-xs",
            option.value === value
              ? active[accent]
              : "border-line bg-panel text-text3 hover:border-text3 hover:text-text2",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** The dashed "add another" affordance every list in the editor ends with. */
export function AddRowButton({
  children,
  onClick,
  className,
}: {
  children: ReactNode;
  onClick: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "rounded-lg border border-dashed border-line px-3.5 py-2 text-xs font-semibold text-sky",
        "transition hover:border-sky hover:bg-sky-dim",
        className,
      )}
    >
      {children}
    </button>
  );
}

/** The ✕ that removes one row of a list. */
export function RemoveRowButton({
  label,
  onClick,
  className,
}: {
  label: string;
  onClick: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={cn("rounded p-1 text-text3 transition hover:text-red", className)}
    >
      <Icon d={ICONS.close} size={12} />
    </button>
  );
}
