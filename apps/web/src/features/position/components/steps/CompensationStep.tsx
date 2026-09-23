import { SegmentedControl, type SegmentedOption } from "../../../../components/ui/SegmentedControl";
import type { BaseSalaryMode, BonusBasis, Compensation, IncentiveType } from "../../api/types";
import { formatAmount, packageTotal } from "../../lib/compensation";
import {
  BASE_SALARY_MODE_LABELS,
  BONUS_BASIS_LABELS,
  CURRENCIES,
  INCENTIVE_TYPE_LABELS,
  OFFERED_BONUS_BASES,
  OFFERED_INCENTIVE_TYPES,
} from "../../lib/labels";
import { BenefitsTable } from "../BenefitsTable";
import { BriefPanel, ChipGroup, Eyebrow, FieldBlock, FigureInput, UnderlineField, withRecorded, type ChipOption } from "../BriefFields";
import { PackageSummary } from "../PackageSummary";

const PERIOD_OPTIONS: SegmentedOption<BaseSalaryMode>[] = (
  Object.entries(BASE_SALARY_MODE_LABELS) as [BaseSalaryMode, string][]
).map(([value, label]) => ({ value, label }));

const BONUS_OPTIONS: ChipOption<BonusBasis>[] = OFFERED_BONUS_BASES.map((value) => ({
  value,
  label: BONUS_BASIS_LABELS[value],
}));

/** "None" is the absence of an incentive, which the wire spells as null rather than a fourth kind. */
type IncentiveChoice = IncentiveType | "NONE";

const INCENTIVE_OPTIONS: ChipOption<IncentiveChoice>[] = [
  ...OFFERED_INCENTIVE_TYPES.map((value) => ({ value, label: INCENTIVE_TYPE_LABELS[value] })),
  { value: "NONE", label: "None" },
];

const BONUS_CAPTIONS: Record<BonusBasis, string> = {
  PERCENT_OF_BASE: "of base salary",
  FIXED_AMOUNT: "per year",
  PERCENT_OF_TOTAL_FIXED: "of total fixed",
  MONTHS_OF_BASE: "months of base",
};

/** Step three: what the seat pays, and what that adds up to over a year. */
export function CompensationStep({
  compensation,
  onChange,
}: {
  compensation: Compensation;
  onChange: (patch: Partial<Compensation>, immediate?: boolean) => void;
}) {
  const { currency } = compensation;
  const isFixed = compensation.bonusBasis === "FIXED_AMOUNT";
  const total = packageTotal(compensation);
  const incentiveChoice: IncentiveChoice = compensation.incentiveType ?? "NONE";

  const calculatedBonus =
    total.bonus.max <= 0
      ? "—"
      : isFixed
        ? formatAmount(currency, total.bonus.max)
        : `${formatAmount(currency, total.bonus.min)} – ${formatAmount(currency, total.bonus.max)}`;

  return (
    <div className="flex flex-col gap-5">
      <BriefPanel>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Eyebrow>Base salary</Eyebrow>
          <SegmentedControl
            variant="uncava"
            label="Base salary period"
            options={PERIOD_OPTIONS}
            value={compensation.baseSalaryMode}
            onChange={(baseSalaryMode) => onChange({ baseSalaryMode }, true)}
          />
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-4">
          <select
            aria-label="Currency"
            value={currency}
            onChange={(event) => onChange({ currency: event.target.value }, true)}
            className="rounded-[8px] border border-u-border bg-u-sunken px-3 py-1.5 text-body font-semibold text-u-text outline-none"
          >
            {CURRENCIES.map((code) => (
              <option key={code} value={code}>
                {code}
              </option>
            ))}
          </select>
          <FigureInput
            grouped
            value={compensation.salaryMin}
            aria-label="Minimum base salary"
            placeholder="90,000"
            onChange={(salaryMin) => onChange({ salaryMin })}
            className="w-[140px]"
          />
          <span className="text-u-text3">–</span>
          <FigureInput
            grouped
            value={compensation.salaryMax}
            aria-label="Maximum base salary"
            placeholder="120,000"
            onChange={(salaryMax) => onChange({ salaryMax })}
            className="w-[140px]"
          />
        </div>
      </BriefPanel>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <BriefPanel>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Eyebrow>Annual bonus target</Eyebrow>
            <ChipGroup
              size="sm"
              label="Bonus basis"
              options={withRecorded(BONUS_OPTIONS, compensation.bonusBasis, (basis) => BONUS_BASIS_LABELS[basis])}
              value={compensation.bonusBasis}
              onChange={(bonusBasis) => onChange({ bonusBasis }, true)}
              className="flex-nowrap"
            />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            {isFixed && <span className="type-figure-input text-u-text2">{currency}</span>}
            <FigureInput
              grouped={isFixed}
              value={compensation.bonusValue}
              aria-label="Bonus target"
              placeholder={isFixed ? "150,000" : "30"}
              onChange={(bonusValue) => onChange({ bonusValue })}
              className={isFixed ? "w-full max-w-[240px]" : "w-[4ch]"}
            />
            {!isFixed && <span className="type-figure-input text-u-text2">%</span>}
          </div>
          <span className="mt-1 block text-note text-u-text3">
            {compensation.bonusBasis ? BONUS_CAPTIONS[compensation.bonusBasis] : "choose what the figure is read against"}
          </span>
          <div className="mt-4 border-t border-u-border pt-3 text-note text-u-text2">
            Calculated: <span className="font-u-num">{calculatedBonus}</span>
          </div>
        </BriefPanel>

        <BriefPanel>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Eyebrow>LTIP</Eyebrow>
            <ChipGroup
              size="sm"
              label="Long-term incentive"
              options={withRecorded(INCENTIVE_OPTIONS, incentiveChoice, (choice) =>
                choice === "NONE" ? "None" : INCENTIVE_TYPE_LABELS[choice],
              )}
              value={incentiveChoice}
              onChange={(choice) => onChange({ incentiveType: choice === null || choice === "NONE" ? null : choice }, true)}
              className="flex-nowrap"
            />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="type-figure-input text-u-text2">{currency}</span>
            <FigureInput
              grouped
              value={compensation.incentiveAmount}
              aria-label="Incentive amount"
              placeholder="600,000"
              onChange={(incentiveAmount) => onChange({ incentiveAmount })}
              className="w-full max-w-[240px]"
            />
          </div>
          <span className="mt-1 block text-note text-u-text3">total value</span>
          <div className="mt-4 border-t border-u-border pt-2">
            <UnderlineField
              value={compensation.incentiveVesting ?? ""}
              aria-label="Vesting schedule"
              placeholder="Vesting schedule, e.g. 4-year vesting"
              onChange={(event) => onChange({ incentiveVesting: event.target.value || null })}
              // Quiet inline caption under the LTIP figure, not a field of its own — kept borderless
              // and unboxed rather than inheriting UnderlineField's usual box.
              className="rounded-none border-0 bg-transparent px-0 py-2 text-u-text2"
            />
          </div>
        </BriefPanel>
      </div>

      <FieldBlock label="Benefits & allowances">
        <BenefitsTable
          benefits={compensation.benefits}
          onChange={(benefits, immediate) => onChange({ benefits }, immediate)}
        />
      </FieldBlock>

      <PackageSummary compensation={compensation} />
    </div>
  );
}
