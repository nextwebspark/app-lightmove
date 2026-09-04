import { useId } from "react";
import { cn } from "../lib/cn";

interface DetectedFieldInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** Where a field costs something to act on — the employer lookup runs here, not per keystroke. */
  onSettled?: (value: string) => void;
  inputMode?: "text" | "numeric" | "url";
  /** The page is still being read. The input stays editable — typing in it is what claims the field. */
  isReading?: boolean;
  /** The page supplied this value, so it is shown rather than offered for editing. */
  isLocked?: boolean;
}

/**
 * One extracted field, as an editable input.
 *
 * <b>Locked when the page supplied the value</b>: what LinkedIn says a person or a company is called
 * is the record, and a consultant retyping it by hand is how two mandates end up holding the same
 * executive under two spellings. `readOnly` rather than `disabled` so the value stays focusable and
 * copyable, and so the label still names it for a screen reader.
 *
 * It falls back to a real input when the read came back empty, and that is not a nicety: a name is
 * what `canSave` gates on, so a page the extractor missed — or one the reader gave up on past its
 * deadline — would otherwise be a row nobody can file.
 */
export function DetectedFieldInput({
  label,
  value,
  onChange,
  onSettled,
  inputMode = "text",
  isReading = false,
  isLocked = false,
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
        onChange={(event) => onChange(event.target.value)}
        onBlur={(event) => onSettled?.(event.target.value)}
        aria-busy={isReading || undefined}
        readOnly={isLocked}
        className={cn(
          "mt-1 w-full rounded-[7px] border border-line bg-panel2 px-2.5 py-[7px] font-mono text-[12.5px] text-text outline-none",
          isLocked ? "cursor-default border-line-soft bg-transparent" : "focus:border-sky",
          isReading && "animate-pulse opacity-60",
        )}
      />
    </div>
  );
}
