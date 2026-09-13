import { cn } from "../../../lib/cn";
import type { CandidateStatus } from "../../candidates/api/types";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import type { Disclosure } from "../api/types";
import { type CompensationStats, measureValue } from "../lib/compensationStats";
import { formatCompactMoney } from "../lib/figures";

/**
 * The dot a status paints. Green is a yes, sky a conversation under way, grey a name and nothing
 * more, red a closed door — the same reading the grid's Status pill gives, on a mark rather than text.
 */
export const STATUS_FILL: Record<CandidateStatus, string> = {
  identified: "bg-text3",
  contacted: "bg-sky",
  engaged: "bg-sky",
  interested: "bg-green",
  notInterested: "bg-line",
  offLimits: "bg-red",
  outOfScope: "bg-amber",
};

/**
 * One axis, one dot per disclosure, our band shaded across it and the median marked. A strip rather
 * than a histogram because sixteen named points are worth more to a consultant than four buckets —
 * each dot opens the person who said the number.
 */
export function CompensationStrip({
  stats,
  currency,
  onSelect,
}: {
  stats: CompensationStats;
  currency: string;
  onSelect: (d: Disclosure) => void;
}) {
  const span = stats.axisHigh - stats.axisLow;
  const at = (amount: number) => `${(((amount - stats.axisLow) / span) * 100).toFixed(2)}%`;
  const band = stats.band;
  const bandWidth = band ? `${(((band.high - band.low) / span) * 100).toFixed(2)}%` : "0%";
  const unit = stats.measure === "package" ? "total package / yr" : "fixed pay / yr";

  return (
    <div className="relative mx-2.5 mt-4 h-[150px]" role="group" aria-label="Disclosed compensation">
      {band && (
        <>
          <div className="absolute bottom-11 top-[26px] rounded-md bg-sky opacity-[0.08]" style={{ left: at(band.low), width: bandWidth }} />
          <div className="absolute bottom-11 top-[26px] rounded-md border border-dashed border-sky opacity-50" style={{ left: at(band.low), width: bandWidth }} />
          <div
            className="absolute top-1.5 text-center font-mono text-[9.5px] font-semibold tracking-[0.08em] text-sky"
            style={{ left: at(band.low), width: bandWidth }}
          >
            OUR OFFERED BAND
          </div>
          {[band.low, band.high].map((edge) => (
            <div key={edge} className="absolute bottom-[26px] -translate-x-1/2 text-center" style={{ left: at(edge) }}>
              <div className="mx-auto h-1.5 w-px bg-sky opacity-60" />
              <div className="mt-0.5 whitespace-nowrap font-mono text-[10px] font-semibold text-sky">
                {formatCompactMoney(currency, edge)}
              </div>
            </div>
          ))}
        </>
      )}
      <div className="absolute right-0 top-1.5 font-mono text-[9.5px] text-text3">{unit}</div>
      <div className="absolute inset-x-0 top-1/2 h-px bg-line" />

      {stats.disclosures.length > 0 && (
        <>
          <div className="absolute bottom-3.5 top-[22px] border-l-[1.5px] border-dashed border-amber" style={{ left: at(stats.median) }} />
          <div className="absolute top-4 -ml-[4.5px] size-[9px] rounded-full border-2 border-panel2 bg-amber" style={{ left: at(stats.median) }} />
          <div
            className="absolute bottom-0 -translate-x-1/2 whitespace-nowrap font-mono text-[9.5px] font-semibold tracking-[0.03em] text-amber"
            style={{ left: at(stats.median) }}
          >
            MEDIAN {formatCompactMoney(currency, stats.median)}
          </div>
        </>
      )}

      {stats.disclosures.map((d) => {
        const value = measureValue(d, stats.measure);
        const label = candidateStatusStyle(d.status).label;
        return (
          <button
            key={d.id}
            type="button"
            onClick={() => onSelect(d)}
            aria-label={`${d.fullName}, ${formatCompactMoney(currency, value)}, ${label}`}
            title={`${d.fullName} · ${formatCompactMoney(currency, value)} · ${label}`}
            className="group absolute top-1/2 -ml-[13px] -mt-[13px] grid size-[26px] place-items-center rounded-full"
            style={{ left: at(value) }}
          >
            <i className={cn("block size-3 rounded-full border-2 border-panel2 transition group-hover:scale-[1.35]", STATUS_FILL[d.status])} />
          </button>
        );
      })}
    </div>
  );
}
