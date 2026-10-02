import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "../../lib/cn";

type ChipSize = "default" | "sm";

const SIZE_CLASS: Record<ChipSize, string> = {
  default: "px-[11px] py-[5px]",
  sm: "px-2.5 py-1",
};

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected: boolean;
  /** Muted beside the label; on a chip that brings its own selected colour, a dimmed shade of it. */
  count?: ReactNode;
  size?: ChipSize;
  /** Replaces the accent-tint selected look — the tag chips bring their own colour. */
  selectedClassName?: string;
}

/** The mockups' one pill. The caller supplies the role — pressed, radio or checkbox — since that depends on its group. */
export function Chip({ selected, count, size = "default", selectedClassName, className, children, ...rest }: ChipProps) {
  return (
    <button
      type="button"
      {...rest}
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border text-xs font-medium transition",
        SIZE_CLASS[size],
        selected
          ? (selectedClassName ?? "border-u-accent bg-u-accent-tint text-u-accent")
          : "border-u-border-strong text-u-text2 hover:text-u-text",
        className,
      )}
    >
      {children}
      {count !== undefined && (
        <span className={cn("text-[11px]", selected && selectedClassName ? "opacity-70" : "text-u-text3")}>{count}</span>
      )}
    </button>
  );
}
