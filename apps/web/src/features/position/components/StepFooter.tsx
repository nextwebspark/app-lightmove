import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { POSITION_STEPS, STEP_PARAM, stepIndexOf, type StepKey } from "../lib/steps";
import { BriefButton, OUTLINE_LINK_CLASS } from "./BriefFields";

/**
 * The way through the brief at the foot of every step: the step before on one side, the step after
 * on the other. The last step has no step after it, so once the brief is published it offers the
 * mandate's market instead — which is what is left to do.
 *
 * <p>Walking is all this row does. Declaring the brief ready is the rail's act, offered on every
 * step, so nothing here publishes and nothing here is filled: the palette keeps the solid accent for
 * the one button a screen is actually asking for.
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
