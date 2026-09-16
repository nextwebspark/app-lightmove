import type { NationalityRow } from "../api/types";
import { percent } from "../lib/figures";

const SIZE = 200;
const RADIUS = 66;
const THICKNESS = 28;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * The nationality mix as one ring, with the largest group's share at its centre.
 *
 * <p>Built from `stroke-dasharray` and `stroke-dashoffset` on concentric circles rather than arc
 * paths — the same ring with none of the trigonometry, and it degrades to a legible circle if a
 * segment rounds to nothing.
 *
 * <p><b>Colour carries the GCC distinction</b>, which is the question this chapter exists to answer:
 * Gulf nationalities take the accent, everything else the neutral surface. Not the warning colour —
 * a nationality is a fact about the pool, never a problem with it. The legend still names and counts
 * every group, so the reading never rests on colour alone.
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
  const segments = rows.map((row) => {
    const dash = total === 0 ? 0 : (row.total / total) * CIRCUMFERENCE;
    const segment = { row, dash, offset: -consumed };
    consumed += dash;
    return segment;
  });

  return (
    <div className="mt-4 flex flex-wrap items-center gap-x-8 gap-y-5">
      <svg
        width={SIZE}
        height={SIZE}
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        className="flex-none"
        role="img"
        aria-label={`Nationality mix of ${total} executives`}
      >
        <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
          {segments.map(({ row, dash, offset }) => (
            <circle
              key={row.nationality}
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              strokeWidth={THICKNESS}
              className={row.gcc ? "stroke-u-accent" : "stroke-u-sunken"}
              strokeDasharray={`${dash} ${CIRCUMFERENCE - dash}`}
              strokeDashoffset={offset}
            />
          ))}
        </g>
        {largest && (
          <>
            <text
              x={SIZE / 2}
              y={SIZE / 2 - 3}
              textAnchor="middle"
              className="fill-u-text text-[25px] font-extrabold"
            >
              {percent(largest.total, total)}%
            </text>
            <text
              x={SIZE / 2}
              y={SIZE / 2 + 16}
              textAnchor="middle"
              className="fill-u-text3 text-[9px] font-semibold uppercase tracking-[0.05em]"
            >
              {largest.nationality}
            </text>
          </>
        )}
      </svg>
      <div className="flex min-w-[190px] flex-1 flex-col gap-2">
        {rows.map((row) => (
          <div key={row.nationality} className="flex items-center gap-2.5 text-[12.5px]">
            <span
              aria-hidden
              className={`size-[11px] flex-none rounded-[3px] ${row.gcc ? "bg-u-accent" : "bg-u-sunken"}`}
            />
            <span className="flex-1 text-u-text2">{row.nationality}</span>
            <span className="font-u-num font-medium">{row.total}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
