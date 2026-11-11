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
 * One section of the brief read back; an incomplete one says why in copper, frame included. A published brief
 * offers no way in until Edit position, or the card would contradict the rail.
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
        "rounded-[10px] px-4 py-3.5 shadow-u-e1",
        done ? "bg-u-surface" : "border border-u-signal bg-u-signal-tint/40",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <Icon d={step.icon} size={14} className="flex-none text-u-text2" />
        <span className="type-heading text-u-text">{step.name}</span>
        <StatusBadge tone={done ? "complete" : "attention"}>{done ? "Complete" : "Needs attention"}</StatusBadge>
        {canEdit && (
          <Link
            to={{ search: `?${STEP_PARAM}=${step.key}` }}
            className="ms-auto inline-flex items-center gap-1.5 text-note font-medium text-u-accent hover:underline"
          >
            Edit section
            <Icon d={ICONS.pencil} size={12} />
          </Link>
        )}
      </div>

      <dl className="mt-3 grid grid-cols-1 gap-x-6 gap-y-2.5 sm:grid-cols-2 lg:grid-cols-4">
        {fields.map((field) => (
          <div key={field.label} className="min-w-0">
            <dt>
              <Eyebrow size="summary">{field.label}</Eyebrow>
            </dt>
            <dd className="mt-0.5 truncate text-body text-u-text">{field.value}</dd>
          </div>
        ))}
      </dl>

      {attention && (
        <div className="mt-3 flex items-center gap-2 rounded-[8px] bg-u-signal-tint px-3 py-2 text-note text-u-signal">
          <Icon d={ICONS.warning} size={14} className="flex-none" />
          {attention}
        </div>
      )}
    </section>
  );
}
