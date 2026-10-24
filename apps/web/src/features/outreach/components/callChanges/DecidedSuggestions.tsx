import { cn } from "../../../../lib/cn";
import type { CallSuggestion } from "../../api/notetakerApi";
import { SUGGESTION_FIELD, valueTextOf } from "../../lib/callSuggestions";

/** Changes already applied or skipped, read only: once reviewed, a correction is made on the record itself. */
export function DecidedSuggestions({ suggestions }: { suggestions: CallSuggestion[] }) {
  return (
    <ul>
      {suggestions.map((suggestion) => {
        const applied = suggestion.status === "APPLIED";
        return (
          <li
            key={suggestion.id}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-4 border-t border-u-border py-2.5"
          >
            <span className="min-w-0">
              <span className="block text-meta text-u-text3">{SUGGESTION_FIELD[suggestion.kind].label}</span>
              <span className={cn("block break-words text-note", applied ? "text-u-text" : "text-u-text3")}>
                {valueTextOf(suggestion.kind, suggestion.value)}
              </span>
            </span>
            <span className={cn("text-meta", applied ? "font-medium text-u-direct" : "text-u-text3")}>
              {applied ? (suggestion.edited ? "Applied, as edited" : "Applied") : "Skipped"}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
