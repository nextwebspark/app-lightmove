import { useId } from "react";

interface DetectedFieldInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  inputMode?: "text" | "numeric" | "url";
}

/**
 * One extracted field, as an editable input.
 *
 * Every detected field is rendered this way and never as read-only text — an extractor reading a
 * corporate About page is pattern-matching on prose, and the consultant is the one who knows whether
 * it got the trading name or the legal one. Nothing is written blind.
 */
export function DetectedFieldInput({
  label,
  value,
  onChange,
  placeholder,
  inputMode = "text",
}: DetectedFieldInputProps) {
  const inputId = useId();
  return (
    <div>
      <label
        htmlFor={inputId}
        className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.11em] text-text3"
      >
        {label}
      </label>
      <input
        id={inputId}
        value={value}
        inputMode={inputMode}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 w-full rounded-[7px] border border-line bg-panel2 px-2.5 py-[7px] font-mono text-[12.5px] text-text outline-none focus:border-sky"
      />
    </div>
  );
}
