import { DetailGrid, DetailTile } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import { formatNumber } from "../../../lib/format";
import type { CandidateCompensation } from "../api/types";
import {
  formatAmount,
  incentiveTypesLabel,
  packageOf,
  recordedAllowanceLines,
  type PackagePart,
} from "../lib/compensation";

/**
 * A package as the profile reads it: the four figures as tiles, then the total with how it is made
 * up. The split is the reading an executive-search consultant actually wants — how much of the
 * package is at risk — and a bar says it faster than four numbers do. The allowance lines and the
 * LTIP's instruments the editor records are read back beside the figures they make up.
 */
export function CompensationSummary({ compensation }: { compensation: CandidateCompensation }) {
  const currency = compensation.currency ?? "";
  const allowanceLines = recordedAllowanceLines(compensation.allowanceLines);
  const instruments = incentiveTypesLabel(compensation.longTermIncentiveTypes ?? []);
  const ltipAmount = formatAmount(currency, compensation.longTermIncentive);
  return (
    <>
      <DetailGrid>
        <DetailTile label="Base" value={formatAmount(currency, compensation.baseSalary)} />
        <DetailTile label="Bonus" value={formatAmount(currency, compensation.bonus)} />
        <DetailTile label="Allowances" value={formatAmount(currency, compensation.allowances)} />
        <DetailTile
          label="LTIP"
          value={ltipAmount && instruments ? `${ltipAmount} · ${instruments}` : (ltipAmount ?? instruments)}
        />
        <DetailTile label="Notice period" value={compensation.noticePeriod} full />
      </DetailGrid>
      {allowanceLines.length > 0 && (
        <div className="mt-3">
          <div className="mb-1 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
            Allowance breakdown
          </div>
          <ul className="overflow-hidden rounded-[7px] border border-u-border">
            {allowanceLines.map((line, index) => (
              <li
                key={`${line.label}-${index}`}
                className="flex items-center gap-2 border-b border-u-border bg-u-raised px-2.5 py-[7px] font-mono text-[12.5px] last:border-b-0"
              >
                <span className="min-w-0 flex-1 truncate text-u-text2">{line.label?.trim() || "Allowance"}</span>
                <span className="flex-none font-bold text-u-text">
                  {formatAmount(currency, line.amount) ?? "—"}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
      <PackageTotal currency={currency} compensation={compensation} />
    </>
  );
}

const PART_COLOURS: Record<PackagePart["key"], string> = {
  baseSalary: "bg-u-accent",
  bonus: "bg-u-direct",
  allowances: "bg-u-accent",
  longTermIncentive: "bg-u-text3",
};

/**
 * The total and its composition. Shown once something is established, and always while the
 * figures are being typed, where it is the running answer to "does that add up to what they said".
 */
export function PackageTotal({
  currency,
  compensation,
  live = false,
}: {
  currency: string;
  compensation: Pick<CandidateCompensation, PackagePart["key"]>;
  /** Under a form: the row stays on screen at nought so the reader sees it move. */
  live?: boolean;
}) {
  const { total, parts } = packageOf(compensation);
  if (total === 0 && !live) return null;

  return (
    <div className="mt-3 rounded-[8px] border border-u-border bg-u-raised px-3 py-2.5">
      <div className="flex items-center justify-between">
        <span className="font-mono text-[12px] text-u-text2">
          Total{live && <span className="ms-1.5 text-[10.5px] text-u-text3">· as typed</span>}
        </span>
        <span
          data-testid="package-total"
          className={cn("font-mono text-[15px] font-bold", total > 0 ? "text-u-text" : "text-u-text3")}
        >
          {total > 0 ? `${currency} ${formatNumber(total)}`.trim() : "—"}
        </span>
      </div>
      {parts.length > 1 && (
        <>
          <div aria-hidden="true" className="mt-2.5 flex h-[6px] gap-px overflow-hidden rounded-full">
            {parts.map((part) => (
              <span
                key={part.key}
                className={cn("h-full", PART_COLOURS[part.key])}
                style={{ width: `${part.share * 100}%` }}
              />
            ))}
          </div>
          <ul className="mt-2 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[10.5px] text-u-text3">
            {parts.map((part) => (
              <li key={part.key} className="flex items-center gap-1.5">
                <span
                  aria-hidden="true"
                  className={cn("size-[7px] rounded-full", PART_COLOURS[part.key])}
                />
                {part.label} {Math.round(part.share * 100)}%
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
