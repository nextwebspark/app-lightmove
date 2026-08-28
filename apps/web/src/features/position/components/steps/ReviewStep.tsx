import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { formatInstantDate } from "../../../../lib/format";
import type { Position } from "../../api/types";
import { labelOfNode, managerOf } from "../../lib/orgChart";
import { REVIEWABLE_STEPS, panelTotal, type StepKey } from "../../lib/steps";

/**
 * Step six: read the brief back before calling it ready.
 *
 * The checklist reports readiness; it does not gate anything. V38 retired the readiness gate along
 * with the lock, so publishing is available whatever the checklist says and an unfinished brief can
 * still be declared ready by someone who means it.
 */
export function ReviewStep({
  position,
  canEdit,
  onEditStep,
  onWithdraw,
}: {
  position: Position;
  /**
   * Whether each section offers its way in. A published brief reads back rather than invites edits
   * until somebody says they mean to change it — see the rail's "Edit position". Not a lock: the
   * fields themselves never stop accepting input, and V38 retired the one that did.
   */
  canEdit: boolean;
  onEditStep: (key: StepKey) => void;
  /**
   * Taking the publication back, offered only while the brief is being changed — it is the one
   * moment somebody is asking what publishing means, and a control this consequential should not sit
   * under the reader's cursor the rest of the time. Absent when there is nothing to withdraw.
   */
  onWithdraw: (() => void) | null;
}) {
  const published = position.publication.publishedAt;

  return (
    <div className="flex flex-col gap-5">
      {published && (
        <div className="flex flex-wrap items-center gap-3 rounded-[10px] border border-green/40 bg-green-dim px-[18px] py-3.5">
          <Icon d={ICONS.checkCircle} size={18} className="flex-none text-green" />
          <span className="min-w-0">
            <span className="block text-[13px] font-semibold text-green">
              Position profile published
            </span>
            <span className="mt-px block font-mono text-[11.5px] text-text3">
              {position.publication.publishedBy
                ? `${position.publication.publishedBy} · ${formatInstantDate(published)}`
                : formatInstantDate(published)}
              {canEdit ? " · the brief stays editable" : " · Edit position to change it"}
            </span>
          </span>
          {onWithdraw && (
            <button
              type="button"
              onClick={onWithdraw}
              className="ms-auto text-[11.5px] font-semibold text-text3 hover:text-red hover:underline"
            >
              Withdraw publication
            </button>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {REVIEWABLE_STEPS.map((step, index) => (
          <div
            key={step.key}
            className="rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-4"
          >
            <div className="flex items-center gap-2">
              <span className="font-mono text-[10px] font-bold uppercase tracking-[0.1em] text-text3">
                {index + 1}. {step.name}
              </span>
              {canEdit && (
                <button
                  type="button"
                  onClick={() => onEditStep(step.key)}
                  className="ms-auto text-[11.5px] font-semibold text-amber hover:underline"
                >
                  Edit
                </button>
              )}
            </div>
            <span className="mt-2 block truncate text-[13px] font-semibold text-text">
              {step.summary(position)}
            </span>
            <span className="mt-0.5 block truncate font-mono text-[11.5px] text-text3">
              {step.detail(position)}
            </span>
          </div>
        ))}
      </div>

      <div className="rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-4">
        <span className="font-mono text-[10px] font-bold uppercase tracking-[0.1em] text-text3">
          Readiness
        </span>
        <div className="mt-3 flex flex-col gap-2.5">
          {readinessOf(position).map((item) => (
            <span key={item.label} className="flex items-start gap-2.5">
              <span
                className={cn(
                  "mt-px grid size-[18px] flex-none place-items-center rounded-full",
                  item.met ? "bg-green-dim text-green" : "bg-panel text-text3",
                )}
              >
                {item.met ? <Icon d={ICONS.check} size={11} /> : <span className="text-[10px]">!</span>}
              </span>
              <span className={cn("text-[13px]", item.met ? "text-text2" : "text-text3")}>
                {item.label}
              </span>
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function readinessOf(position: Position): { label: string; met: boolean }[] {
  return [
    {
      label: "Role parameters, mandate context and reporting lines specified",
      met: Boolean(
        position.details.roleTitle.trim() &&
          position.details.location?.trim() &&
          position.context.businessDriver?.trim() &&
          labelOfNode(managerOf(position.reporting.orgChart)),
      ),
    },
    {
      label: "Compensation package captured with allowances quantified",
      met:
        position.compensation.salaryMin !== null &&
        position.compensation.salaryMax !== null &&
        position.compensation.benefits.every((benefit) => benefit.amount !== null),
    },
    {
      label: "Technical and behavioural weighting totals exactly 100%",
      met: panelTotal(position, "technical") === 100 && panelTotal(position, "behavioural") === 100,
    },
  ];
}
