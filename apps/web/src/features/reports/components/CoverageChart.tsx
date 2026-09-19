import type { ReportProgress } from "../api/types";
import { formatShortDate } from "../lib/figures";
import type { Projection } from "../lib/projection";
import { PulseRings } from "./PulseRings";

const W = 760;
const H = 280;
const PAD_LEFT = 40;
const PAD_RIGHT = 20;
const PAD_TOP = 30;
const PAD_BOTTOM = 36;
const PLOT_H = H - PAD_TOP - PAD_BOTTOM;
// The frame ends a little past the furthest mark on it rather than a fixed month past today: a
// mandate a fortnight old was drawing its whole line into the left fifth of an otherwise empty grid.
const HEADROOM_FRACTION = 0.12;
const MIN_HEADROOM_WEEKS = 0.6;
const LABEL = "text-[10px] font-semibold tracking-[0.04em]";
// Rendered widths of the three labels that share the top line, measured at their own type sizes: SVG
// text cannot reflow, so the only way to keep them apart is to reserve the room they take.
const FULL_COVERAGE_LABEL_W = 132;
const PROJECTED_LABEL_W = 92;
const TARGET_LABEL_HALF_W = 30;
const KICKOFF_LABEL_W = 46;

/**
 * Cumulative companies covered, the target as a vertical rule where the mandate has one, and the
 * projection as a dashed line running from today to full coverage where a pace exists. The band
 * between the target and the projected date is the slippage, washed in red so the size of the miss
 * reads before any number does.
 */
export function CoverageChart({ progress, projection }: { progress: ReportProgress; projection: Projection }) {
  const cum = projection.coverage;
  // A covered universe projects onto today itself, which would stack a completion marker on the
  // "today" one and on the full-coverage caption. There is nothing to project once nothing remains.
  const hasProjection =
    Number.isFinite(projection.projectedWeek) && projection.projectedDate !== null && projection.remaining > 0;
  const horizon = Math.max(projection.lastWeek, hasProjection ? projection.projectedWeek : 0, projection.targetWeek ?? 0);
  const xMax = horizon + Math.max(MIN_HEADROOM_WEEKS, horizon * HEADROOM_FRACTION);
  const x = (week: number) => PAD_LEFT + (week / xMax) * (W - PAD_LEFT - PAD_RIGHT);
  const y = (count: number) => PAD_TOP + (1 - count / Math.max(progress.targetCompanies, 1)) * PLOT_H;
  const point = (week: number, count: number) => `${x(week).toFixed(1)},${y(count).toFixed(1)}`;

  const actualPoints = cum.map((count, week) => point(week, count)).join(" ");
  const areaPoints = `${point(0, 0)} ${actualPoints} ${point(projection.lastWeek, 0)}`;
  const todayX = x(projection.lastWeek);
  const todayY = y(cum[projection.lastWeek]);
  const fullY = y(progress.targetCompanies);
  const targetX = projection.targetWeek === null ? null : x(projection.targetWeek);
  const projectedX = hasProjection ? x(projection.projectedWeek) : null;
  const projectedLabel = projectedX === null ? null : placeProjectedLabel(projectedX, progress.targetDate ? targetX : null, fullY);
  // A universe of zero or one has fewer than three distinct ticks; drawn once each, they neither
  // overlap nor share a key.
  const ticks = [...new Set([0, Math.round(progress.targetCompanies / 2), progress.targetCompanies])];

  return (
    <div className="overflow-x-auto">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`Companies covered per week against a universe of ${progress.targetCompanies}`}
        className="block h-auto w-full min-w-[560px] overflow-visible"
      >
        {[0, 0.25, 0.5, 0.75, 1].map((f) => (
          <line
            key={f}
            x1={PAD_LEFT}
            x2={W - PAD_RIGHT}
            y1={PAD_TOP + f * PLOT_H}
            y2={PAD_TOP + f * PLOT_H}
            className="stroke-u-grid-line"
            strokeWidth={1}
          />
        ))}
        {ticks.map((v) => (
          <text key={v} x={PAD_LEFT - 10} y={y(v) + 3} textAnchor="end" className="fill-u-text3 font-u-num text-[10.5px]">
            {v}
          </text>
        ))}

        {targetX !== null && projectedX !== null && projectedX > targetX && (
          <rect x={targetX} y={PAD_TOP} width={projectedX - targetX} height={PLOT_H} className="fill-u-offlimits" opacity={0.08} />
        )}
        <line x1={PAD_LEFT} x2={W - PAD_RIGHT} y1={fullY} y2={fullY} className="stroke-u-border-strong" strokeWidth={1} strokeDasharray="3 3" />
        <text x={PAD_LEFT} y={fullY - 8} className={`fill-u-text3 ${LABEL}`}>
          FULL COVERAGE ({progress.targetCompanies})
        </text>
        {targetX !== null && progress.targetDate && (
          <>
            <line x1={targetX} x2={targetX} y1={PAD_TOP} y2={H - PAD_BOTTOM} className="stroke-u-border-strong" strokeWidth={1} strokeDasharray="3 3" />
            <text x={targetX} y={PAD_TOP - 10} textAnchor="middle" className={`fill-u-text3 ${LABEL}`}>
              TARGET
            </text>
            <text x={targetX} y={PAD_TOP + 1} textAnchor="middle" className="fill-u-text3 text-[10px]">
              {formatShortDate(progress.targetDate)}
            </text>
          </>
        )}

        <polygon points={areaPoints} className="fill-u-accent" opacity={0.13} />
        <polyline points={actualPoints} fill="none" className="stroke-u-accent" strokeWidth={1.75} strokeLinejoin="round" strokeLinecap="round" />
        {cum.slice(0, projection.lastWeek).map((count, week) => (
          <circle key={week} cx={x(week)} cy={y(count)} r={2.8} className="fill-u-accent" />
        ))}

        {projectedX !== null && projectedLabel && projection.projectedDate && (
          <>
            <polyline
              points={`${point(projection.lastWeek, cum[projection.lastWeek])} ${point(projection.projectedWeek, progress.targetCompanies)}`}
              fill="none"
              className="stroke-u-offlimits"
              strokeWidth={1.75}
              strokeDasharray="6 4"
              strokeLinecap="round"
            />
            <circle cx={projectedX} cy={fullY} r={4.5} className="fill-u-offlimits" />
            <text
              x={projectedLabel.x}
              y={projectedLabel.y}
              textAnchor={projectedLabel.anchor}
              className="fill-u-offlimits text-[10px] font-bold tracking-[0.04em]"
            >
              PROJECTED
            </text>
            <text
              x={projectedLabel.x}
              y={projectedLabel.y + 13}
              textAnchor={projectedLabel.anchor}
              className="fill-u-offlimits font-u-num text-[13.5px] font-bold"
            >
              {formatShortDate(projection.projectedDate)}
            </text>
          </>
        )}

        <PulseRings cx={todayX} cy={todayY} r={6} strokeClass="stroke-u-accent" />
        <circle cx={todayX} cy={todayY} r={6.5} className="fill-u-accent stroke-u-surface" strokeWidth={2} />
        <text x={todayX} y={H - PAD_BOTTOM + 18} textAnchor="middle" className="fill-u-text text-[10.5px] font-bold">
          today
        </text>
        <text x={todayX} y={H - PAD_BOTTOM + 30} textAnchor="middle" className="fill-u-text3 text-[9.5px]">
          {formatShortDate(progress.asOf)}
        </text>
        {todayX - PAD_LEFT > KICKOFF_LABEL_W && (
          <text x={PAD_LEFT} y={H - PAD_BOTTOM + 18} className="fill-u-text3 text-[10.5px]">
            {formatShortDate(progress.kickoff)}
          </text>
        )}
      </svg>
    </div>
  );
}

/**
 * Where the completion marker's two lines can be written. Full coverage is always the top gridline,
 * so the coverage caption and the target column's own date already sit on that line: a projection
 * landing near either would be drawn straight through it, or off the frame's left edge.
 */
function placeProjectedLabel(projectedX: number, targetX: number | null, fullY: number) {
  let aboveX = projectedX;
  if (targetX !== null && projectedX > targetX - TARGET_LABEL_HALF_W && projectedX - PROJECTED_LABEL_W < targetX + TARGET_LABEL_HALF_W) {
    aboveX = targetX - TARGET_LABEL_HALF_W;
  }
  if (aboveX - PROJECTED_LABEL_W >= PAD_LEFT + FULL_COVERAGE_LABEL_W) {
    return { anchor: "end", x: aboveX, y: fullY - 20 } as const;
  }
  // Nothing free on the top line, so the label drops under it — and to the right of the point where
  // the frame allows, because the dashed projection climbs into that point from the left.
  const rightFits = projectedX + PROJECTED_LABEL_W <= W - PAD_RIGHT;
  return { anchor: rightFits ? "start" : "end", x: projectedX + (rightFits ? 8 : -8), y: fullY + 18 } as const;
}
