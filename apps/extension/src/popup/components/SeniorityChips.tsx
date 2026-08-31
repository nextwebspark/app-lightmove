import { CANDIDATE_SENIORITIES, type CandidateSeniority } from "../../api/types";
import { cn } from "../lib/cn";

interface SeniorityChipsProps {
  selected: CandidateSeniority | null;
  onSelect: (seniority: CandidateSeniority | null) => void;
}

/**
 * Where the person sits relative to the chief executive.
 *
 * Single-select and clearable: nothing selected means the researcher has not established it, which
 * the mandate reads differently from a guess. All five rungs the API accepts are offered — the design
 * shows four, and omitting `N-3` would make it unrecordable from here.
 */
export function SeniorityChips({ selected, onSelect }: SeniorityChipsProps) {
  return (
    <div className="flex flex-wrap gap-1.5" role="group" aria-label="Seniority">
      {CANDIDATE_SENIORITIES.map((seniority) => {
        const isSelected = seniority === selected;
        return (
          <button
            key={seniority}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onSelect(isSelected ? null : seniority)}
            className={cn(
              "rounded-md border px-2.5 py-1 text-[11.5px] font-medium",
              isSelected
                ? "border-amber bg-amber-dim text-amber"
                : "border-line bg-panel2 text-text3 hover:text-text2",
            )}
          >
            {seniority}
          </button>
        );
      })}
    </div>
  );
}
