import { cn } from "../../../lib/cn";
import type { Disclosure, DisclosureOutcome } from "../api/types";
import { type CompensationStats, measureValue } from "../lib/compensationStats";
import { formatMoneyK } from "../lib/figures";

export const OUTCOME_LABEL: Record<DisclosureOutcome, string> = {
  accepted: "Accepted",
  process: "In process",
  declined: "Declined",
  withdrawn: "Withdrawn",
};

export const OUTCOME_FILL: Record<DisclosureOutcome, string> = {
  accepted: "bg-green",
  process: "bg-sky",
  declined: "bg-red",
  withdrawn: "bg-text3",
};

/**
 * One axis, one dot per disclosure, our band shaded across it and the median marked. A strip rather
 * than a histogram because sixteen named points are worth more to a consultant than four buckets —
 * each dot opens the person who said the number.
 */
export function CompensationStrip({ stats, onSelect }: { stats: CompensationStats; onSelect: (d: Disclosure) => void }) {
  const span = stats.axisHighK - stats.axisLowK;
  const at = (k: number) => `${(((k - stats.axisLowK) / span) * 100).toFixed(2)}%`;
  const bandLeft = at(stats.band.lowK);
  const bandWidth = `${(((stats.band.highK - stats.band.lowK) / span) * 100).toFixed(2)}%`;
  const unit = stats.measure === "package" ? "total comp / yr" : "base salary / yr";

  return (
    <div className="relative mx-2.5 mt-4 h-[150px]" role="group" aria-label="Verified compensation disclosures">
      <div className="absolute bottom-11 top-[26px] rounded-md bg-sky opacity-[0.08]" style={{ left: bandLeft, width: bandWidth }} />
      <div className="absolute bottom-11 top-[26px] rounded-md border border-dashed border-sky opacity-50" style={{ left: bandLeft, width: bandWidth }} />
      <div
        className="absolute top-1.5 text-center font-mono text-[9.5px] font-semibold tracking-[0.08em] text-sky"
        style={{ left: bandLeft, width: bandWidth }}
      >
        OUR OFFERED BAND
      </div>
      <div className="absolute right-0 top-1.5 font-mono text-[9.5px] text-text3">{unit}</div>
      <div className="absolute inset-x-0 top-1/2 h-px bg-line" />

      {[stats.band.lowK, stats.band.highK].map((edge) => (
        <div key={edge} className="absolute bottom-[26px] -translate-x-1/2 text-center" style={{ left: at(edge) }}>
          <div className="mx-auto h-1.5 w-px bg-sky opacity-60" />
          <div className="mt-0.5 font-mono text-[10px] font-semibold text-sky">{formatMoneyK(edge)}</div>
        </div>
      ))}

      {stats.disclosures.length > 0 && (
        <>
          <div className="absolute bottom-3.5 top-[22px] border-l-[1.5px] border-dashed border-amber" style={{ left: at(stats.median) }} />
          <div className="absolute top-4 -ml-[4.5px] size-[9px] rounded-full border-2 border-panel2 bg-amber" style={{ left: at(stats.median) }} />
          <div
            className="absolute bottom-0 -translate-x-1/2 whitespace-nowrap font-mono text-[9.5px] font-semibold tracking-[0.03em] text-amber"
            style={{ left: at(stats.median) }}
          >
            MEDIAN {formatMoneyK(stats.median)}
          </div>
        </>
      )}

      {stats.disclosures.map((d) => {
        const value = measureValue(d, stats.measure);
        return (
          <button
            key={d.id}
            type="button"
            onClick={() => onSelect(d)}
            aria-label={`${d.name}, ${formatMoneyK(value)}, ${OUTCOME_LABEL[d.outcome]}`}
            title={`${d.name} · ${formatMoneyK(value)} · ${OUTCOME_LABEL[d.outcome]}`}
            className="group absolute top-1/2 -ml-[13px] -mt-[13px] grid size-[26px] place-items-center rounded-full"
            style={{ left: at(value) }}
          >
            <i className={cn("block size-3 rounded-full border-2 border-panel2 transition group-hover:scale-[1.35]", OUTCOME_FILL[d.outcome])} />
          </button>
        );
      })}
    </div>
  );
}
