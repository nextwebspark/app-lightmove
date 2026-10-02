import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "../../lib/cn";

type ChipSize = "default" | "sm";

const SIZE_CLASS: Record<ChipSize, string> = {
  default: "px-[11px] py-[5px]",
  sm: "px-2.5 py-1",
};

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected: boolean;
  /** A count beside the label, in the muted colour whatever the chip's own. */
  count?: ReactNode;
  size?: ChipSize;
  /** Replaces the accent-tint selected look — the tag chips bring their own colour. */
  selectedClassName?: string;
}

/**
 * The mockups' one pill: a 12px toggle with a hairline border that fills with the accent tint when
 * chosen. Quick views, kind-of-activity chips and tag toggles are all this chip; the caller supplies
 * the role (`aria-pressed`, `role="radio"`, `role="checkbox"`), since which one it is depends on the
 * group it sits in.
 */
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
      {count !== undefined && <span className="text-[11px] text-u-text3">{count}</span>}
    </button>
  );
}
