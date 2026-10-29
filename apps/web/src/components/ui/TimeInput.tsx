import { useMemo } from "react";
import { formatClock } from "../../lib/datetime";
import { Select } from "./index";

/**
 * A time of day, picked from steps and labelled in the person's own clock ("14:30" or "2:30 PM"). A native time input
 * draws the computer's clock whatever Uncava is set to, so it is not used. The value stays `HH:mm`.
 */
export function TimeInput({
  value,
  onChange,
  stepMinutes = 15,
  ariaLabel,
  invalid,
  disabled,
  placeholder,
  className,
}: {
  /** `HH:mm`; "" when unset. */
  value: string;
  onChange: (time: string) => void;
  stepMinutes?: number;
  ariaLabel?: string;
  invalid?: boolean;
  disabled?: boolean;
  /** The empty option's words, offered only while nothing is chosen. */
  placeholder?: string;
  className?: string;
}) {
  const times = useMemo(() => {
    const steps = Array.from({ length: Math.ceil(1440 / stepMinutes) }, (_, index) => {
      const minutes = index * stepMinutes;
      return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
    });
    // A time off the steps (one typed elsewhere, or saved before) stays shown rather than reading as blank.
    return value && !steps.includes(value) ? [...steps, value].sort() : steps;
  }, [stepMinutes, value]);

  return (
    <Select
      aria-label={ariaLabel}
      value={value}
      invalid={invalid}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
      className={className}
    >
      {value === "" && <option value="">{placeholder ?? "—"}</option>}
      {times.map((time) => (
        <option key={time} value={time}>
          {formatClock(time)}
        </option>
      ))}
    </Select>
  );
}
