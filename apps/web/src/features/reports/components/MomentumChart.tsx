import { cn } from "../../../lib/cn";
import type { ReportProgress } from "../api/types";
import { formatShortDate } from "../lib/figures";
import { rollingAverage } from "../lib/projection";

/**
 * Executives identified per week — not cumulative, which is the point: a cumulative line keeps
 * climbing through a slowdown. A week below the run's average is drawn in red, one at or above it
 * in green.
 */
export function WeeklyMomentumChart({ progress, average }: { progress: ReportProgress; average: number }) {
  const max = Math.max(...progress.weekly.map((w) => w.identified), 1);
  return (
    <div className="mt-5 flex h-40 items-end gap-3 px-1" role="img" aria-label="Executives identified per week">
      {progress.weekly.map((week) => {
        const isBelow = week.identified < average;
        return (
          <div
            key={week.weekEnding}
            className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-[7px]"
            title={`${week.identified} executives in the week ending ${formatShortDate(week.weekEnding)}`}
          >
            <span className={cn("font-u-num text-[13px] font-bold", isBelow ? "text-u-offlimits" : "text-u-direct")}>
              {week.identified}
            </span>
            <div className="flex h-full w-full items-end">
              <div
                className={cn("w-full rounded-b-[3px] rounded-t-[5px]", isBelow ? "bg-u-offlimits" : "bg-u-direct")}
                style={{ height: `${Math.max((week.identified / max) * 100, 5)}%` }}
              />
            </div>
            <span className="whitespace-nowrap font-u-num text-[10px] text-u-text3">{formatShortDate(week.weekEnding)}</span>
          </div>
        );
      })}
    </div>
  );
}

const ROLLING_WINDOW = 7;
const W = 760;
const H = 200;
const PAD_LEFT = 34;
const PAD_RIGHT = 16;
const PAD_TOP = 16;
const PAD_BOTTOM = 30;
const PLOT_W = W - PAD_LEFT - PAD_RIGHT;
const PLOT_H = H - PAD_TOP - PAD_BOTTOM;
const MAX_DATE_LABELS = 8;

/**
 * The same weeks day by day, with a seven-day rolling mean over the bars. The bars alone mislead —
 * the weekend hole repeats every week by design — so the mean is the series that carries the trend.
 */
export function DailyMomentumChart({ progress }: { progress: ReportProgress }) {
  const series = progress.daily;
  const rolling = rollingAverage(series, ROLLING_WINDOW);
  const yMax = Math.max(...series, 1) + 1;
  const x = (day: number) => PAD_LEFT + (series.length > 1 ? day / (series.length - 1) : 0.5) * PLOT_W;
  const y = (count: number) => PAD_TOP + (1 - count / yMax) * PLOT_H;
  const barWidth = (PLOT_W / series.length) * 0.62;
  const linePoints = rolling.map((v, day) => `${x(day).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  const labelEvery = Math.ceil(progress.weekly.length / MAX_DATE_LABELS);

  return (
    <div className="mt-2 overflow-x-auto">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label="Executives identified per day with a seven-day rolling average"
        className="block h-auto w-full min-w-[560px]"
      >
        {[0, 0.5, 1].map((f) => (
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
        {[0, yMax].map((v) => (
          <text key={v} x={PAD_LEFT - 8} y={y(v) + 3} textAnchor="end" className="fill-u-text3 font-u-num text-[9.5px]">
            {v}
          </text>
        ))}
        {series.map((count, day) => (
          <rect
            key={day}
            x={x(day) - barWidth / 2}
            y={y(count)}
            width={barWidth}
            height={Math.max(H - PAD_BOTTOM - y(count), 1)}
            rx={1.2}
            className="fill-u-sunken"
          >
            <title>{`${count} on day ${day + 1}`}</title>
          </rect>
        ))}
        <polyline points={linePoints} fill="none" className="stroke-u-accent" strokeWidth={1.75} strokeLinejoin="round" strokeLinecap="round" />
        {progress.weekly.map((week, index) =>
          index % labelEvery === 0 ? (
            <text
              key={week.weekEnding}
              x={x(Math.min((index + 1) * 7 - 1, series.length - 1))}
              y={H - PAD_BOTTOM + 16}
              textAnchor="middle"
              className="fill-u-text3 font-u-num text-[9.5px]"
            >
              {formatShortDate(week.weekEnding)}
            </text>
          ) : null,
        )}
      </svg>
    </div>
  );
}
