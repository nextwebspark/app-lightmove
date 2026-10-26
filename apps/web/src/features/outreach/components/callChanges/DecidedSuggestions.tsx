import { cn } from "../../../../lib/cn";
import type { CallSuggestion } from "../../api/notetakerApi";
import { SUGGESTION_FIELD, valueTextOf } from "../../lib/callSuggestions";

/**
 * Changes already applied or skipped, read only: what each one replaced, the words it rests on, and who decided it.
 * Once reviewed, a correction is made on the record itself.
 */
export function DecidedSuggestions({ suggestions }: { suggestions: CallSuggestion[] }) {
  return (
    <ul>
      {suggestions.map((suggestion) => {
        const applied = suggestion.status === "APPLIED";
        const now = valueTextOf(suggestion.kind, suggestion.value);
        const was = applied ? valueTextOf(suggestion.kind, suggestion.was) : null;
        return (
          <li key={suggestion.id} className="border-t border-u-border py-2.5">
            <div className="flex flex-wrap items-baseline justify-between gap-x-4">
              <span className="type-micro-label font-mono text-u-text3">{SUGGESTION_FIELD[suggestion.kind].label}</span>
              <span className={cn("text-meta", applied ? "font-medium text-u-direct" : "text-u-text3")}>
                {decisionOf(suggestion)}
              </span>
            </div>
            <p className={cn("mt-1 break-words text-note", applied ? "text-u-text" : "text-u-text3")}>
              {was && was !== now && (
                <>
                  <del className="text-u-text3">{was}</del>
                  <span aria-hidden="true"> → </span>
                </>
              )}
              {now ?? "—"}
            </p>
            {suggestion.said && (
              <p className="mt-1 flex items-baseline gap-2 text-meta text-u-text3">
                {suggestion.at && <span className="flex-none font-mono text-u-accent">{suggestion.at}</span>}
                <q className="italic">{suggestion.said}</q>
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function decisionOf(suggestion: CallSuggestion): string {
  if (suggestion.status !== "APPLIED") return "Skipped";
  const by = suggestion.appliedBy ? ` by ${suggestion.appliedBy.trim().split(/\s+/)[0]}` : "";
  return `${suggestion.edited ? "Edited and applied" : "Applied"}${by}`;
}
