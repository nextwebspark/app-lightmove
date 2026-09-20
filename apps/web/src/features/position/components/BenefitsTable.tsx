import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { Benefit, BenefitFrequency } from "../api/types";
import { BENEFIT_PRESETS } from "../lib/benefits";
import { BENEFIT_FREQUENCY_LABELS } from "../lib/labels";
import { BriefButton, ChipGroup, ColumnHead, FigureInput, RemoveDot, type ChipOption } from "./BriefFields";

const FREQUENCY_OPTIONS: ChipOption<BenefitFrequency>[] = (
  Object.entries(BENEFIT_FREQUENCY_LABELS) as [BenefitFrequency, string][]
).map(([value, label]) => ({ value, label }));

const BLANK: Benefit = { name: "", amount: null, frequency: "MONTHLY" };

// Header, rows and the add row share one template so their columns cannot drift apart.
const GRID = "grid grid-cols-[minmax(0,1fr)_120px_160px_28px] items-center gap-3 px-4";

/**
 * The benefits and allowances a package names, one line each: what it is, how much, and whether that
 * figure is monthly or yearly. An amount may stay blank — a package often names an allowance without
 * quantifying it — and the review calls that out rather than this table refusing the line.
 */
export function BenefitsTable({
  benefits,
  onChange,
}: {
  benefits: Benefit[];
  /** `immediate` for adding and removing a line, which are decisions rather than typing. */
  onChange: (benefits: Benefit[], immediate?: boolean) => void;
}) {
  const [draft, setDraft] = useState<Benefit>(BLANK);

  const patch = (index: number, changes: Partial<Benefit>) =>
    onChange(benefits.map((benefit, i) => (i === index ? { ...benefit, ...changes } : benefit)));

  const add = () => {
    const name = draft.name.trim();
    if (!name) return;
    onChange([...benefits, { ...draft, name }], true);
    setDraft(BLANK);
  };

  return (
    <div className="overflow-x-auto rounded-[11px] bg-u-surface shadow-u-e1">
      <div className="min-w-[600px]">
        <div className={cn(GRID, "border-b border-u-border py-3")}>
          <ColumnHead>Benefit</ColumnHead>
          <ColumnHead className="text-center">Amount</ColumnHead>
          <ColumnHead className="text-center">Frequency</ColumnHead>
          <span />
        </div>

        {benefits.map((benefit, index) => (
          <div key={index} className={cn(GRID, "border-b border-u-border py-2.5")}>
            <input
              value={benefit.name}
              aria-label={`Benefit ${index + 1} name`}
              onChange={(event) => patch(index, { name: event.target.value })}
              className="min-w-0 bg-transparent text-[14px] text-u-text outline-none"
            />
            <FigureInput
              grouped
              value={benefit.amount}
              aria-label={`${benefit.name} amount`}
              placeholder="–"
              onChange={(amount) => patch(index, { amount })}
              className="border-transparent text-center text-[14px] focus:border-u-accent"
            />
            <ChipGroup
              size="sm"
              label={`${benefit.name} frequency`}
              options={FREQUENCY_OPTIONS}
              value={benefit.frequency}
              onChange={(frequency) => frequency && patch(index, { frequency })}
              className="flex-nowrap justify-center"
            />
            <RemoveDot
              label={`Remove ${benefit.name}`}
              onClick={() => onChange(benefits.filter((_, i) => i !== index), true)}
            />
          </div>
        ))}

        <div className={cn(GRID, "py-2.5")}>
          <div className="flex min-w-0 items-center gap-2 text-u-text3">
            <Icon d={ICONS.plus} size={13} className="flex-none" />
            <input
              value={draft.name}
              list="brief-benefit-presets"
              aria-label="New benefit name"
              placeholder="Add benefit or allowance…"
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              onKeyDown={(event) => {
                if (event.key !== "Enter") return;
                event.preventDefault();
                add();
              }}
              className="min-w-0 flex-1 bg-transparent text-[14px] text-u-text outline-none placeholder:text-u-text3"
            />
          </div>
          <FigureInput
            grouped
            value={draft.amount}
            aria-label="New benefit amount"
            placeholder="–"
            onChange={(amount) => setDraft({ ...draft, amount })}
            className="border-transparent text-center text-[14px] focus:border-u-accent"
          />
          <ChipGroup
            size="sm"
            label="New benefit frequency"
            options={FREQUENCY_OPTIONS}
            value={draft.frequency}
            onChange={(frequency) => frequency && setDraft({ ...draft, frequency })}
            className="flex-nowrap justify-center"
          />
          <BriefButton variant="link" onClick={add} aria-label="Add benefit" className="px-0 text-[12px]">
            Add
          </BriefButton>
        </div>

        <datalist id="brief-benefit-presets">
          {BENEFIT_PRESETS.map((preset) => (
            <option key={preset} value={preset} />
          ))}
        </datalist>
      </div>
    </div>
  );
}
