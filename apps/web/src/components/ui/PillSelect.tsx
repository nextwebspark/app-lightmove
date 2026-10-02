import type { ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * A toolbar filter that reads as one control — "Who  Everyone ⌄" — the caption inside the box rather
 * than shouting beside it. Still a native select underneath, so the keyboard and the phone picker are
 * the platform's.
 */
export function PillSelect({
  label,
  value,
  onChange,
  children,
  className,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label
      className={cn(
        "relative inline-flex items-center gap-1.5 rounded-[8px] border border-u-border-strong bg-u-surface ps-3 text-[13px]",
        "transition focus-within:border-u-accent hover:border-u-text3",
        className,
      )}
    >
      <span className="text-u-text3">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="cursor-pointer appearance-none bg-transparent py-[7px] pe-8 font-medium text-u-text outline-none"
      >
        {children}
      </select>
      <Icon d={ICONS.chevronDown} size={14} className="pointer-events-none absolute end-2.5 text-u-text3" />
    </label>
  );
}
