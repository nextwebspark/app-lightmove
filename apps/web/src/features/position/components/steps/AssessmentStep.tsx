import { Spinner } from "../../../../components/ui";
import type { Criterion, PositionDocument, PositionExtraction, ProposedField } from "../../api/types";
import type { IdentifiedCompetency } from "../../lib/competencyRows";
import { CompetencyPanel } from "../CompetencyPanel";
import { CriteriaCard } from "../CriteriaCard";
import { PositionExtractionPanel } from "../PositionExtractionPanel";
import { SectionHeading } from "../fields";

export type CompetencyPanelKey = "technical" | "behavioural";

/** Step five: what a candidate is scored against, how much each part counts, and in what order. */
export function AssessmentStep({
  criteria,
  technical,
  behavioural,
  locked,
  document,
  extraction,
  extracting,
  onCriteria,
  onPanel,
  onToggleLock,
  onReorder,
  onExtract,
  onAcceptProposal,
  onDismissProposal,
  onAcceptAllProposals,
}: {
  criteria: Criterion[];
  technical: IdentifiedCompetency[];
  behavioural: IdentifiedCompetency[];
  locked: ReadonlySet<string>;
  document: PositionDocument | null;
  extraction: PositionExtraction | null;
  extracting: boolean;
  onCriteria: (criteria: Criterion[]) => void;
  onPanel: (panel: CompetencyPanelKey) => (rows: IdentifiedCompetency[]) => void;
  onToggleLock: (id: string) => void;
  onReorder: (panel: CompetencyPanelKey) => (fromId: string, toId: string) => void;
  onExtract: () => void;
  onAcceptProposal: (field: ProposedField, value: string) => void;
  onDismissProposal: (field: ProposedField) => void;
  onAcceptAllProposals: () => void;
}) {
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2.5">
        {document ? (
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onExtract}
              disabled={extracting}
              className="rounded-[7px] border border-sky/60 px-2.5 py-[5px] text-[11.5px] font-medium text-sky transition hover:border-sky disabled:opacity-50"
            >
              Read from document
            </button>
            {extracting && (
              <span className="flex items-center gap-[7px] font-mono text-[11.5px] text-text3">
                <Spinner />
                Reading document…
              </span>
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

      <div>
        <SectionHeading
          title="Competency weighting"
          aside="drag to rank · lock a weight to hold it"
        />
        <div className="flex flex-col gap-5">
          <CompetencyPanel
            title="Technical Competencies"
            accent="sky"
            rows={technical}
            locked={locked}
            onChange={onPanel("technical")}
            onToggleLock={onToggleLock}
            onReorder={onReorder("technical")}
          />
          <CompetencyPanel
            title="Behavioural Competencies"
            accent="amber"
            rows={behavioural}
            locked={locked}
            onChange={onPanel("behavioural")}
            onToggleLock={onToggleLock}
            onReorder={onReorder("behavioural")}
          />
        </div>
      </div>

      <CriteriaCard criteria={criteria} onChange={onCriteria} />
    </div>
  );
}
