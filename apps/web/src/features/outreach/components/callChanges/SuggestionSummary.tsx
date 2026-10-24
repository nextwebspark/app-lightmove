import { cn } from "../../../../lib/cn";
import type { CallSuggestion, CallSuggestions } from "../../api/notetakerApi";
import { SUGGESTION_FIELD, valueTextOf } from "../../lib/callSuggestions";

/** The review's last step: what was applied and skipped, each with Undo. */
export function SuggestionSummary({
  answer,
  pending,
  onUndo,
}: {
  answer: CallSuggestions;
  pending: boolean;
  onUndo: (suggestion: CallSuggestion) => void;
}) {
  const applied = answer.suggestions.filter((each) => each.status === "APPLIED").length;
  const skipped = answer.suggestions.filter((each) => each.status === "SKIPPED").length;
  return (
    <div className="pb-2">
      <p className="mb-3 text-subhead font-semibold">
        {applied} applied <span className="font-normal text-u-text3">· {skipped} skipped</span>
      </p>
      <ul>
        {answer.suggestions.map((suggestion) => (
          <li
            key={suggestion.id}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 border-t border-u-border py-2.5"
          >
            <span className="min-w-0">
              <span className="block text-meta text-u-text3">{SUGGESTION_FIELD[suggestion.kind].label}</span>
              <span
                className={cn(
                  "block truncate text-note",
                  suggestion.status === "APPLIED" ? "text-u-text" : "text-u-text3",
                )}
              >
                {valueTextOf(suggestion.kind, suggestion.value)}
              </span>
            </span>
            <span className="flex items-center gap-2.5 text-meta">
              {suggestion.status === "APPLIED" ? (
                <span className="font-medium text-u-direct">Applied</span>
              ) : suggestion.status === "SKIPPED" ? (
                <span className="text-u-text3">Skipped</span>
              ) : (
                <span className="text-u-text3">Waiting</span>
              )}
              {suggestion.status !== "PENDING" && (
                <button
                  type="button"
                  disabled={pending}
                  onClick={() => onUndo(suggestion)}
                  className="font-medium text-u-accent hover:underline disabled:opacity-60"
                >
                  Undo
                </button>
              )}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
