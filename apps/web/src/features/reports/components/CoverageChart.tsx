import type { ReportProgress } from "../api/types";
import { formatShortDate } from "../lib/figures";
import type { Projection } from "../lib/projection";

const W = 760;
const H = 280;
const PAD_LEFT = 40;
const PAD_RIGHT = 20;
const PAD_TOP = 30;
const PAD_BOTTOM = 36;
const PLOT_H = H - PAD_TOP - PAD_BOTTOM;

/**
 * Cumulative companies covered, the target as a vertical rule, and the projection as a dashed line
 * running from today to full coverage. The band between the target and the projected date is the
 * slippage, washed in red so the size of the miss reads before any number does.
 */
export function CoverageChart({ progress, projection }: { progress: ReportProgress; projection: Projection }) {
  const cum = progress.companiesCumulative;
  const xMax = Math.max(projection.projectedWeek + 0.6, projection.targetWeek + 5);
  const x = (week: number) => PAD_LEFT + (week / xMax) * (W - PAD_LEFT - PAD_RIGHT);
  const y = (count: number) => PAD_TOP + (1 - count / progress.targetCompanies) * PLOT_H;
  const point = (week: number, count: number) => `${x(week).toFixed(1)},${y(count).toFixed(1)}`;

  const actualPoints = cum.map((count, week) => point(week, count)).join(" ");
  const areaPoints = `${point(0, 0)} ${actualPoints} ${point(projection.lastWeek, 0)}`;
  const projectedPoints = `${point(projection.lastWeek, cum[projection.lastWeek])} ${point(projection.projectedWeek, progress.targetCompanies)}`;
  const targetX = x(projection.targetWeek);
  const projectedX = x(projection.projectedWeek);
  const todayX = x(projection.lastWeek);
  const todayY = y(cum[projection.lastWeek]);
  const half = progress.targetCompanies / 2;

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={`Companies covered per week against a target of ${progress.targetCompanies}, projected to reach full coverage on ${formatShortDate(projection.projectedDate)}`}
      className="mt-2 block h-auto w-full overflow-visible"
    >
      {[0, 0.25, 0.5, 0.75, 1].map((f) => (
        <line
          key={f}
          x1={PAD_LEFT}
          x2={W - PAD_RIGHT}
          y1={PAD_TOP + f * PLOT_H}
          y2={PAD_TOP + f * PLOT_H}
          className={f === 0 || f === 1 ? "stroke-line" : "stroke-line-soft"}
          strokeWidth={1}
        />
      ))}
      {[0, half, progress.targetCompanies].map((v) => (
        <text key={v} x={PAD_LEFT - 10} y={y(v) + 3} textAnchor="end" className="fill-text3 font-mono text-[10.5px]">
          {v}
        </text>
      ))}
      <text x={PAD_LEFT} y={PAD_TOP - 8} className="fill-text3 font-mono text-[10px] font-semibold tracking-[0.06em]">
        FULL COVERAGE · {progress.targetCompanies}
      </text>

      {projectedX > targetX && (
        <rect x={targetX} y={PAD_TOP} width={projectedX - targetX} height={PLOT_H} className="fill-red" opacity={0.06} />
      )}
      <line x1={targetX} x2={targetX} y1={PAD_TOP} y2={H - PAD_BOTTOM} className="stroke-text3" strokeWidth={1} strokeDasharray="3 3" />
      <text x={targetX} y={PAD_TOP - 10} textAnchor="middle" className="fill-text3 font-mono text-[10px] font-semibold tracking-[0.06em]">
        TARGET · {formatShortDate(progress.targetDate).toUpperCase()}
      </text>

      <polygon points={areaPoints} className="fill-sky" opacity={0.08} />
      <polyline points={actualPoints} fill="none" className="stroke-sky" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
      <polyline points={projectedPoints} fill="none" className="stroke-red" strokeWidth={2} strokeDasharray="6 4" strokeLinecap="round" />
      <circle cx={projectedX} cy={y(progress.targetCompanies)} r={4.5} className="fill-red stroke-panel2" strokeWidth={2} />
      <text x={projectedX} y={PAD_TOP - 10} textAnchor="end" className="fill-red font-mono text-[10px] font-semibold tracking-[0.06em]">
        PROJECTED · {formatShortDate(projection.projectedDate).toUpperCase()}
      </text>

      <circle cx={todayX} cy={todayY} r={6} className="animate-pulse-ring stroke-sky" fill="none" strokeWidth={2} />
      <circle cx={todayX} cy={todayY} r={6} className="fill-sky stroke-panel2" strokeWidth={2} />
      <text x={todayX} y={H - PAD_BOTTOM + 18} textAnchor="middle" className="fill-text text-[10.5px] font-semibold">
        today
      </text>
      <text x={todayX} y={H - PAD_BOTTOM + 30} textAnchor="middle" className="fill-text3 font-mono text-[9.5px]">
        {formatShortDate(progress.asOf)}
      </text>
      <text x={PAD_LEFT} y={H - PAD_BOTTOM + 18} className="fill-text3 font-mono text-[10.5px]">
        {formatShortDate(progress.kickoff)}
      </text>
    </svg>
  );
}
