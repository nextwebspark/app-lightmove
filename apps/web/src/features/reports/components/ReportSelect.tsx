import type { SelectHTMLAttributes } from "react";
import { cn } from "../../../lib/cn";

// A data URI cannot read a CSS variable, so the chevron carries the one grey that reads on both grounds.
const CHEVRON =
  "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%23A6ADBB' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>\")";

/** The filter a report card scopes itself with. */
export function ReportSelect({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...rest}
      style={{ backgroundImage: CHEVRON, backgroundPosition: "right 10px center" }}
      className={cn(
        "appearance-none rounded-[8px] border border-u-border-strong bg-u-surface bg-no-repeat py-[7px] pl-3 pr-7 font-sans text-[11.5px] font-semibold text-u-text outline-none transition hover:border-u-accent focus-visible:border-u-accent",
        className,
      )}
    >
      {children}
    </select>
  );
}
