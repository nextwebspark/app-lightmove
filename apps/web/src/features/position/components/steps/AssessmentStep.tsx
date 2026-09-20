import type { Criterion } from "../../api/types";
import type { IdentifiedCompetency } from "../../lib/competencyRows";
import { FieldBlock } from "../BriefFields";
import { CompetencySplit } from "../CompetencySplit";
import { CompetencyTable } from "../CompetencyTable";
import { CriteriaList } from "../CriteriaList";

export type CompetencyPanelKey = "technical" | "behavioural";

/** Step four: the gates a candidate passes, how the assessment divides, and what it scores. */
export function AssessmentStep({
  criteria,
  technical,
  behavioural,
  technicalShare,
  locked,
  onCriteria,
  onPanel,
  onShare,
  onToggleLock,
  onReorder,
}: {
  criteria: Criterion[];
  technical: IdentifiedCompetency[];
  behavioural: IdentifiedCompetency[];
  technicalShare: number;
  locked: ReadonlySet<string>;
  onCriteria: (criteria: Criterion[]) => void;
  onPanel: (panel: CompetencyPanelKey) => (rows: IdentifiedCompetency[]) => void;
  onShare: (technicalShare: number) => void;
  onToggleLock: (id: string) => void;
  onReorder: (panel: CompetencyPanelKey) => (fromId: string, toId: string) => void;
}) {
  return (
    <div className="flex flex-col gap-8">
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
        <CriteriaList criteria={criteria} onChange={onCriteria} />
      </FieldBlock>

      <FieldBlock label="Competency split">
        <CompetencySplit technicalShare={technicalShare} onChange={onShare} />
      </FieldBlock>

      <CompetencyTable
        title="Technical competencies"
        tone="technical"
        rows={technical}
        locked={locked}
        onChange={onPanel("technical")}
        onToggleLock={onToggleLock}
        onReorder={onReorder("technical")}
      />

      <CompetencyTable
        title="Behavioural competencies"
        tone="behavioural"
        rows={behavioural}
        locked={locked}
        onChange={onPanel("behavioural")}
        onToggleLock={onToggleLock}
        onReorder={onReorder("behavioural")}
      />
    </div>
  );
}
