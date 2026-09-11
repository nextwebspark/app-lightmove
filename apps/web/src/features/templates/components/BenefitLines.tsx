import { Select } from "../../../components/ui";
import type { BenefitFrequency } from "../../position/api/types";
import { AddRowButton, InlineInput, RemoveRowButton } from "../../position/components/fields";
import { BENEFIT_FREQUENCY_LABELS } from "../../position/lib/labels";
import type { DraftBenefit } from "../lib/templateDraft";

const MAX_BENEFITS = 20;

/** The allowance lines a template drafts: a name and the period it is paid over, never an amount. */
export function BenefitLines({
  benefits,
  onChange,
}: {
  benefits: DraftBenefit[];
  onChange: (benefits: DraftBenefit[]) => void;
}) {
  const patch = (index: number, changes: Partial<DraftBenefit>) =>
    onChange(benefits.map((benefit, i) => (i === index ? { ...benefit, ...changes } : benefit)));

  return (
    <div>
      <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        Benefits &amp; allowances — lines only, the amount is each mandate&apos;s
      </span>
      <div className="rounded-[10px] border border-line-soft bg-panel px-3.5 pb-3 pt-1">
        {benefits.map((benefit, index) => (
          <div key={index} className="flex items-center gap-2.5 border-b border-line-soft py-2">
            <InlineInput
              value={benefit.name}
              aria-label={`Benefit ${index + 1} name`}
              maxLength={120}
              onChange={(event) => patch(index, { name: event.target.value })}
              className="min-w-0 flex-1 font-sans"
            />
            <Select
              value={benefit.frequency}
              aria-label={`Benefit ${index + 1} frequency`}
              onChange={(event) => patch(index, { frequency: event.target.value as BenefitFrequency })}
              className="w-[120px] flex-none !bg-panel2 !py-1.5"
            >
              {Object.entries(BENEFIT_FREQUENCY_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
            <RemoveRowButton
              label={`Remove benefit ${index + 1}`}
              onClick={() => onChange(benefits.filter((_, i) => i !== index))}
            />
          </div>
        ))}
        {benefits.length < MAX_BENEFITS && (
          <AddRowButton
            onClick={() => onChange([...benefits, { name: "New allowance", frequency: "MONTHLY" }])}
            className="mt-2.5 w-full"
          >
            + Add benefit
          </AddRowButton>
        )}
      </div>
    </div>
  );
}
