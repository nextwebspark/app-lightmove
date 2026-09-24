import { useState, type KeyboardEvent } from "react";

/**
 * A short list typed as chips: Enter or a comma adds what is typed, × removes a chip, and Backspace in
 * an empty box takes the last one back. A repeat of an existing chip (any case) is not added twice.
 */
export function TagListInput({
  values,
  onChange,
  placeholder,
  ariaLabel,
  maxItems,
  disabled,
}: {
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  ariaLabel: string;
  maxItems?: number;
  disabled?: boolean;
}) {
  const [draft, setDraft] = useState("");
  const isFull = maxItems !== undefined && values.length >= maxItems;

  const commitDraft = () => {
    const value = draft.trim();
    setDraft("");
    if (!value || isFull) return;
    if (values.some((existing) => existing.toLowerCase() === value.toLowerCase())) return;
    onChange([...values, value]);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      commitDraft();
      return;
    }
    if (event.key === "Backspace" && draft === "" && values.length > 0) {
      onChange(values.slice(0, -1));
    }
  };

  return (
    <div className="flex min-h-[42px] w-full flex-wrap items-center gap-1.5 rounded-lg border border-u-border-strong bg-u-surface px-2 py-1.5 transition focus-within:border-u-accent">
      {values.map((value) => (
        <span
          key={value.toLowerCase()}
          className="inline-flex items-center gap-1 rounded-md bg-u-raised px-2 py-1 font-mono text-[12px] text-u-text ring-1 ring-u-border"
        >
          {value}
          {!disabled && (
            <button
              type="button"
              aria-label={`Remove ${value}`}
              onClick={() => onChange(values.filter((existing) => existing !== value))}
              className="text-u-text3 hover:text-u-offlimits"
            >
              ×
            </button>
          )}
        </span>
      ))}
      {!disabled && !isFull && (
        <input
          value={draft}
          aria-label={ariaLabel}
          placeholder={values.length === 0 ? placeholder : undefined}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={commitDraft}
          className="min-w-[120px] flex-1 bg-transparent px-1 py-1 font-mono text-[13px] text-u-text outline-none"
        />
      )}
    </div>
  );
}
