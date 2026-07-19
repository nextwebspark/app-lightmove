import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";
import { cn } from "../../../lib/cn";
import { formatDate } from "../../../lib/format";

const INLINE =
  "w-full border-b border-transparent bg-transparent py-1 font-mono text-[13.5px] font-medium text-text outline-none transition " +
  "hover:border-line focus:border-sky disabled:hover:border-transparent";

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
 * A date field that reads as "15 Sep 2026" (not the browser's dd/mm/yyyy): the formatted value shows
 * on top of a transparent native date input, so a click opens the native picker and keyboard entry
 * still works — no date-picker dependency.
 */
export function FormattedDateField({
  value,
  onChange,
  disabled,
}: {
  value: string | null;
  onChange: (value: string | null) => void;
  disabled?: boolean;
}) {
  return (
    <div className="relative">
      <span
        className={cn(
          "block border-b border-transparent py-1 font-mono text-[13.5px] font-medium transition",
          disabled ? "text-text" : "cursor-pointer hover:border-line",
          value ? "text-text" : "text-text3",
        )}
      >
        {value ? formatDate(value) : "Set a date"}
      </span>
      <input
        type="date"
        disabled={disabled}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value || null)}
        // A native date input only opens its calendar from the (here invisible) picker icon; opening it
        // explicitly on any click makes the whole formatted field editable, not just that far corner.
        onClick={(e) => {
          try {
            e.currentTarget.showPicker?.();
          } catch {
            /* showPicker throws outside a user gesture on some engines — the click itself still focuses */
          }
        }}
        aria-label="Target start date"
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
      />
    </div>
  );
}
