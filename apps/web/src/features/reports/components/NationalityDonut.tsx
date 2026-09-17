import { cn } from "../../../lib/cn";
import type { NationalityRow } from "../api/types";
import { percent } from "../lib/figures";

const SIZE = 200;
const RADIUS = 66;
const THICKNESS = 28;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const OTHER = "Other";

// The categorical ramp is assigned in order and stops at six: the palette has no seventh hue. A
// mandate can name nine groups, so the three past it step through the neutrals rather than sharing
// one grey and reading as a single arc; the tail row the API folds together stays the faintest.
const SERIES = [
  { stroke: "stroke-u-chart-1", swatch: "bg-u-chart-1" },
  { stroke: "stroke-u-chart-2", swatch: "bg-u-chart-2" },
  { stroke: "stroke-u-chart-3", swatch: "bg-u-chart-3" },
  { stroke: "stroke-u-chart-4", swatch: "bg-u-chart-4" },
  { stroke: "stroke-u-chart-5", swatch: "bg-u-chart-5" },
  { stroke: "stroke-u-chart-6", swatch: "bg-u-chart-6" },
  { stroke: "stroke-u-text2", swatch: "bg-u-text2" },
  { stroke: "stroke-u-border-strong", swatch: "bg-u-border-strong" },
];
const TAIL = { stroke: "stroke-u-text3", swatch: "bg-u-text3" };

/**
 * The nationality mix as one ring, with the largest group's share at its centre.
 *
 * <p>Built from `stroke-dasharray` and `stroke-dashoffset` on concentric circles rather than arc
 * paths — the same ring with none of the trigonometry, and it degrades to a legible circle if a
 * segment rounds to nothing. The list beside it names and counts every group, so the reading never
 * rests on colour alone.
 */
export function NationalityDonut({
  rows,
  total,
  largest,
}: {
  rows: NationalityRow[];
  total: number;
  largest: NationalityRow | null;
}) {
  let consumed = 0;
  const segments = rows.map((row, index) => {
    const dash = total === 0 ? 0 : (row.total / total) * CIRCUMFERENCE;
    const segment = { row, dash, offset: -consumed, series: row.nationality === OTHER ? TAIL : (SERIES[index] ?? TAIL) };
    consumed += dash;
    return segment;
  });

  return (
    <div className="mt-4 flex flex-wrap items-center gap-x-[30px] gap-y-5">
      <svg
        width={SIZE}
        height={SIZE}
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        className="flex-none"
        role="img"
        aria-label={`Nationality mix of ${total} executives`}
      >
        <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
          {segments.map(({ row, dash, offset, series }) => (
            <circle
              key={row.nationality}
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              strokeWidth={THICKNESS}
              className={series.stroke}
              strokeDasharray={`${dash} ${CIRCUMFERENCE - dash}`}
              strokeDashoffset={offset}
            />
          ))}
        </g>
        {largest && (
          <>
            <text x={SIZE / 2} y={SIZE / 2 - 3} textAnchor="middle" className="fill-u-text text-[25px] font-bold">
              {percent(largest.total, total)}%
            </text>
            <text
              x={SIZE / 2}
              y={SIZE / 2 + 16}
              textAnchor="middle"
              className="fill-u-text3 text-[9px] font-bold uppercase tracking-[0.05em]"
            >
              {largest.nationality}
            </text>
          </>
        )}
      </svg>
      <div className="flex min-w-[190px] flex-1 flex-col gap-2">
        {segments.map(({ row, series }) => (
          <div key={row.nationality} className="flex items-center gap-[9px] text-[12.5px]">
            <span aria-hidden className={cn("size-[11px] flex-none rounded-[3px]", series.swatch)} />
            <span className="flex-1 text-u-text2">{row.nationality}</span>
            <span className="font-u-num font-bold">{row.total}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
