import { cn } from "../../../lib/cn";

const SIZE = 200;
const RADIUS = 66;
const THICKNESS = 28;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

export interface DonutSegment {
  key: string;
  label: string;
  value: number;
  series: { fill: string; stroke: string };
}

/**
 * One ring of shares with a figure at its centre and a legend beneath, drawn the way
 * `NationalityDonut` is — dash-array arcs on concentric circles, so no segment needs trigonometry.
 */
export function CoverageDonut({
  segments,
  total,
  centre,
  centreLabel,
}: {
  segments: DonutSegment[];
  total: number;
  centre: string;
  centreLabel: string;
}) {
  let consumed = 0;
  const arcs = segments.map((segment) => {
    const dash = total === 0 ? 0 : (segment.value / total) * CIRCUMFERENCE;
    const arc = { ...segment, dash, offset: -consumed };
    consumed += dash;
    return arc;
  });

  return (
    <>
      <div className="mt-3 flex justify-center">
        <svg width={150} height={150} viewBox={`0 0 ${SIZE} ${SIZE}`} role="img" aria-label={`${centre} ${centreLabel.toLowerCase()}`}>
          <circle cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} fill="none" strokeWidth={THICKNESS} className="stroke-u-sunken" />
          <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
            {arcs.map((arc) => (
              <circle
                key={arc.key}
                cx={SIZE / 2}
                cy={SIZE / 2}
                r={RADIUS}
                fill="none"
                strokeWidth={THICKNESS}
                className={arc.series.stroke}
                strokeDasharray={`${arc.dash} ${CIRCUMFERENCE - arc.dash}`}
                strokeDashoffset={arc.offset}
              />
            ))}
          </g>
          <text x={SIZE / 2} y={SIZE / 2 - 3} textAnchor="middle" className="fill-u-text text-[25px] font-bold">
            {centre}
          </text>
          <text x={SIZE / 2} y={SIZE / 2 + 16} textAnchor="middle" className="fill-u-text3 text-[9px] font-bold uppercase tracking-[0.05em]">
            {centreLabel}
          </text>
        </svg>
      </div>
      <div className="mt-2.5 flex flex-col gap-[7px]">
        {arcs.map((arc) => (
          <div key={arc.key} className="flex items-center gap-2 text-[11.5px]">
            <span aria-hidden className={cn("size-[9px] flex-none rounded-[2px]", arc.series.fill)} />
            <span className="min-w-0 flex-1 truncate text-u-text2">{arc.label}</span>
            <span className="font-u-num font-bold">{arc.value}</span>
          </div>
        ))}
      </div>
    </>
  );
}
