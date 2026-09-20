import { cn } from "../../../lib/cn";
import type { Position } from "../api/types";
import { POSITION_STEPS, completion, doneSteps } from "../lib/steps";
import { BriefPanel } from "./BriefFields";

/** How far the brief has got: sections done out of five, one segment each. */
export function ReadinessBar({ position }: { position: Position }) {
  const done = doneSteps(position);
  const count = done.filter(Boolean).length;
  const complete = count === POSITION_STEPS.length;

  return (
    <BriefPanel className="px-6 py-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <span className="text-[15px] font-semibold text-u-text">
          Profile readiness
          <span className={cn("ms-2.5 font-u-num text-[13px] font-medium", complete ? "text-u-direct" : "text-u-signal")}>
            {count} of {POSITION_STEPS.length} sections complete
          </span>
        </span>
        <span className="text-[12px] text-u-text3">{completion(position)}% overall completeness</span>
      </div>
      <div className="mt-3 flex gap-1.5" aria-hidden="true">
        {done.map((isDone, index) => (
          <span key={POSITION_STEPS[index].key} className={cn("h-1.5 flex-1 rounded-full", isDone ? "bg-u-direct" : "bg-u-signal")} />
        ))}
      </div>
    </BriefPanel>
  );
}
