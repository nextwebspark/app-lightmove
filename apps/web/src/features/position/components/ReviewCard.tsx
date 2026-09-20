import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { STEP_PARAM, type PositionStep } from "../lib/steps";
import { Eyebrow, StatusBadge } from "./BriefFields";

export interface ReviewField {
  label: string;
  value: string;
}

/**
 * One section of the brief read back: its name, whether it is complete, a few of its facts, and the
 * way in. A section that is not done says why in its own words, in the copper UNCAVA keeps for a
 * warning, and wears that colour on its frame so the eye finds it before reading.
 *
 * <p>A published brief reads back with no way in at all until Edit position is pressed: offering a
 * link into a section the rail is calling settled would be the card contradicting the rail.
 */
export function ReviewCard({
  step,
  done,
  attention,
  fields,
  canEdit,
}: {
  step: PositionStep;
  done: boolean;
  attention: string | null;
  fields: ReviewField[];
  canEdit: boolean;
}) {
  return (
    <section
      aria-label={step.name}
      className={cn(
        "rounded-[11px] px-5 py-4 shadow-u-e1",
        done ? "bg-u-surface" : "border border-u-signal bg-u-signal-tint/40",
      )}
    >
      <div className="flex flex-wrap items-center gap-2.5">
        <Icon d={step.icon} size={16} className="flex-none text-u-text2" />
        <span className="text-[15px] font-semibold text-u-text">{step.name}</span>
        <StatusBadge tone={done ? "complete" : "attention"}>{done ? "Complete" : "Needs attention"}</StatusBadge>
        {canEdit && (
          <Link
            to={{ search: `?${STEP_PARAM}=${step.key}` }}
            className="ms-auto inline-flex items-center gap-1.5 text-[13px] font-medium text-u-accent hover:underline"
          >
            Edit section
            <Icon d={ICONS.pencil} size={13} />
          </Link>
        )}
      </div>

      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-4">
        {fields.map((field) => (
          <div key={field.label} className="min-w-0">
            <dt>
              <Eyebrow>{field.label}</Eyebrow>
            </dt>
            <dd className="mt-1 truncate text-[14px] text-u-text">{field.value}</dd>
          </div>
        ))}
      </dl>

      {attention && (
        <div className="mt-4 flex items-center gap-2.5 rounded-[8px] bg-u-signal-tint px-3.5 py-2.5 text-[12.5px] text-u-signal">
          <Icon d={ICONS.warning} size={14} className="flex-none" />
          {attention}
        </div>
      )}
    </section>
  );
}
