import { useRef } from "react";
import { cn } from "../../lib/cn";
import { formatDate } from "../../lib/format";
import { Icon, ICONS } from "../layout/Icon";

/**
 * A date field that displays the mockups' "15 Sep 2026" shape instead of the browser's dd/mm/yyyy.
 * The native input still does the picking — it sits invisibly over the styled face, so the calendar,
 * keyboard entry and form semantics all stay the browser's.
 */
export function DateInput({
  value,
  onChange,
  className,
}: {
  /** ISO yyyy-MM-dd as the API speaks it; "" when unset. */
  value: string;
  onChange: (isoDate: string) => void;
  className?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);

  const openPicker = () => {
    try {
      inputRef.current?.showPicker();
    } catch {
      // showPicker is missing (older Safari) or refused outside a user gesture; the input's own
      // focus behaviour is the fallback.
      inputRef.current?.focus();
    }
  };

  return (
    <div
      className={cn(
        "relative flex w-full items-center justify-between rounded-lg border border-line bg-panel2",
        "px-3 py-2.5 font-mono text-[13px] transition focus-within:border-sky",
        className,
      )}
    >
      <span className={value ? "text-text" : "text-text3"}>
        {value ? formatDate(value) : "Select date"}
      </span>
      <Icon d={ICONS.calendar} size={14} className="text-text3" />
      <input
        ref={inputRef}
        type="date"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onClick={openPicker}
        className="absolute inset-0 cursor-pointer opacity-0"
      />
    </div>
  );
}
