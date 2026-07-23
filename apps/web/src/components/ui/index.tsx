import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";
import { cn } from "../../lib/cn";

export { Avatar } from "./Avatar";
export { HealthDot, StagePill, stageLabel } from "./Badge";
export { DateInput } from "./DateInput";
export { Drawer } from "./Drawer";
export { EmptyState } from "./EmptyState";
export { Modal } from "./Modal";
export { Skeleton, TableSkeleton } from "./Skeleton";
export { ToastProvider, useToast } from "./Toast";

/**
 * The handful of primitives every auth screen is built from.
 *
 * Each is a direct transcription of the mockups' inline styles into Tailwind utilities over the same
 * tokens, so a screen assembled from these matches the design without anyone eyeballing it.
 *
 * One file, because there are six of them and they are only meaningful together. When this grows past
 * a screenful, split it — not before.
 *
 * Every `className` goes through {@link cn}, so a caller can override any default. Concatenating
 * instead would let the stylesheet's ordering decide who wins, and it does not decide in the caller's
 * favour: an invite row asking for a 130px select got a full-width one.
 */

// ── Button ──────────────────────────────────────────────────────────────────

type ButtonVariant = "primary" | "secondary" | "ghost";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
}

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  // The amber call-to-action. Its label is near-black in both themes — see --color-on-amber.
  primary:
    "bg-amber-btn border border-amber-btn text-on-amber font-semibold hover:brightness-105 " +
    "disabled:opacity-50 disabled:hover:brightness-100",
  secondary:
    "bg-panel border border-line text-text2 font-medium hover:text-text hover:border-text3 " +
    "disabled:opacity-50",
  ghost: "bg-transparent border-none text-text3 font-medium hover:text-text2 hover:underline",
};

export function Button({
  variant = "primary",
  loading = false,
  disabled,
  children,
  className = "",
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cn(
        "flex items-center justify-center gap-2 rounded-lg px-3.5 py-2.5 text-[13.5px] transition",
        "disabled:cursor-not-allowed",
        BUTTON_STYLES[variant],
        className,
      )}
    >
      {loading && <Spinner />}
      {children}
    </button>
  );
}

/** Exported so a rare unavoidable wait can borrow the button's spinner rather than invent a second one. */
export function Spinner() {
  return (
    <svg
      className="size-3.5 animate-spin"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" opacity="0.25" />
      <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

// ── Field ───────────────────────────────────────────────────────────────────

/**
 * The uppercase mono micro-label the mockups put above every input.
 *
 * Wraps rather than sits beside the control, so clicking the label focuses the input without anyone
 * having to remember to wire up an id.
 */
export function Field({
  label,
  error,
  hint,
  action,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  /** The "Forgot?" link the Login mockup places on the password label's own line. */
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label className="mb-4 block">
      <span className="mb-1.5 flex items-baseline justify-between">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
          {label}
        </span>
        {action}
      </span>

      {children}

      {/* The error replaces the hint rather than stacking under it — the mockups leave one line of
          space here, and two would shift the form.

          aria-live, because a validation error appears *after* the user has already left the field: a
          sighted user sees red text arrive, and without this a screen-reader user is told nothing at
          all and simply finds the form refusing to submit. */}
      <span aria-live="polite">
        {error ? (
          <span className="mt-1.5 block font-mono text-[11px] text-red">{error}</span>
        ) : hint ? (
          <span className="mt-1.5 block font-mono text-[11px] text-text3">{hint}</span>
        ) : null}
      </span>
    </label>
  );
}

// ── Input / Select ──────────────────────────────────────────────────────────

const CONTROL =
  "w-full rounded-lg border bg-panel2 px-3 py-2.5 font-mono text-[13px] text-text outline-none " +
  "transition focus:border-sky";

export function Input({ invalid, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      {...rest}
      aria-invalid={invalid}
      className={cn(CONTROL, invalid ? "border-red" : "border-line", className)}
    />
  );
}

export function Select({
  invalid,
  className,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <select
      {...rest}
      aria-invalid={invalid}
      className={cn(CONTROL, invalid ? "border-red" : "border-line", className)}
    >
      {children}
    </select>
  );
}

// ── Card / Logo ─────────────────────────────────────────────────────────────

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("animate-fade-up rounded-panel border border-line bg-panel p-8 shadow-panel", className)}>
      {children}
    </div>
  );
}

/** The amber "L" tile and wordmark that heads every unauthenticated screen. */
export function Logo() {
  return (
    <div className="flex animate-fade-up items-center gap-2.5">
      <span className="grid size-[30px] place-items-center rounded-lg bg-amber-btn font-mono text-sm font-bold text-on-amber">
        L
      </span>
      <span className="font-mono text-base font-semibold tracking-[0.02em]">LightMove</span>
    </div>
  );
}

/**
 * An error that belongs to the form as a whole rather than to one field — a wrong password, a locked
 * account, a rate limit.
 */
export function FormError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <div
      role="alert"
      className="mb-4 rounded-lg bg-red-dim px-3 py-2.5 font-mono text-[11.5px] text-red"
    >
      {message}
    </div>
  );
}

/** The blue informational note from the Signup mockup's invite step. */
export function Notice({ children }: { children: ReactNode }) {
  return (
    <div className="mb-5 flex items-center gap-2 rounded-lg bg-sky-dim px-3 py-2.5 font-mono text-[11.5px] text-sky">
      <svg
        className="size-3.5 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8h.01M12 11v5" />
      </svg>
      {children}
    </div>
  );
}
