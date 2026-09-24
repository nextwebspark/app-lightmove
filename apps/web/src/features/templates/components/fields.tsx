import type { InputHTMLAttributes, ReactNode } from "react";
import { cn } from "../../../lib/cn";
import { Icon, ICONS } from "../../../components/layout/Icon";

const INLINE =
  "w-full border-b border-transparent bg-transparent py-1 font-mono text-[13.5px] font-medium text-u-text outline-none transition " +
  "hover:border-u-border-strong focus:border-u-accent";

/**
 * The template editor's field kit — what the Position wizard's own kit left behind when the brief
 * moved to its own underline fields.
 */

/** The section heading pattern the editor repeats: 15px title + a quiet mono aside. */
export function SectionHeading({ title, aside }: { title: string; aside?: string }) {
  return (
    <div className="mb-3 flex items-baseline gap-2">
      <span className="text-[15px] font-semibold text-u-text">{title}</span>
      {aside && <span className="font-mono text-[11.5px] text-u-text3">{aside}</span>}
    </div>
  );
}

/** The underline-on-hover inline input — borderless until pointed at, accent underline when focused. */
export function InlineInput({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={cn(INLINE, className)} />;
}

/** The uppercase column heading the editor's grid tables use. */
export function ColumnLabel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "font-mono text-[10px] font-semibold uppercase tracking-[0.08em] text-u-text3",
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
  accent = "accent",
  size = "md",
  className,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
  accent?: "accent" | "signal" | "offlimits";
  /** "sm" for the grid tables, where two buttons share a 150px column and must not wrap. */
  size?: "sm" | "md";
  className?: string;
}) {
  const active: Record<"accent" | "signal" | "offlimits", string> = {
    accent: "border-u-accent bg-u-accent-tint text-u-accent",
    signal: "border-u-signal bg-u-signal-tint text-u-signal",
    offlimits: "border-u-offlimits bg-u-offlimits-tint text-u-offlimits",
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
              : "border-u-border-strong bg-u-surface text-u-text3 hover:border-u-text3 hover:text-u-text2",
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
        "rounded-lg border border-dashed border-u-border-strong px-3.5 py-2 text-xs font-semibold text-u-accent",
        "transition hover:border-u-accent hover:bg-u-accent-tint",
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
      className={cn("rounded p-1 text-u-text3 transition hover:text-u-offlimits", className)}
    >
      <Icon d={ICONS.close} size={12} />
    </button>
  );
}
