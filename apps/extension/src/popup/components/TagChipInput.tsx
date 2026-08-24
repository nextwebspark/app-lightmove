import { useState } from "react";

interface TagChipInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
}

/**
 * The consultant's own labels on this company, for this mandate.
 *
 * Added on Enter, removed by the chip's own button. Comparison is case-insensitive so "Dairy" and
 * "dairy" cannot both sit in the same row — the server applies the same rule, and disagreeing with it
 * would mean the popup showing two chips that come back as one.
 */
export function TagChipInput({ tags, onChange }: TagChipInputProps) {
  const [draft, setDraft] = useState("");

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && draft === "" && tags.length > 0) {
      onChange(tags.slice(0, -1));
      return;
    }
    if (event.key !== "Enter") {
      return;
    }
    event.preventDefault();
    const tag = draft.trim();
    if (!tag) {
      return;
    }
    const alreadyPresent = tags.some((existing) => existing.toLowerCase() === tag.toLowerCase());
    onChange(alreadyPresent ? tags : [...tags, tag]);
    setDraft("");
  };

  return (
    <div>
      {tags.length > 0 && (
        <ul className="mb-2 flex flex-wrap gap-1.5">
          {tags.map((tag) => (
            <li
              key={tag.toLowerCase()}
              className="inline-flex items-center gap-1.5 rounded-md border border-line-soft bg-panel2 px-2.5 py-1 text-[11px] font-medium text-text2"
            >
              {tag}
              <button
                type="button"
                aria-label={`Remove ${tag}`}
                onClick={() => onChange(tags.filter((existing) => existing !== tag))}
                className="text-text3 hover:text-red"
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        value={draft}
        aria-label="Add a tag"
        placeholder="Add a tag, press enter"
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
        className="w-full rounded-[7px] border border-dashed border-line bg-panel2 px-2.5 py-[7px] font-mono text-[12px] text-text outline-none focus:border-solid focus:border-sky"
      />
    </div>
  );
}
