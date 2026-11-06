import { useRef } from "react";
import { cn } from "../../lib/cn";
import { Icon, ICONS } from "../layout/Icon";

/**
 * A time field drawn as `DateInput` draws a date, so the two sit side by side as one family. The native input still
 * does the picking and the typing — it sits invisibly over the styled face.
 */
export function TimeInput({
  value,
  onChange,
  ariaLabel,
  invalid,
  describedBy,
  className,
}: {
  /** HH:mm; "" when unset. */
  value: string;
  onChange: (time: string) => void;
  ariaLabel?: string;
  invalid?: boolean;
  describedBy?: string;
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
        "relative flex w-full items-center justify-between rounded-[6px] border bg-u-raised px-3 py-2.5 font-mono",
        "text-[13px] transition focus-within:border-u-accent",
        invalid ? "border-u-offlimits" : "border-u-border-strong",
        className,
      )}
    >
      <span className={value ? "text-u-text" : "text-u-text3"}>{value || "--:--"}</span>
      <Icon d={ICONS.clock} size={14} className="text-u-text3" />
      <input
        ref={inputRef}
        type="time"
        value={value}
        aria-label={ariaLabel}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)}
        onClick={openPicker}
        className="absolute inset-0 cursor-pointer opacity-0"
      />
    </div>
  );
}
