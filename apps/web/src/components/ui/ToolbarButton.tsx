import type { ButtonHTMLAttributes } from "react";
import { cn } from "../../lib/cn";
import { Spinner } from "./index";

interface ToolbarButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean;
}

/**
 * The bordered button of a list toolbar — Export, Filters — sized to sit level with the search box and
 * the chips beside it, where the form `Button` stands a size taller.
 */
export function ToolbarButton({ loading = false, disabled, className, children, ...rest }: ToolbarButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[8px] border border-u-border-strong bg-u-surface px-3 py-[7px]",
        "font-sans text-[13px] font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
    >
      {loading && <Spinner />}
      {children}
    </button>
  );
}
