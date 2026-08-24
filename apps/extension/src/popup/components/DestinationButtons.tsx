import { DESTINATION_LABELS, TRIAGE_DESTINATIONS, type TriageDestination } from "../../domain/triageDestination";

interface DestinationButtonsProps {
  onCapture: (destination: TriageDestination) => void;
  isSaving: boolean;
  savingDestination: TriageDestination | null;
  isDisabled: boolean;
}

/**
 * "Add to universe" and "Add to shortlist" — the two stages a capture may land in.
 *
 * Two buttons rather than a destination dropdown plus one Save, because the choice *is* the action:
 * a consultant looking at a company already knows which of the two it is, and the design makes them
 * say it in one click. It is also why the company form carries no relevance chips — the button is the
 * judgement.
 */
export function DestinationButtons({
  onCapture,
  isSaving,
  savingDestination,
  isDisabled,
}: DestinationButtonsProps) {
  const [primary, secondary] = TRIAGE_DESTINATIONS;
  return (
    <div className="flex gap-2">
      <button
        type="button"
        disabled={isDisabled || isSaving}
        onClick={() => onCapture(primary)}
        className="flex flex-1 items-center justify-center gap-[7px] rounded-lg border border-amber-btn bg-amber-btn px-2.5 py-[9px] text-[12.5px] font-semibold text-on-amber disabled:opacity-60"
      >
        <span aria-hidden>+</span>
        {savingDestination === primary ? "Adding…" : DESTINATION_LABELS[primary]}
      </button>
      <button
        type="button"
        disabled={isDisabled || isSaving}
        onClick={() => onCapture(secondary)}
        className="flex flex-1 items-center justify-center gap-[7px] rounded-lg border border-line bg-panel px-2.5 py-[9px] text-[12.5px] font-semibold text-text2 hover:border-text3 hover:text-text disabled:opacity-60"
      >
        <span aria-hidden>✓</span>
        {savingDestination === secondary ? "Adding…" : DESTINATION_LABELS[secondary]}
      </button>
    </div>
  );
}
