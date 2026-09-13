import type { Criterion, PositionDocument, PositionExtraction, ProposedField } from "../../api/types";
import type { IdentifiedCompetency } from "../../lib/competencyRows";
import { CompetencyPanel } from "../CompetencyPanel";
import { CriteriaCard } from "../CriteriaCard";
import { StepExtraction } from "../StepExtraction";
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
  extractionError,
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
  extractionError?: unknown;
  onCriteria: (criteria: Criterion[]) => void;
  onPanel: (panel: CompetencyPanelKey) => (rows: IdentifiedCompetency[]) => void;
  onToggleLock: (id: string) => void;
  onReorder: (panel: CompetencyPanelKey) => (fromId: string, toId: string) => void;
  onExtract: () => void;
  onAcceptProposal: (field: ProposedField, value: string) => void;
  onDismissProposal: (field: ProposedField) => void;
  onAcceptAllProposals: (edits: Record<number, string>) => void;
}) {
  return (
    <div className="flex flex-col gap-5">
      <StepExtraction
        positionDocument={document}
        extraction={extraction}
        extracting={extracting}
        error={extractionError}
        onExtract={onExtract}
        onAcceptProposal={onAcceptProposal}
        onDismissProposal={onDismissProposal}
        onAcceptAllProposals={onAcceptAllProposals}
      />

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
