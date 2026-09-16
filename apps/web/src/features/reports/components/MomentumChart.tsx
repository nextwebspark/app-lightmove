import { cn } from "../../../lib/cn";
import type { ReportProgress } from "../api/types";
import { formatShortDate } from "../lib/figures";
import { rollingAverage } from "../lib/projection";

/**
 * Executives identified per week — not cumulative, which is the point: a cumulative line keeps
 * climbing through a slowdown. A week below the run's average is drawn in amber.
 */
export function WeeklyMomentumChart({ progress, average }: { progress: ReportProgress; average: number }) {
  const max = Math.max(...progress.weekly.map((w) => w.identified), 1);
  return (
    <div className="mt-4 flex h-[150px] items-end gap-3.5 px-1" role="img" aria-label="Executives identified per week">
      {progress.weekly.map((week) => {
        const isBelow = week.identified < average;
        return (
          <div
            key={week.weekEnding}
            className="flex h-full flex-1 flex-col items-center justify-end gap-1.5"
            title={`${week.identified} executives in the week ending ${formatShortDate(week.weekEnding)}`}
          >
            <span className="font-u-num text-[12.5px] font-semibold">{week.identified}</span>
            <div className="flex h-full w-full items-end justify-center">
              <div
                className={cn("w-full max-w-6 rounded-t-[4px]", isBelow ? "bg-u-signal" : "bg-u-chart-1")}
                style={{ height: `${Math.max((week.identified / max) * 100, 4)}%` }}
              />
            </div>
            <span className="font-u-num text-[10px] text-u-text3">{formatShortDate(week.weekEnding)}</span>
          </div>
        );
      })}
    </div>
  );
}

const ROLLING_WINDOW = 7;
const W = 760;
const H = 150;

/**
 * The same weeks day by day, with a seven-day rolling mean over the bars. The bars alone mislead —
 * the weekend hole repeats every week by design — so the mean is the series that carries the trend.
 */
export function DailyMomentumChart({ progress }: { progress: ReportProgress }) {
  const series = progress.daily;
  const rolling = rollingAverage(series, ROLLING_WINDOW);
  const yMax = Math.max(...series, 1) + 1;
  const linePoints = rolling
    .map((v, i) => `${(((i + 0.5) / series.length) * W).toFixed(1)},${((1 - v / yMax) * H).toFixed(1)}`)
    .join(" ");

  return (
    <div className="mt-4">
      <div className="relative h-[150px]" role="img" aria-label="Executives identified per day with a seven-day rolling average">
        <div className="absolute inset-0 flex items-end gap-0.5">
          {series.map((n, i) => (
            <div
              key={i}
              className="flex-1 rounded-t-[2px] bg-u-sunken"
              style={{ height: `${Math.max((n / yMax) * 100, 1)}%` }}
              title={`${n} on day ${i + 1}`}
            />
          ))}
        </div>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="pointer-events-none absolute inset-0 size-full" aria-hidden>
          <polyline
            points={linePoints}
            fill="none"
            className="stroke-u-chart-1"
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      </div>
      <div className="mt-1.5 flex justify-between font-u-num text-[10px] text-u-text3">
        {progress.weekly.map((week) => (
          <span key={week.weekEnding}>{formatShortDate(week.weekEnding)}</span>
        ))}
      </div>
    </div>
  );
}
