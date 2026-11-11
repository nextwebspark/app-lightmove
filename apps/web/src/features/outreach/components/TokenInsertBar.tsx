import { cn } from "../../../lib/cn";
import type { SequenceTokenOption } from "../lib/sequenceTokens";

/**
 * The Insert bar a sequence step and an email template share: each variable a chip, in the same order, inserted as
 * typed (`{{firstName}}`), so a consultant reads and writes them the same way in both.
 */
export function TokenInsertBar({
  options,
  onInsert,
  className,
}: {
  options: readonly SequenceTokenOption[];
  /** `isBodyOnly`: a token a subject never takes (the signature), so it goes into the body whatever had the caret. */
  onInsert: (token: string, isBodyOnly?: boolean) => void;
  className?: string;
}) {
  return (
    <div
      role="toolbar"
      aria-label="Insert a variable"
      className={cn(
        "flex flex-wrap items-center gap-1.5 rounded-u-chip border border-u-border bg-u-raised px-2.5 py-2",
        className,
      )}
    >
      <span className="me-1 font-mono text-meta font-medium text-u-text3">Insert</span>
      {options.map((option) => (
        <button
          key={option.token}
          type="button"
          title={option.tip}
          // Pressing a chip must not take the caret out of the field it inserts into.
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => onInsert(option.token, option.isBodyOnly)}
          className={cn(
            "rounded-full border px-2 py-[3px] font-mono text-meta",
            option.isAi
              ? "border-transparent bg-u-inferred-tint text-u-inferred"
              : "border-u-border bg-u-surface text-u-text2 hover:text-u-text",
          )}
        >
          {option.isAi ? "✦ opener" : option.isLink ? "▦ booking link" : option.token}
        </button>
      ))}
    </div>
  );
}
