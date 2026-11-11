import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { POSITION_STEPS, STEP_PARAM, stepIndexOf, type StepKey } from "../lib/steps";
import { BriefButton, OUTLINE_LINK_CLASS } from "./BriefFields";

/**
 * The step before and after at the foot of every step; once published, the last offers the market instead. It only
 * walks — publishing is the rail's — so nothing here is filled.
 */
export function StepFooter({
  activeKey,
  published,
  onGoToStrategy,
}: {
  activeKey: StepKey;
  published: boolean;
  onGoToStrategy: () => void;
}) {
  const index = stepIndexOf(activeKey);
  const previous = POSITION_STEPS[index - 1];
  const next = POSITION_STEPS[index + 1];

  return (
    <div className="mt-9 flex flex-wrap items-center justify-between gap-3 border-t border-u-border pt-5">
      {previous ? (
        <Link to={{ search: `?${STEP_PARAM}=${previous.key}` }} className={OUTLINE_LINK_CLASS}>
          <Icon d={ICONS.arrowLeft} size={14} className="flex-none" />
          Back to {previous.name}
        </Link>
      ) : (
        <span />
      )}

      {next ? (
        <Link to={{ search: `?${STEP_PARAM}=${next.key}` }} className={OUTLINE_LINK_CLASS}>
          Next: {next.name}
          <Icon d={ICONS.arrowRight} size={14} className="flex-none" />
        </Link>
      ) : (
        published && <BriefButton onClick={onGoToStrategy}>Move to Strategy</BriefButton>
      )}
    </div>
  );
}
