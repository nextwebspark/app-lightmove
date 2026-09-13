import { Button, Spinner } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import type { PositionDocument, PositionExtraction, ProposedField } from "../api/types";
import { PositionExtractionPanel } from "./PositionExtractionPanel";

/**
 * The "Read from document" affordance and its proposal review panel — byte-identical across every step
 * that offers one (`MandateContextStep`, `CompensationStep`, `AssessmentStep`), pulled out once a third
 * copy made restyling it, or changing its copy, a multi-file edit with an easy miss. Step one's own
 * document-upload dropzone is a different shape (it also attaches and downloads the document itself),
 * so it is not one of these.
 */
export function StepExtraction({
  positionDocument,
  extraction,
  extracting,
  error,
  onExtract,
  onAcceptProposal,
  onDismissProposal,
  onAcceptAllProposals,
}: {
  positionDocument: PositionDocument | null;
  extraction: PositionExtraction | null;
  extracting: boolean;
  /** Set only when this section's own slot in the last "read the whole document" fan-out failed —
   * the button below is also the retry, so this is only the message, never a second control. */
  error?: unknown;
  onExtract: () => void;
  onAcceptProposal: (field: ProposedField, value: string) => void;
  onDismissProposal: (field: ProposedField) => void;
  onAcceptAllProposals: (edits: Record<number, string>) => void;
}) {
  return (
    <div className="flex flex-col gap-2.5">
      {positionDocument ? (
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-3">
            <Button
              type="button"
              variant="secondary"
              onClick={onExtract}
              disabled={extracting}
              className="border-sky/60 px-2.5 py-[5px] text-[11.5px] text-sky hover:border-sky"
            >
              Read from document
            </Button>
            {extracting && (
              <span className="flex items-center gap-[7px] font-mono text-[11.5px] text-text3">
                <Spinner />
                Reading document…
              </span>
            )}
          </div>
          {!extracting && error !== undefined && (
            <span className="font-mono text-[11.5px] text-red">{messageFor(error)}</span>
          )}
        </div>
      ) : (
        <span className="font-mono text-[11.5px] text-text3">
          Attach a position description on the Details step to read this step from it.
        </span>
      )}
      {extraction && (
        <PositionExtractionPanel
          extraction={extraction}
          onAccept={onAcceptProposal}
          onDismiss={onDismissProposal}
          onAcceptAll={onAcceptAllProposals}
        />
      )}
    </div>
  );
}
