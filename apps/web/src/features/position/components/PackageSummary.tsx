import { cn } from "../../../lib/cn";
import type { Compensation } from "../api/types";
import { formatAmount, packageMix, packageTotal } from "../lib/compensation";
import { BriefPanel, Eyebrow } from "./BriefFields";

/** The mix bar's three colours, in the order `packageMix` lists its rows: base, bonus, the rest. */
const SEGMENTS = ["bg-u-accent", "bg-u-direct", "bg-u-signal"] as const;

/** The tinted panel at the foot of Compensation: the annual total and what it is made of. */
export function PackageSummary({ compensation }: { compensation: Compensation }) {
  const total = packageTotal(compensation);
  const mix = packageMix(total);
  const currency = compensation.currency;

  return (
    <BriefPanel tone="accent" className="px-6 py-5">
      <Eyebrow>Total target annual package</Eyebrow>
      <div className="mt-3 font-u-num text-[26px] font-medium tracking-[-0.02em] text-u-text sm:text-[30px]">
        {total.min === null || total.max === null
          ? "—"
          : `${formatAmount(currency, total.min)} – ${formatAmount(currency, total.max)}`}
      </div>

      <div className="mt-4 flex h-2.5 gap-0.5 overflow-hidden rounded-full bg-u-sunken" aria-hidden="true">
        {mix.map((row, index) =>
          row.percent > 0 ? (
            <span
              key={row.label}
              style={{ width: `${row.percent}%` }}
              className={cn("rounded-full", SEGMENTS[index])}
            />
          ) : null,
        )}
      </div>

      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        {mix.map((row) => (
          <div key={row.label} className="rounded-[8px] border border-u-border bg-u-bg px-3.5 py-3">
            <span className="block text-[12px] text-u-text2">{row.label}</span>
            <span className="mt-1 block font-u-num text-[15px] font-medium text-u-text">
              {formatAmount(currency, row.amount)}
            </span>
          </div>
        ))}
      </div>
    </BriefPanel>
  );
}
