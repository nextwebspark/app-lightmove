import type { SelectHTMLAttributes } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/** The filter a report card scopes itself with. */
export function ReportSelect({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    // The chevron is drawn over the select rather than set as its background image: a data URI cannot
    // read a token, so it carried one fixed grey in both themes.
    <span className="relative inline-flex">
      <select
        {...rest}
        className={cn(
          "appearance-none rounded-[8px] border border-u-border-strong bg-u-surface py-[7px] pl-3 pr-7 font-sans text-[11.5px] font-semibold text-u-text outline-none transition hover:border-u-accent focus-visible:border-u-accent",
          className,
        )}
      >
        {children}
      </select>
      <Icon
        d={ICONS.chevronDown}
        size={10}
        className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-u-text2 [stroke-width:2.5]"
      />
    </span>
  );
}
