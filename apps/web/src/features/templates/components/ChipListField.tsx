import { useState } from "react";
import { Input } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { AddRowButton, RemoveRowButton } from "./fields";

const LABEL = "mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3";

/** Short texts as removable chips, with an input that adds one — keywords, responsibilities, seats. */
export function ChipListField({
  label,
  values,
  placeholder,
  hint,
  max,
  maxLength,
  lowercase = false,
  onChange,
}: {
  label: string;
  values: string[];
  placeholder: string;
  hint?: string;
  max: number;
  maxLength: number;
  /** Keywords are matched lower-cased, so they are stored that way. */
  lowercase?: boolean;
  onChange: (values: string[]) => void;
}) {
  const [draft, setDraft] = useState("");
  const [duplicate, setDuplicate] = useState<string | null>(null);

  const add = () => {
    const text = lowercase ? draft.trim().toLowerCase() : draft.trim();
    if (!text || values.length >= max) return;
    // Already in the list is what the user asked for, so the box clears — but saying so, or + Add
    // reads as broken.
    if (values.includes(text)) {
      setDuplicate(text);
      setDraft("");
      return;
    }
    onChange([...values, text]);
    setDraft("");
  };

  return (
    <div className="mb-4">
      <span className={LABEL}>{label}</span>
      <div className="rounded-[10px] border border-line-soft bg-panel px-3.5 py-3">
        {values.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {values.map((value, index) => (
              <span
                key={`${value}-${index}`}
                className="inline-flex items-center gap-[7px] rounded border border-line bg-panel2 py-1.5 pe-2 ps-2.5 text-xs font-semibold text-text2"
              >
                {value}
                <RemoveRowButton
                  label={`Remove ${value}`}
                  onClick={() => onChange(values.filter((_, i) => i !== index))}
                />
              </span>
            ))}
          </div>
        )}
        {values.length < max && (
          <div className={cn("flex gap-2", values.length > 0 && "mt-3")}>
            <Input
              value={draft}
              aria-label={`Add to ${label.toLowerCase()}`}
              placeholder={placeholder}
              maxLength={maxLength}
              onChange={(event) => {
                setDraft(event.target.value);
                setDuplicate(null);
              }}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  add();
                }
              }}
            />
            <AddRowButton onClick={add} className="flex-none">
              + Add
            </AddRowButton>
          </div>
        )}
        {duplicate && (
          <p role="status" className="mt-2.5 font-mono text-[11px] text-amber">
            &ldquo;{duplicate}&rdquo; is already in the list.
          </p>
        )}
        {hint && <p className="mt-2.5 font-mono text-[11px] text-text3">{hint}</p>}
      </div>
    </div>
  );
}
