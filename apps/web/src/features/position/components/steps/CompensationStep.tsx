import { useState } from "react";
import { Input, Select } from "../../../../components/ui";
import { formatNumber } from "../../../../lib/format";
import type { Benefit, Compensation } from "../../api/types";
import { bandReadings, packageMix, packageTotal } from "../../lib/compensation";
import { BENEFIT_PRESETS } from "../../lib/benefits";
import {
  BASE_SALARY_MODE_LABELS,
  BENEFIT_FREQUENCY_LABELS,
  BONUS_BASIS_LABELS,
  CURRENCIES,
  INCENTIVE_TYPE_LABELS,
} from "../../lib/labels";
import {
  AddRowButton,
  ColumnLabel,
  MoneyInput,
  NumberInput,
  RemoveRowButton,
  SegmentedControl,
  StepField,
  SubCard,
} from "../fields";

/** Step four: what the seat pays, and what that adds up to over a year. */
export function CompensationStep({
  compensation,
  onChange,
}: {
  compensation: Compensation;
  onChange: (patch: Partial<Compensation>, immediate?: boolean) => void;
}) {
  const [draft, setDraft] = useState<Benefit>({ name: "", amount: null, frequency: "MONTHLY" });
  const total = packageTotal(compensation);
  const band = bandReadings(compensation);
  const money = (amount: number | null) =>
    amount === null ? "—" : `${compensation.currency} ${formatNumber(Math.round(amount))}`;

  const addBenefit = () => {
    const name = draft.name.trim();
    if (!name) return;
    onChange({ benefits: [...compensation.benefits, { ...draft, name }] }, true);
    setDraft({ name: "", amount: null, frequency: "MONTHLY" });
  };
  const patchBenefit = (index: number, changes: Partial<Benefit>) =>
    onChange({
      benefits: compensation.benefits.map((benefit, i) =>
        i === index ? { ...benefit, ...changes } : benefit,
      ),
    });

  return (
    <div className="flex flex-col gap-5">
      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Base salary
        </span>
        <SubCard>
          <div className="flex flex-wrap items-center gap-2">
            <Select
              aria-label="Currency"
              value={compensation.currency}
              onChange={(event) => onChange({ currency: event.target.value }, true)}
              className="w-[92px] flex-none bg-panel"
            >
              {CURRENCIES.map((currency) => (
                <option key={currency} value={currency}>
                  {currency}
                </option>
              ))}
            </Select>
            <div className="min-w-[110px] flex-1 rounded-lg border border-line bg-panel px-3 py-1.5">
              <MoneyInput
                value={compensation.salaryMin}
                aria-label="Minimum base salary"
                placeholder="90,000"
                onChange={(salaryMin) => onChange({ salaryMin })}
              />
            </div>
            <span className="font-mono text-[13px] text-text3">–</span>
            <div className="min-w-[110px] flex-1 rounded-lg border border-line bg-panel px-3 py-1.5">
              <MoneyInput
                value={compensation.salaryMax}
                aria-label="Maximum base salary"
                placeholder="120,000"
                onChange={(salaryMax) => onChange({ salaryMax })}
              />
            </div>
            <SegmentedControl
              label="Base salary period"
              className="flex-none"
              value={compensation.baseSalaryMode}
              onChange={(baseSalaryMode) => onChange({ baseSalaryMode }, true)}
              options={Object.entries(BASE_SALARY_MODE_LABELS).map(([value, label]) => ({
                value: value as Compensation["baseSalaryMode"],
                label,
              }))}
            />
          </div>

          <div className="mt-4">
            <div className="h-[3px] rounded-full bg-gradient-to-r from-line via-sky to-line" />
            <div className="mt-2 flex justify-between font-mono text-[11px] text-text3">
              <span>Min {money(band.min)}</span>
              <span className="text-sky">Mid {money(band.mid)}</span>
              <span>Max {money(band.max)}</span>
            </div>
          </div>
        </SubCard>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-x-[18px]">
        <StepField label="Annual bonus target" hint={bonusHint(total, compensation.currency)}>
          <div className="flex gap-2">
            <div className="min-w-0 flex-1 rounded-lg border border-line bg-panel2 px-3 py-1.5">
              <NumberInput
                value={compensation.bonusValue}
                aria-label="Bonus target"
                placeholder="40"
                onChange={(bonusValue) => onChange({ bonusValue })}
              />
            </div>
            <Select
              aria-label="Bonus basis"
              value={compensation.bonusBasis ?? ""}
              onChange={(event) =>
                onChange(
                  { bonusBasis: (event.target.value || null) as Compensation["bonusBasis"] },
                  true,
                )
              }
              className="w-[190px] flex-none"
            >
              <option value="">Not set</option>
              {Object.entries(BONUS_BASIS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </StepField>

        <StepField label="Long-term incentive">
          <div className="flex flex-wrap gap-2">
            <Select
              aria-label="Incentive type"
              value={compensation.incentiveType ?? ""}
              onChange={(event) =>
                onChange(
                  { incentiveType: (event.target.value || null) as Compensation["incentiveType"] },
                  true,
                )
              }
              className="w-[150px] flex-none"
            >
              <option value="">Not set</option>
              {Object.entries(INCENTIVE_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
            <div className="min-w-[110px] flex-1 rounded-lg border border-line bg-panel2 px-3 py-1.5">
              <MoneyInput
                value={compensation.incentiveAmount}
                aria-label="Incentive amount"
                placeholder="600,000"
                onChange={(incentiveAmount) => onChange({ incentiveAmount })}
              />
            </div>
          </div>
          <Input
            value={compensation.incentiveVesting ?? ""}
            aria-label="Vesting schedule"
            placeholder="Vesting schedule"
            onChange={(event) => onChange({ incentiveVesting: event.target.value || null })}
            className="mt-2"
          />
        </StepField>
      </div>

      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Benefits &amp; allowances
        </span>
        <div className="overflow-x-auto rounded-[10px] border border-line-soft bg-panel2">
          <div className="min-w-[560px]">
            <div className="grid grid-cols-[minmax(0,1fr)_130px_150px_30px] gap-2.5 border-b border-line-soft px-3.5 py-[9px]">
              <ColumnLabel>Benefit</ColumnLabel>
              <ColumnLabel>Amount</ColumnLabel>
              <ColumnLabel>Frequency</ColumnLabel>
              <span />
            </div>
            {compensation.benefits.map((benefit, index) => (
              <div
                key={index}
                className="grid grid-cols-[minmax(0,1fr)_130px_150px_30px] items-center gap-2.5 border-b border-line-soft px-3.5 py-2"
              >
                <input
                  value={benefit.name}
                  aria-label={`Benefit ${index + 1} name`}
                  onChange={(event) => patchBenefit(index, { name: event.target.value })}
                  className="min-w-0 bg-transparent text-[13px] font-medium text-text outline-none"
                />
                <MoneyInput
                  value={benefit.amount}
                  aria-label={`${benefit.name} amount`}
                  placeholder="—"
                  onChange={(amount) => patchBenefit(index, { amount })}
                />
                <SegmentedControl
                  size="sm"
                  label={`${benefit.name} frequency`}
                  value={benefit.frequency}
                  onChange={(frequency) => patchBenefit(index, { frequency })}
                  options={Object.entries(BENEFIT_FREQUENCY_LABELS).map(([value, label]) => ({
                    value: value as Benefit["frequency"],
                    label,
                  }))}
                />
                <RemoveRowButton
                  label={`Remove ${benefit.name}`}
                  onClick={() =>
                    onChange(
                      { benefits: compensation.benefits.filter((_, i) => i !== index) },
                      true,
                    )
                  }
                />
              </div>
            ))}
            <div className="grid grid-cols-[minmax(0,1fr)_130px_150px_30px] items-center gap-2.5 px-3.5 py-2.5">
              <Input
                value={draft.name}
                list="position-benefit-presets"
                aria-label="New benefit name"
                placeholder="e.g. Housing allowance"
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                onKeyDown={(event) => {
                  if (event.key !== "Enter") return;
                  event.preventDefault();
                  addBenefit();
                }}
                className="bg-panel"
              />
              <div className="rounded-lg border border-line bg-panel px-3 py-1.5">
                <MoneyInput
                  value={draft.amount}
                  aria-label="New benefit amount"
                  placeholder="Amount"
                  onChange={(amount) => setDraft({ ...draft, amount })}
                />
              </div>
              <Select
                aria-label="New benefit frequency"
                value={draft.frequency}
                onChange={(event) =>
                  setDraft({ ...draft, frequency: event.target.value as Benefit["frequency"] })
                }
                className="bg-panel"
              >
                {Object.entries(BENEFIT_FREQUENCY_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
              <AddRowButton onClick={addBenefit} className="px-2 py-1.5 text-[11px]">
                Add
              </AddRowButton>
            </div>
            <datalist id="position-benefit-presets">
              {BENEFIT_PRESETS.map((preset) => (
                <option key={preset} value={preset} />
              ))}
            </datalist>
          </div>
        </div>
      </div>

      <PackageTotalPanel compensation={compensation} />
    </div>
  );
}

/** The amber panel: the annual total and what it is made of. */
function PackageTotalPanel({ compensation }: { compensation: Compensation }) {
  const total = packageTotal(compensation);
  const mix = packageMix(total);
  const money = (amount: number) =>
    `${compensation.currency} ${formatNumber(Math.round(amount))}`;

  return (
    <div className="rounded-[10px] border border-amber-btn/35 bg-amber-dim/40 px-[18px] py-4">
      <div className="flex flex-wrap items-center gap-2.5">
        <ColumnLabel className="tracking-[0.12em]">Total target annual package</ColumnLabel>
      </div>
      <div className="mt-2 text-[21px] font-bold text-text">
        {total.min === null || total.max === null
          ? "—"
          : `${money(total.min)} – ${money(total.max)}`}
      </div>

      <div className="mt-3.5 flex h-2 overflow-hidden rounded-full bg-line">
        {mix.map((row, index) => (
          <span
            key={row.label}
            style={{ width: `${row.percent}%` }}
            className={index === 0 ? "bg-sky" : index === 1 ? "bg-amber-btn" : "bg-green"}
            aria-hidden="true"
          />
        ))}
      </div>
      <div className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1">
        {mix.map((row, index) => (
          <span key={row.label} className="flex items-center gap-1.5 font-mono text-[11px] text-text3">
            <span
              className={`size-2 rounded-full ${
                index === 0 ? "bg-sky" : index === 1 ? "bg-amber-btn" : "bg-green"
              }`}
            />
            {row.label} · {money(row.amount)}
          </span>
        ))}
      </div>
    </div>
  );
}

function bonusHint(total: ReturnType<typeof packageTotal>, currency: string): string | undefined {
  if (total.bonus.max <= 0) return undefined;
  return `${currency} ${formatNumber(Math.round(total.bonus.min))} – ${currency} ${formatNumber(
    Math.round(total.bonus.max),
  )}`;
}
