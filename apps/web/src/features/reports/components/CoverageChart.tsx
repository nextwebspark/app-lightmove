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
const WEEKS_OF_HEADROOM = 4;
const LABEL = "text-[10px] font-semibold tracking-[0.04em]";
/** Roughly how wide "FULL COVERAGE (42)" sets, and how tall the two-line projected label stands. */
const FULL_LABEL_WIDTH = 90;
const LABEL_ROW_HEIGHT = 16;
/** How far the label steps aside, and how much room "today" needs before the kickoff tick fits. */
const LABEL_DODGE = 26;
const KICKOFF_TICK_CLEARANCE = 40;

/**
 * Cumulative companies covered, the target as a vertical rule where the mandate has one, and the
 * projection as a dashed line running from today to full coverage where a pace exists. The band
 * between the target and the projected date is the slippage, washed in red so the size of the miss
 * reads before any number does.
 *
 * <p>Every forward mark is drawn only for a real projection. A mandate with nothing left to cover,
 * or too young to have a pace, gets its actuals and the target rule and no dashed line — a marker
 * sitting on today's date is a statement about the future that the rows do not make.
 */
export function CoverageChart({ progress, projection }: { progress: ReportProgress; projection: Projection }) {
  const cum = projection.coverage;
  const projectedWeek = projection.status === "projected" ? projection.projectedWeek : null;
  const xMax = Math.max(
    projection.nowWeek + WEEKS_OF_HEADROOM,
    projectedWeek !== null ? projectedWeek + 0.6 : 0,
    projection.targetWeek !== null ? projection.targetWeek + 1 : 0,
  );
  const x = (week: number) => PAD_LEFT + (week / xMax) * (W - PAD_LEFT - PAD_RIGHT);
  const y = (count: number) => PAD_TOP + (1 - count / Math.max(progress.targetCompanies, 1)) * PLOT_H;
  const point = (week: number, count: number) => `${x(week).toFixed(1)},${y(count).toFixed(1)}`;

  // The last bucket's coverage is as of today, which is part-way through its week — plotting it at
  // the week's start would put the series behind the "today" marker it is supposed to meet.
  const weekAt = (week: number) => (week === projection.lastWeek ? projection.nowWeek : week);
  const actualPoints = cum.map((count, week) => point(weekAt(week), count)).join(" ");
  const areaPoints = `${point(0, 0)} ${actualPoints} ${point(projection.nowWeek, 0)}`;
  const todayX = x(projection.nowWeek);
  const todayY = y(cum[projection.lastWeek]);
  const fullY = y(progress.targetCompanies);
  const targetX = projection.targetWeek === null ? null : x(projection.targetWeek);
  const projectedX = projectedWeek === null ? null : x(projectedWeek);
  // A universe of zero or one has fewer than three distinct ticks; drawn once each, they neither
  // overlap nor share a key.
  const ticks = [...new Set([0, Math.round(progress.targetCompanies / 2), progress.targetCompanies])];
  // One bucket is a reading, not a series: the line collapses to nothing and the area to a spike
  // under the marker. The marker alone says what is known.
  const hasSeries = cum.length > 1;
  // An end-anchored label on a projection left of centre runs off the canvas and is clipped.
  const labelAnchor = projectedX !== null && projectedX > W / 2 ? "end" : "start";
  // Two marks can land on this label: the today marker on a mandate covered in its first days, and
  // a start-anchored projected date, which sets from projectedX along the same row.
  const sitsOnFullLabel = (markX: number, markY: number) =>
    markX - PAD_LEFT < FULL_LABEL_WIDTH && Math.abs(markY - fullY) < LABEL_ROW_HEIGHT;
  const fullLabelClash =
    sitsOnFullLabel(todayX, todayY) || (projectedX !== null && labelAnchor === "start" && sitsOnFullLabel(projectedX, fullY));
  const fullLabelX = fullLabelClash ? PAD_LEFT + LABEL_DODGE : PAD_LEFT;
  // On a young mandate "today" sits on the kickoff tick and the two labels overprint each other.
  const showsKickoffTick = todayX - PAD_LEFT > KICKOFF_TICK_CLEARANCE;

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
        <text x={fullLabelX} y={fullY - 8} className={`fill-u-text3 ${LABEL}`}>
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

        {hasSeries && (
          <>
            <polygon points={areaPoints} className="fill-u-accent" opacity={0.13} />
            <polyline points={actualPoints} fill="none" className="stroke-u-accent" strokeWidth={1.75} strokeLinejoin="round" strokeLinecap="round" />
          </>
        )}
        {cum.slice(0, projection.lastWeek).map((count, week) => (
          <circle key={week} cx={x(week)} cy={y(count)} r={2.8} className="fill-u-accent" />
        ))}

        {projectedX !== null && projectedWeek !== null && projection.projectedDate && (
          <>
            <polyline
              points={`${point(projection.nowWeek, cum[projection.lastWeek])} ${point(projectedWeek, progress.targetCompanies)}`}
              fill="none"
              className="stroke-u-offlimits"
              strokeWidth={1.75}
              strokeDasharray="6 4"
              strokeLinecap="round"
            />
            <circle cx={projectedX} cy={fullY} r={4.5} className="fill-u-offlimits" />
            <text x={projectedX} y={fullY - 20} textAnchor={labelAnchor} className="fill-u-offlimits text-[10px] font-bold tracking-[0.04em]">
              PROJECTED
            </text>
            <text x={projectedX} y={fullY - 7} textAnchor={labelAnchor} className="fill-u-offlimits font-u-num text-[13.5px] font-bold">
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
        {showsKickoffTick && (
          <text x={PAD_LEFT} y={H - PAD_BOTTOM + 18} className="fill-u-text3 text-[10.5px]">
            {formatShortDate(progress.kickoff)}
          </text>
        )}
      </svg>
    </div>
  );
}
