import type { Assessment, Competency, Criterion } from "../../api/types";
import { CompetencyPanel } from "../CompetencyPanel";
import { CriteriaCard } from "../CriteriaCard";
import { SectionHeading } from "../fields";

/** Step five: what a candidate is scored against, and how much each part counts. */
export function AssessmentStep({
  assessment,
  onCriteria,
  onPanel,
}: {
  assessment: Assessment;
  onCriteria: (criteria: Criterion[]) => void;
  onPanel: (panel: "technical" | "behavioural") => (rows: Competency[]) => void;
}) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <SectionHeading
          title="Competency weighting"
          aside="drag to rebalance · type a number to set exactly"
        />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <CompetencyPanel
            title="Technical Competencies"
            accent="sky"
            rows={assessment.technical}
            onChange={onPanel("technical")}
          />
          <CompetencyPanel
            title="Behavioural Competencies"
            accent="amber"
            rows={assessment.behavioural}
            onChange={onPanel("behavioural")}
          />
        </div>
      </div>

      <CriteriaCard criteria={assessment.criteria} onChange={onCriteria} />
    </div>
  );
}
