import type { Criterion } from "../../api/types";
import type { IdentifiedCompetency } from "../../lib/competencyRows";
import { fieldCountOf, type StepReceipt } from "../../lib/documentFill";
import { FieldBlock } from "../BriefFields";
import { CompetencySplit } from "../CompetencySplit";
import { CompetencyTable } from "../CompetencyTable";
import { CriteriaList } from "../CriteriaList";
import { DocumentFillStrip } from "../DocumentFillStrip";

export type CompetencyPanelKey = "technical" | "behavioural";

/** Step four: the gates a candidate passes, how the assessment divides, and what it scores. */
export function AssessmentStep({
  criteria,
  technical,
  behavioural,
  technicalShare,
  locked,
  receipt,
  stripError,
  extracting,
  onCriteria,
  onPanel,
  onShare,
  onToggleLock,
  onReorder,
  onExtractDocument,
  onUndoAll,
  onDismissStrip,
  onUndoCriterion,
  onUndoCompetency,
}: {
  criteria: Criterion[];
  technical: IdentifiedCompetency[];
  behavioural: IdentifiedCompetency[];
  technicalShare: number;
  locked: ReadonlySet<string>;
  /** This session's Assessment-screen receipt — the criteria list and both competency panels. */
  receipt?: StepReceipt;
  stripError?: string;
  extracting: boolean;
  onCriteria: (criteria: Criterion[]) => void;
  onPanel: (panel: CompetencyPanelKey) => (rows: IdentifiedCompetency[]) => void;
  onShare: (technicalShare: number) => void;
  onToggleLock: (id: string) => void;
  onReorder: (panel: CompetencyPanelKey) => (fromId: string, toId: string) => void;
  onExtractDocument: () => void;
  onUndoAll: () => void;
  onDismissStrip: () => void;
  onUndoCriterion: (text: string) => void;
  onUndoCompetency: (panel: CompetencyPanelKey, name: string) => void;
}) {
  return (
    <div className="flex flex-col gap-8">
      <DocumentFillStrip
        fileName={receipt?.fileName ?? ""}
        count={fieldCountOf(receipt)}
        error={stripError}
        onRetry={onExtractDocument}
        retrying={extracting}
        onUndoAll={onUndoAll}
        onDismiss={onDismissStrip}
      />

      <FieldBlock
        label={
          <>
            Screening criteria
            <span className="ms-2 normal-case tracking-normal text-u-text3">
              · Required narrows the field · Preferred breaks ties
            </span>
          </>
        }
      >
        <CriteriaList criteria={criteria} receipt={receipt} onChange={onCriteria} onUndo={onUndoCriterion} />
      </FieldBlock>

      <FieldBlock label="Competency split">
        <CompetencySplit technicalShare={technicalShare} onChange={onShare} />
      </FieldBlock>

      <CompetencyTable
        title="Technical competencies"
        tone="technical"
        rows={technical}
        locked={locked}
        receipt={receipt?.lists.technical}
        onChange={onPanel("technical")}
        onToggleLock={onToggleLock}
        onReorder={onReorder("technical")}
        onUndo={(name) => onUndoCompetency("technical", name)}
      />

      <CompetencyTable
        title="Behavioural competencies"
        tone="behavioural"
        rows={behavioural}
        locked={locked}
        receipt={receipt?.lists.behavioural}
        onChange={onPanel("behavioural")}
        onToggleLock={onToggleLock}
        onReorder={onReorder("behavioural")}
        onUndo={(name) => onUndoCompetency("behavioural", name)}
      />
    </div>
  );
}
