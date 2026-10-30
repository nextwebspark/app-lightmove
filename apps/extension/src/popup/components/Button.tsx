import type { ButtonHTMLAttributes } from "react";
import { cn } from "../lib/cn";

/** The panel's buttons: the accent action, or a quiet outlined one beside it. */
export function Button({
  variant = "primary",
  size = "sm",
  loading = false,
  className,
  disabled,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary";
  size?: "xs" | "sm" | "md";
  loading?: boolean;
}) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-u-control font-semibold transition disabled:cursor-default disabled:opacity-45",
        size === "xs" && "h-7 px-2.5 text-note",
        size === "sm" && "h-8 px-3 text-note",
        size === "md" && "h-[38px] px-4 text-body",
        variant === "primary"
          ? "bg-u-accent-solid text-white hover:enabled:bg-u-accent-solid-hover"
          : "border border-u-border-strong text-u-text2 hover:enabled:border-u-text3 hover:enabled:text-u-text",
        className,
      )}
    >
      {children}
    </button>
  );
}
