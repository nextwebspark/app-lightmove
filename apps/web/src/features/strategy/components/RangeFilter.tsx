import type { FacetCount, NumericRange } from "../api/types";
import { cn } from "../../../lib/cn";
import { FacetsUnavailable } from "./FacetsUnavailable";
import { FilterCheckRow } from "../../../components/ui/FilterCheckRow";

/**
 * The Employees and Revenue panels, which share one shape: a mode pair at the top, then either the
 * predefined rows or a Min/Max pair.
 *
 * <p><b>The mode is derived, never stored.</b> A non-null `range` is Custom Range; null is
 * Predefined. There is no third piece of state that could say "custom" while the range is empty, and
 * switching modes clears whichever side is being left — so the panel and the query can never
 * disagree about what is in force, on screen or in the saved document.
 */
export function RangeFilter({
  options,
  unavailable = false,
  selectedBands,
  range,
  onToggleBand,
  onRangeChange,
  minPlaceholder = "Min",
  maxPlaceholder = "Max",
  footnote,
}: {
  options: FacetCount[] | undefined;
  /** The counts were refused, not merely unread — a skeleton here would pulse forever. */
  unavailable?: boolean;
  selectedBands: string[];
  range: NumericRange | null;
  onToggleBand: (value: string) => void;
  onRangeChange: (range: NumericRange | null) => void;
  minPlaceholder?: string;
  maxPlaceholder?: string;
  footnote?: React.ReactNode;
}) {
  const isCustom = range !== null;

  return (
    <div className="flex flex-col gap-[14px]">
      <div className="flex flex-col gap-[6px]">
        <ModeOption
          label="Predefined Range"
          selected={!isCustom}
          onSelect={() => onRangeChange(null)}
        />
        <ModeOption
          label="Custom Range"
          selected={isCustom}
          // Entering custom mode with both ends empty is a mode, not a constraint: the server
          // normalises an empty range away, so the table keeps showing the full scope until a
          // figure is typed.
          onSelect={() => onRangeChange({ min: null, max: null })}
        />
      </div>

      {isCustom ? (
        <div className="flex items-center gap-2">
          <BoundInput
            placeholder={minPlaceholder}
            value={range.min}
            onChange={(min) => onRangeChange({ ...range, min })}
          />
          <span className="font-sans text-[12px] font-medium text-text3">to</span>
          <BoundInput
            placeholder={maxPlaceholder}
            value={range.max}
            onChange={(max) => onRangeChange({ ...range, max })}
          />
        </div>
      ) : (
        <div className="flex flex-col gap-[2px]">
          {unavailable && <FacetsUnavailable />}
          {!unavailable && options
            ? options.map((option) => (
                <FilterCheckRow
                  key={option.value}
                  label={option.label}
                  count={option.count}
                  checked={selectedBands.includes(option.value)}
                  onToggle={() => onToggleBand(option.value)}
                />
              ))
            : !unavailable && <RowSkeleton />}
        </div>
      )}

      {footnote}
    </div>
  );
}

function ModeOption({
  label,
  selected,
  onSelect,
}: {
  label: string;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      className={cn(
        "flex items-center gap-[10px] rounded-md border px-3 py-[10px] text-left transition",
        selected ? "border-amber bg-amber-dim" : "border-line bg-transparent hover:border-text3",
      )}
    >
      <span
        className={cn(
          "grid h-4 w-4 flex-none place-items-center rounded-full shadow-[inset_0_0_0_1.5px_currentColor]",
          selected ? "text-amber" : "text-line",
        )}
      >
        <span className={cn("h-2 w-2 rounded-full bg-amber", selected ? "opacity-100" : "opacity-0")} />
      </span>
      <span
        className={cn(
          "font-sans text-[13px] font-semibold",
          selected ? "text-amber" : "text-text",
        )}
      >
        {label}
      </span>
    </button>
  );
}

const MAX_BOUND_DIGITS = 15;

/**
 * A bound that is empty rather than zero when cleared. Parsing "" to 0 would turn "delete what I
 * typed" into "at least nothing", which reads as a constraint and is not one.
 */
function BoundInput({
  placeholder,
  value,
  onChange,
}: {
  placeholder: string;
  value: number | null;
  onChange: (value: number | null) => void;
}) {
  return (
    <input
      inputMode="numeric"
      aria-label={placeholder}
      placeholder={placeholder}
      value={value ?? ""}
      onChange={(event) => {
        // Capped at the digits a headcount or a revenue figure can plausibly need. Past
        // Number.MAX_SAFE_INTEGER the value the server receives is not the one that was typed, and
        // past Long.MAX_VALUE it answers with a JSON parse error rather than a validation message.
        const raw = event.target.value.replace(/[^\d]/g, "").slice(0, MAX_BOUND_DIGITS);
        onChange(raw === "" ? null : Number(raw));
      }}
      className="w-0 flex-1 rounded-md border border-line bg-panel2 px-[10px] py-2 font-sans text-[13px] font-medium text-text outline-none focus:border-amber"
    />
  );
}

function RowSkeleton() {
  return (
    <div className="flex flex-col gap-[6px]">
      {[0, 1, 2, 3, 4].map((row) => (
        <div key={row} className="h-[34px] animate-pulse rounded-[5px] bg-panel2" />
      ))}
    </div>
  );
}
