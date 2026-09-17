import type { KeyboardEvent } from "react";
import { cn } from "../../../lib/cn";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import type { Disclosure } from "../api/types";
import { type CompensationStats, measureValue } from "../lib/compensationStats";
import { formatCompactMoney } from "../lib/figures";
import { STATUS_TONES, statusTone } from "../lib/statusTone";
import { PulseRings } from "./PulseRings";

const W = 760;
const H = 172;
const PAD_X = 20;
const PAD_TOP = 34;
const PAD_BOTTOM = 48;
const PLOT_BOTTOM = H - PAD_BOTTOM;
const BAND_TOP = PAD_TOP - 10;
const MID_Y = PAD_TOP + (H - PAD_TOP - PAD_BOTTOM) / 2;
const EDGE_LABEL_Y = PLOT_BOTTOM + 18;
// The median takes its own row under the band edges, so the two can never collide.
const MEDIAN_LABEL_Y = PLOT_BOTTOM + 38;

/**
 * One axis, one dot per disclosure, our band shaded across it and the median marked. A strip rather
 * than a histogram because sixteen named points are worth more to a consultant than four buckets —
 * each dot opens the person who said the number. No numeric axis: the band's two edges and the
 * median are the only figures that matter, so they are the only ones labelled.
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
  const x = (amount: number) => PAD_X + ((amount - stats.axisLow) / span) * (W - PAD_X * 2);
  const band = stats.band;
  const unit = stats.measure === "package" ? "total package / yr" : "fixed pay / yr";
  const medianX = x(stats.median);

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${W} ${H}`} role="group" aria-label="Disclosed compensation" className="block h-auto w-full min-w-[560px] overflow-visible">
        {band && (
          <>
            <rect x={x(band.low)} y={BAND_TOP} width={x(band.high) - x(band.low)} height={PLOT_BOTTOM - BAND_TOP} className="fill-u-accent" opacity={0.1} />
            <rect
              x={x(band.low)}
              y={BAND_TOP}
              width={x(band.high) - x(band.low)}
              height={PLOT_BOTTOM - BAND_TOP}
              fill="none"
              className="stroke-u-accent"
              strokeWidth={1}
              strokeDasharray="4 3"
              opacity={0.5}
            />
            <text x={(x(band.low) + x(band.high)) / 2} y={PAD_TOP - 16} textAnchor="middle" className="fill-u-accent text-[10px] font-bold tracking-[0.04em]">
              OUR OFFERED BAND
            </text>
            {[band.low, band.high].map((edge) => (
              <g key={edge}>
                <line x1={x(edge)} x2={x(edge)} y1={PLOT_BOTTOM} y2={PLOT_BOTTOM + 6} className="stroke-u-accent" strokeWidth={1} strokeDasharray="2 2" opacity={0.7} />
                <text x={x(edge)} y={EDGE_LABEL_Y} textAnchor="middle" className="fill-u-accent font-u-num text-[10px] font-bold">
                  {formatCompactMoney(currency, edge)}
                </text>
              </g>
            ))}
          </>
        )}

        {stats.disclosures.length > 0 && (
          <>
            <line x1={medianX} x2={medianX} y1={PAD_TOP - 4} y2={MEDIAN_LABEL_Y - 8} className="stroke-u-offlimits" strokeWidth={1.4} strokeDasharray="3 3" />
            <PulseRings cx={medianX} cy={PAD_TOP - 4} r={5} strokeClass="stroke-u-offlimits" />
            <circle cx={medianX} cy={PAD_TOP - 4} r={5} className="fill-u-offlimits stroke-u-surface" strokeWidth={2} />
            <text x={medianX} y={MEDIAN_LABEL_Y} textAnchor="middle" className="fill-u-offlimits text-[9.5px] font-bold tracking-[0.03em]">
              MEDIAN {formatCompactMoney(currency, stats.median)}
            </text>
          </>
        )}

        <line x1={PAD_X} x2={W - PAD_X} y1={MID_Y} y2={MID_Y} className="stroke-u-border-strong" strokeWidth={1} />
        {stats.disclosures.map((d) => {
          const value = measureValue(d, stats.measure);
          const summary = `${d.fullName} · ${formatCompactMoney(currency, value)} · ${candidateStatusStyle(d.status).label}`;
          const handleKey = (event: KeyboardEvent<SVGCircleElement>) => {
            if (event.key !== "Enter" && event.key !== " ") return;
            event.preventDefault();
            onSelect(d);
          };
          return (
            <circle
              key={d.id}
              cx={x(value)}
              cy={MID_Y}
              r={7}
              role="button"
              tabIndex={0}
              aria-label={summary}
              onClick={() => onSelect(d)}
              onKeyDown={handleKey}
              strokeWidth={2}
              className={cn(
                "origin-center cursor-pointer stroke-u-surface outline-none transition-transform [transform-box:fill-box] hover:scale-[1.3] focus-visible:scale-[1.3] focus-visible:stroke-u-accent",
                STATUS_TONES[statusTone(d.status)].fill,
              )}
            >
              <title>{summary}</title>
            </circle>
          );
        })}
        <text x={W - PAD_X} y={PAD_TOP - 16} textAnchor="end" className="fill-u-text3 font-u-num text-[9.5px]">
          {unit}
        </text>
      </svg>
    </div>
  );
}
