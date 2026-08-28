import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";
import { cn } from "../../../lib/cn";
import { Icon, ICONS } from "../../../components/layout/Icon";

const INLINE =
  "w-full border-b border-transparent bg-transparent py-1 font-mono text-[13.5px] font-medium text-text outline-none transition " +
  "hover:border-line focus:border-sky";

/** The section heading pattern the Position mockup repeats: 15px title + a quiet mono aside. */
export function SectionHeading({ title, aside }: { title: string; aside?: string }) {
  return (
    <div className="mb-3 flex items-baseline gap-2">
      <span className="text-[15px] font-semibold text-text">{title}</span>
      {aside && <span className="font-mono text-[11.5px] text-text3">{aside}</span>}
    </div>
  );
}

/** The mockup's uppercase micro-label over inline fields. */
export function MicroLabel({ children }: { children: ReactNode }) {
  return (
    <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
      {children}
    </span>
  );
}

/**
 * The mockup's underline-on-hover inline input — borderless until pointed at, sky underline when
 * focused. The Position screen's org and package grids are made of these.
 */
export function InlineInput({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={cn(INLINE, className)} />;
}

/** The select twin of {@link InlineInput} — same borderless, underline-on-hover treatment. */
export function InlineSelect({
  className,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...rest} className={cn(INLINE, className)}>
      {children}
    </select>
  );
}

/**
 * The plain-number sibling of {@link InlineInput}: digits only, no thousands separator (that is
 * {@code MoneyField}'s job). Used for counts and the bonus percentage. A blank field means null.
 */
export function NumberInput({
  value,
  onChange,
  max,
  suffix,
  className,
  ...rest
}: {
  value: number | null;
  onChange: (value: number | null) => void;
  max?: number;
  suffix?: string;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "max">) {
  const field = (
    <input
      {...rest}
      inputMode="numeric"
      value={value === null ? "" : String(value)}
      onChange={(e) => {
        const digits = e.target.value.replace(/[^\d]/g, "");
        if (!digits) return onChange(null);
        const n = Number(digits);
        onChange(max !== undefined ? Math.min(n, max) : n);
      }}
      className={cn(INLINE, suffix ? "flex-1" : "", className)}
    />
  );
  if (!suffix) return field;
  return (
    <span className="flex items-center gap-1">
      {field}
      <span className="font-mono text-[13.5px] text-text3">{suffix}</span>
    </span>
  );
}

/**
 * The money sibling of {@link NumberInput}: grouped with thousands separators as it is typed, because
 * a seven-figure salary typed as a bare run of digits cannot be checked by eye. A blank field is null.
 */
export function MoneyInput({
  value,
  onChange,
  className,
  ...rest
}: {
  value: number | null;
  onChange: (value: number | null) => void;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange">) {
  return (
    <input
      {...rest}
      inputMode="numeric"
      value={value === null ? "" : value.toLocaleString("en-GB")}
      onChange={(e) => {
        const digits = e.target.value.replace(/[^\d]/g, "");
        onChange(digits ? Number(digits) : null);
      }}
      className={cn(INLINE, className)}
    />
  );
}

/**
 * The wizard's field label — 12px sans, uppercase, over a bordered control. Distinct from the shared
 * {@code Field} in components/ui, which wears the auth screens' smaller mono micro-label; both are
 * transcriptions of their own mockup and neither is the other one scaled.
 */
export function StepField({
  label,
  hint,
  children,
  className,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={cn("block min-w-0", className)}>
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
        {label}
      </span>
      {children}
      {hint && <span className="mt-1.5 block font-mono text-[11px] text-text3">{hint}</span>}
    </label>
  );
}

/** The step-one input that shows a green check once it holds something. */
export function CheckedInput({ value, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  const filled = typeof value === "string" && value.trim().length > 0;
  return (
    <span className="relative block">
      <input
        {...rest}
        value={value}
        className={cn(
          "w-full rounded-lg border border-line bg-panel2 py-2.5 pl-3 pr-9 text-[13.5px] font-medium",
          "text-text outline-none transition focus:border-sky",
        )}
      />
      <Icon
        d={ICONS.checkCircle}
        size={16}
        className={cn(
          "pointer-events-none absolute end-3 top-3 transition-colors",
          filled ? "text-green" : "text-line",
        )}
      />
    </span>
  );
}

/** The mockup's sub-card: a quiet panel a step groups a table or a chip list inside. */
export function SubCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-4", className)}>
      {children}
    </div>
  );
}

/** The uppercase column heading the wizard's grid tables use. */
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
 * The segmented button group the wizard uses wherever a choice is short and worth seeing all of at
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

/** The dashed "add another" affordance every list on the wizard ends with. */
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
export function RemoveRowButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="rounded p-1 text-text3 transition hover:text-red"
    >
      <Icon d={ICONS.close} size={12} />
    </button>
  );
}
