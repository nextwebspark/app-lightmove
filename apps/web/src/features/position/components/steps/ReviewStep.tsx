import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { formatDate, formatInstantDate } from "../../../../lib/format";
import { SENIORITY_LABELS } from "../../../../lib/seniority";
import type { Position } from "../../api/types";
import { annualisedBase, formatAmount, packageTotal } from "../../lib/compensation";
import { BONUS_BASIS_LABELS, labelOf } from "../../lib/labels";
import { directReportsOf, labelOfNode, managerOf } from "../../lib/orgChart";
import { REVIEWABLE_STEPS, placeLineOf, readinessOf, type StepKey } from "../../lib/steps";
import { BriefPanel } from "../BriefFields";
import { ReadinessBar } from "../ReadinessBar";
import { ReviewCard, type ReviewField } from "../ReviewCard";

/**
 * Step five: the brief read back before it is called ready. The checks report; they gate nothing —
 * V38 retired the readiness gate along with the lock, so publishing stays available whatever they
 * say, and an unfinished brief can still be declared ready by someone who means it.
 *
 * <p>Publishing is the rail's act on every step and walking is `StepFooter`'s, so this step draws
 * neither. Repeating the rail's two buttons here bought nothing but a second place to look.
 */
export function ReviewStep({
  position,
  readBack,
  onWithdraw,
}: {
  position: Position;
  /** Published and not reopened: no section offers a way in, and neither does the banner. */
  readBack: boolean;
  onWithdraw: () => void;
}) {
  const published = position.publication.publishedAt;

  return (
    <div className="flex flex-col gap-3">
      {published && (
        <div className="flex flex-wrap items-center gap-3 rounded-[10px] bg-u-direct-tint px-4 py-3 text-body text-u-direct">
          <Icon d={ICONS.checkCircle} size={16} className="flex-none" />
          <span className="min-w-0">
            Position profile published
            {position.publication.publishedBy ? ` by ${position.publication.publishedBy}` : ""} ·{" "}
            {formatInstantDate(published)} · the brief stays editable
          </span>
          {!readBack && (
            <button
              type="button"
              onClick={onWithdraw}
              className="ms-auto text-note font-semibold text-u-text3 hover:text-u-offlimits hover:underline"
            >
              Withdraw publication
            </button>
          )}
        </div>
      )}

      <ReadinessBar position={position} />

      {REVIEWABLE_STEPS.map((step) => (
        <ReviewCard
          key={step.key}
          step={step}
          done={step.isDone(position)}
          attention={step.attention(position)}
          fields={fieldsOf(step.key, position)}
          canEdit={!readBack}
        />
      ))}

      <BriefPanel className="px-4 py-3.5">
        <span className="block type-heading text-u-text">Publication readiness</span>
        <span className="mt-0.5 block text-note text-u-text3">
          What a complete brief states, so a client reads a position and not a draft.
        </span>
        <ul className="mt-3 flex flex-col gap-2.5">
          {readinessOf(position).map((item) => (
            <li key={item.label} className="flex items-start gap-3">
              <span
                className={cn(
                  "mt-px grid size-[18px] flex-none place-items-center rounded-full",
                  item.met ? "bg-u-direct-tint text-u-direct" : "bg-u-signal-tint text-u-signal",
                )}
              >
                <Icon d={item.met ? ICONS.check : ICONS.warning} size={11} />
              </span>
              <span className={cn("text-body", item.met ? "text-u-text" : "text-u-text2")}>{item.label}</span>
            </li>
          ))}
        </ul>
        <p className="mt-3 flex items-center gap-2 border-t border-u-border pt-3 text-note text-u-text3">
          <Icon d={ICONS.info} size={13} className="flex-none" />
          These checks report. Publishing records that the brief is ready and freezes nothing.
        </p>
      </BriefPanel>
    </div>
  );
}

/** The few facts each card reads back — what a reader checks before saying the section is right. */
function fieldsOf(key: StepKey, position: Position): ReviewField[] {
  const dash = (value: string | null | undefined) => (value?.trim() ? value : "—");
  switch (key) {
    case "brief":
      return [
        { label: "Role title", value: dash(position.details.roleTitle) },
        { label: "Department", value: dash(position.details.department) },
        { label: "Location", value: dash(placeLineOf(position)) },
      ];
    case "reporting": {
      const reports = directReportsOf(position.reporting.orgChart).length;
      return [
        { label: "Reports to", value: dash(labelOfNode(managerOf(position.reporting.orgChart))) },
        { label: "Direct reports", value: `${reports} direct report${reports === 1 ? "" : "s"}` },
        { label: "Org level", value: dash(labelOf(SENIORITY_LABELS, position.details.seniority)) },
        { label: "Effective date", value: formatDate(position.reporting.targetStart) },
      ];
    }
    case "compensation": {
      const { compensation } = position;
      const total = packageTotal(compensation);
      const base = annualisedBase(compensation);
      const range = (min: number | null, max: number | null) =>
        min === null || max === null
          ? "—"
          : `${formatAmount(compensation.currency, min)} to ${formatAmount(compensation.currency, max)}`;
      return [
        { label: "Total package target", value: range(total.min, total.max) },
        { label: "Base salary", value: total.min === null ? "—" : range(base.min, base.max) },
        { label: "Annual bonus target", value: bonusOf(position) },
      ];
    }
    case "assessment": {
      const rules = position.assessment.criteria.length;
      return [
        { label: "Technical competencies", value: `${position.assessment.technicalShare}% weighting` },
        { label: "Behavioural competencies", value: `${100 - position.assessment.technicalShare}% weighting` },
        { label: "Screening criteria", value: `${rules} rule${rules === 1 ? "" : "s"} active` },
      ];
    }
    case "review":
    default:
      return [];
  }
}

function bonusOf(position: Position): string {
  const { bonusValue, bonusBasis, currency } = position.compensation;
  if (bonusValue === null || bonusBasis === null) return "—";
  if (bonusBasis === "FIXED_AMOUNT") return `${formatAmount(currency, bonusValue)} fixed`;
  return `${bonusValue}${bonusBasis === "MONTHS_OF_BASE" ? " " : "% "}${BONUS_BASIS_LABELS[bonusBasis].replace(/^% /, "").toLowerCase()}`;
}
