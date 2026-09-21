import { useRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type KeyboardEvent, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/**
 * The brief's field kit, in the UNCAVA palette (`u-*` tokens only). The screen draws a field as a
 * small uppercase eyebrow over a hairline rather than a boxed input, a choice as a row of pills, and
 * a figure in the numeral face — this is that vocabulary, stated once so five steps cannot drift.
 */

export function Eyebrow({
  children,
  tone = "quiet",
  className,
}: {
  children: ReactNode;
  /** `inferred` is the purple UNCAVA reserves for what a machine drafted rather than a person typed. */
  tone?: "quiet" | "inferred";
  className?: string;
}) {
  return (
    <span
      className={cn(
        "block text-[10px] font-semibold uppercase tracking-[0.12em]",
        tone === "inferred" ? "text-u-inferred" : "text-u-text3",
        className,
      )}
    >
      {children}
    </span>
  );
}

/** An eyebrow over a control, with room for a note or a toggle at the eyebrow's other end. */
export function FieldBlock({
  label,
  tone,
  aside,
  children,
  className,
}: {
  label: ReactNode;
  tone?: "quiet" | "inferred";
  aside?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("min-w-0", className)}>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <Eyebrow tone={tone}>{label}</Eyebrow>
        {aside}
      </div>
      {children}
    </div>
  );
}

const UNDERLINE =
  "w-full border-b border-u-border bg-transparent py-2 text-[15px] text-u-text outline-none transition " +
  "placeholder:text-u-text3 focus:border-u-accent";

/** Text on a hairline — the brief's plain field. */
export function UnderlineField({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={cn(UNDERLINE, className)} />;
}

export interface ChipOption<T extends string> {
  value: T;
  label: string;
  /** The colour a chosen chip wears; `offlimits` is the red of a gate that narrows the field. */
  tone?: "accent" | "offlimits";
}

const CHIP_TONES = {
  accent: "border-u-accent bg-u-accent-tint text-u-accent",
  offlimits: "border-u-offlimits bg-u-offlimits-tint text-u-offlimits",
} as const;

/**
 * A row of pills, one chosen — employment type, seniority, a bonus basis. A radio group to assistive
 * tech, with the radio group's keyboard: one tab stop, arrows move the choice. With `allowClear` a
 * second press on the chosen pill clears it, which is how an optional field is unset.
 */
export function ChipGroup<T extends string>({
  label,
  options,
  value,
  onChange,
  allowClear = false,
  size = "md",
  className,
}: {
  label: string;
  options: readonly ChipOption<T>[];
  value: T | null;
  onChange: (value: T | null) => void;
  allowClear?: boolean;
  size?: "sm" | "md";
  className?: string;
}) {
  const groupRef = useRef<HTMLDivElement>(null);
  const chosenIndex = options.findIndex((option) => option.value === value);
  const tabStop = chosenIndex >= 0 ? chosenIndex : 0;

  const step = (event: KeyboardEvent<HTMLDivElement>) => {
    const forward = event.key === "ArrowRight" || event.key === "ArrowDown";
    const back = event.key === "ArrowLeft" || event.key === "ArrowUp";
    if (!forward && !back) return;
    event.preventDefault();
    const from = chosenIndex >= 0 ? chosenIndex : back ? 0 : -1;
    const next = options[(from + (forward ? 1 : -1) + options.length) % options.length];
    onChange(next.value);
    groupRef.current?.querySelectorAll("button")[options.indexOf(next)]?.focus();
  };

  return (
    <div
      ref={groupRef}
      role="radiogroup"
      aria-label={label}
      onKeyDown={step}
      className={cn("flex flex-wrap gap-2", className)}
    >
      {options.map((option, index) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={index === tabStop ? 0 : -1}
            onClick={() => onChange(selected && allowClear ? null : option.value)}
            className={cn(
              "rounded-full border font-medium transition",
              size === "sm" ? "px-2.5 py-[3px] text-[11.5px]" : "px-3.5 py-1.5 text-[13px]",
              selected
                ? CHIP_TONES[option.tone ?? "accent"]
                : "border-u-border-strong bg-u-bg text-u-text2 hover:border-u-text3 hover:text-u-text",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * The offered options plus the stored value where the two disagree, so a brief already stating
 * something nobody offers — an advisory engagement, a bonus in months of base — keeps stating it
 * rather than being cleared by a control with no pill for it.
 */
export function withRecorded<T extends string>(
  options: readonly ChipOption<T>[],
  value: T | null,
  labelOf: (value: T) => string,
): ChipOption<T>[] {
  if (value === null || options.some((option) => option.value === value)) return [...options];
  return [...options, { value, label: `${labelOf(value)} (as recorded)` }];
}

/** One of two cards a choice is made between — Standard or Confidential — with its dot on the end. */
export function ChoiceCard({
  title,
  body,
  selected,
  onSelect,
}: {
  title: string;
  body: string;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      className={cn(
        "flex items-start justify-between gap-4 rounded-[10px] border px-4 py-3.5 text-start transition",
        selected ? "border-u-accent bg-u-accent-tint" : "border-u-border-strong bg-u-bg hover:border-u-text3",
      )}
    >
      <span className="min-w-0">
        <span className={cn("block text-[14px] font-semibold", selected ? "text-u-text" : "text-u-text2")}>
          {title}
        </span>
        <span className="mt-1 block text-[11.5px] text-u-text3">{body}</span>
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
}

/** The grey dot that removes one item of a list. */
export function RemoveDot({
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
      className={cn(
        "grid size-[18px] flex-none place-items-center rounded-full bg-u-text3/70 text-white transition hover:bg-u-offlimits",
        className,
      )}
    >
      <Icon d={ICONS.close} size={9} className="[stroke-width:3]" />
    </button>
  );
}

/** A responsibility, a priority — short text as a removable token. `marker` sits before the ✕. */
export function TokenChip({
  label,
  marker,
  onRemove,
}: {
  label: string;
  marker?: ReactNode;
  onRemove: () => void;
}) {
  return (
    <span className="inline-flex items-center gap-2 rounded-[6px] bg-u-raised py-1.5 pe-2 ps-3 text-[12.5px] font-medium text-u-text">
      {label}
      {marker}
      <RemoveDot label={`Remove ${label}`} onClick={onRemove} />
    </span>
  );
}

/** The surface a step groups a table or a figure inside. */
export function BriefPanel({
  children,
  className,
  tone = "surface",
}: {
  children: ReactNode;
  className?: string;
  /** `accent` is the tinted, ringed panel the package total sits in. */
  tone?: "surface" | "accent";
}) {
  return (
    <div
      className={cn(
        "rounded-[11px] px-5 py-5",
        tone === "accent" ? "border border-u-accent-ring bg-u-accent-tint" : "bg-u-surface shadow-u-e1",
        className,
      )}
    >
      {children}
    </div>
  );
}

type BriefButtonVariant = "primary" | "outline" | "link";

const BUTTON_BASE = "inline-flex items-center justify-center gap-2 text-[13px] font-semibold transition";

const BUTTON_CLASS: Record<BriefButtonVariant, string> = {
  primary:
    "rounded-[8px] border border-transparent bg-u-accent-solid px-4 py-2.5 text-white hover:brightness-110",
  outline:
    "rounded-[8px] border border-u-border-strong bg-transparent px-4 py-2.5 text-u-text2 hover:bg-u-raised hover:text-u-text",
  link: "border-transparent px-1 py-1 text-u-accent hover:underline",
};

/**
 * The outline button's own classes, for the controls that are links rather than buttons — walking
 * between steps is navigation, so it belongs in the URL. Shared rather than copied: a second spelling
 * of the same button is a second thing to keep in step with the palette.
 */
export const OUTLINE_LINK_CLASS = cn(BUTTON_BASE, BUTTON_CLASS.outline);

/** The brief's button: one filled per screen, outlines beside it, links for the small acts. */
export function BriefButton({
  variant = "primary",
  loading = false,
  disabled,
  className,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: BriefButtonVariant; loading?: boolean }) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cn(
        BUTTON_BASE,
        "disabled:cursor-not-allowed disabled:opacity-50",
        BUTTON_CLASS[variant],
        className,
      )}
    >
      {loading && <span className="size-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
      {children}
    </button>
  );
}

/** The uppercase heading over a table's column. */
export function ColumnHead({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span className={cn("text-[10px] font-semibold uppercase tracking-[0.1em] text-u-text3", className)}>
      {children}
    </span>
  );
}

const BADGE_TONES = {
  complete: "border-u-direct/40 bg-u-direct-tint text-u-direct",
  attention: "border-u-signal/50 bg-u-signal-tint text-u-signal",
  offlimits: "border-u-offlimits/40 bg-u-offlimits-tint text-u-offlimits",
  neutral: "border-u-border-strong bg-u-sunken text-u-text2",
} as const;

/** Complete, Needs attention, or a quiet note — tint plus saturated text, UNCAVA's one badge shape. */
export function StatusBadge({
  tone,
  children,
  className,
}: {
  tone: keyof typeof BADGE_TONES;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-[5px] border px-2 py-0.5 text-[11px] font-semibold",
        BADGE_TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/**
 * A number in the numeral face: money grouped as it is typed, so a seven-figure salary can be
 * checked by eye, or a plain count. A blank field is null.
 */
export function FigureInput({
  value,
  onChange,
  grouped = false,
  max,
  size = "md",
  className,
  ...rest
}: {
  value: number | null;
  onChange: (value: number | null) => void;
  /** Thousands separators while typing — money, never a percentage. */
  grouped?: boolean;
  max?: number;
  size?: "md" | "lg";
} & Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "max" | "size">) {
  return (
    <input
      {...rest}
      inputMode="numeric"
      value={value === null ? "" : grouped ? value.toLocaleString("en-GB") : String(value)}
      onChange={(event) => {
        const digits = event.target.value.replace(/[^\d]/g, "");
        if (!digits) return onChange(null);
        const figure = Number(digits);
        onChange(max !== undefined ? Math.min(figure, max) : figure);
      }}
      className={cn(
        "min-w-0 border-b border-u-border bg-transparent font-u-num text-u-text outline-none transition placeholder:text-u-text3 focus:border-u-accent",
        size === "lg" ? "py-1 text-[28px] font-medium tracking-[-0.02em]" : "py-1.5 text-[16px]",
        className,
      )}
    />
  );
}
