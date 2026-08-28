import type { Criterion } from "../../api/types";
import type { IdentifiedCompetency } from "../../lib/competencyRows";
import { CompetencyPanel } from "../CompetencyPanel";
import { CriteriaCard } from "../CriteriaCard";
import { SectionHeading } from "../fields";

export type CompetencyPanelKey = "technical" | "behavioural";

/** Step five: what a candidate is scored against, how much each part counts, and in what order. */
export function AssessmentStep({
  criteria,
  technical,
  behavioural,
  locked,
  onCriteria,
  onPanel,
  onToggleLock,
  onReorder,
}: {
  criteria: Criterion[];
  technical: IdentifiedCompetency[];
  behavioural: IdentifiedCompetency[];
  locked: ReadonlySet<string>;
  onCriteria: (criteria: Criterion[]) => void;
  onPanel: (panel: CompetencyPanelKey) => (rows: IdentifiedCompetency[]) => void;
  onToggleLock: (id: string) => void;
  onReorder: (panel: CompetencyPanelKey) => (fromId: string, toId: string) => void;
}) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <SectionHeading
          title="Competency weighting"
          aside="drag to rank · lock a weight to hold it"
        />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
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
