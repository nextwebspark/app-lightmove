import type { InputHTMLAttributes } from "react";
import { cn } from "../../../lib/cn";
import { Icon, ICONS } from "../../../components/layout/Icon";

/**
 * The input that shows a green check once it holds something — withheld when `invalid`, where a tick
 * reading "this is fine" would contradict the error message under the field. The New-project modal's
 * role-title box; the brief itself draws its title on an UNCAVA hairline instead.
 */
export function CheckedInput({
  invalid,
  value,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  const filled = typeof value === "string" && value.trim().length > 0;
  return (
    <span className="relative block">
      <input
        {...rest}
        value={value}
        aria-invalid={invalid}
        className={cn(
          "w-full rounded-lg border bg-panel2 py-2.5 pl-3 pr-9 text-body font-medium",
          invalid ? "border-red" : "border-line",
          "text-text outline-none transition focus:border-sky",
        )}
      />
      <Icon
        d={ICONS.checkCircle}
        size={16}
        className={cn(
          "pointer-events-none absolute end-3 top-3 transition-colors",
          filled && !invalid ? "text-green" : "text-line",
        )}
      />
    </span>
  );
}
