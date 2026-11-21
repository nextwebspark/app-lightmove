import { cn } from "../../../lib/cn";
import type { ChartResult, ChartType } from "../api/types";

const HUES = [
  "var(--color-u-chart-1)",
  "var(--color-u-chart-2)",
  "var(--color-u-chart-3)",
  "var(--color-u-chart-4)",
  "var(--color-u-chart-5)",
  "var(--color-u-chart-6)",
  "var(--color-u-text3)",
];

const hue = (index: number) => HUES[index % HUES.length];

/** The label the server folds a breakdown's tail into (`ChartShaping.OTHER`). */
const OTHER_LABEL = "Other";

/** Labels repeat — two positions with one title, a week-ending date a year apart — so a key needs the place too. */
const keyOf = (label: string, index: number) => `${index}:${label}`;

const plain = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 1 });
const compact = new Intl.NumberFormat("en-GB", { notation: "compact", maximumFractionDigits: 1 });

/** A figure as a chart states it: compact past ten thousand, so a package reads "1.2M", not seven digits. */
export function formatFigure(value: number, unit = ""): string {
  const figure = Math.abs(value) >= 10_000 ? compact.format(value) : plain.format(value);
  return unit ? `${figure} ${unit}` : figure;
}

/** One library answer drawn as `chart`. Hand-drawn like the report's charts, in the same tokens. */
export function DashboardChart({ result, chart }: { result: ChartResult; chart: ChartType }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {chart === "kpi" && <KpiFigure result={result} />}
      {chart === "bar" && <BarRows result={result} />}
      {chart === "column" && <Columns result={result} />}
      {(chart === "pie" || chart === "donut") && <Pie result={result} donut={chart === "donut"} />}
      {(chart === "line" || chart === "area") && <LineChart result={result} area={chart === "area"} />}
      {chart === "stacked" && <StackedRows result={result} />}
      {chart === "table" && <ResultTable result={result} />}
      {result.note && <p className="mt-3 text-meta italic text-u-text3">{result.note}</p>}
      {result.truncated && (
        <p className="mt-2 text-meta text-u-text3">
          {/* A median or a rate has no Other to fold the rest into: its tail is left off. */}
          {result.labels.includes(OTHER_LABEL)
            ? "Showing the largest groups; the rest are folded into Other."
            : "Showing the largest groups only."}
        </p>
      )}
    </div>
  );
}

function KpiFigure({ result }: { result: ChartResult }) {
  const kpi = result.kpi;
  if (!kpi) return null;
  return (
    <div className="mt-2.5">
      <div className={cn("font-u-num text-figure font-semibold", kpi.alarm ? "text-u-signal" : "text-u-accent")}>
        {kpi.value}
        {kpi.unit && <span className="ml-1 text-lead font-medium opacity-60">{kpi.unit}</span>}
      </div>
      <div className="mt-1.5 text-note text-u-text2">{kpi.sub}</div>
    </div>
  );
}

function Empty() {
  return <p className="mt-4 text-note text-u-text3">Nothing to show yet.</p>;
}

function BarRows({ result }: { result: ChartResult }) {
  if (result.labels.length === 0) return <Empty />;
  const max = Math.max(...result.values, 1);
  return (
    <ul className="mt-4 flex flex-col gap-2.5">
      {result.labels.map((label, index) => (
        <li
          key={keyOf(label, index)}
          title={`${label}: ${formatFigure(result.values[index], result.unit)}`}
          className="grid grid-cols-[minmax(80px,34%)_minmax(0,1fr)_auto] items-center gap-2.5 text-note"
        >
          <span className="truncate text-u-text2">{label}</span>
          <span className="h-2 rounded-full bg-u-raised">
            <span
              className={cn("block h-full rounded-full", index === 0 ? "bg-u-accent" : "bg-u-accent/45")}
              style={{ width: `${Math.max((result.values[index] / max) * 100, 2)}%` }}
            />
          </span>
          <span className="text-right font-u-num font-semibold">{formatFigure(result.values[index], result.unit)}</span>
        </li>
      ))}
    </ul>
  );
}

function Columns({ result }: { result: ChartResult }) {
  if (result.labels.length === 0) return <Empty />;
  const max = Math.max(...result.values, 1);
  return (
    <div role="img" aria-label={`${result.measure || "Count"} by ${result.dimension.toLowerCase()}`}
         className="mt-4 flex min-h-28 flex-[1_1_10rem] items-end gap-2">
      {result.labels.map((label, index) => (
        <div
          key={keyOf(label, index)}
          title={`${label}: ${formatFigure(result.values[index], result.unit)}`}
          className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1.5"
        >
          <span className="font-u-num text-meta font-bold">{formatFigure(result.values[index])}</span>
          <span className="flex h-full w-full items-end">
            <span
              className="block w-full rounded-t-md rounded-b-sm bg-u-accent"
              style={{ height: `${Math.max((result.values[index] / max) * 100, 3)}%` }}
            />
          </span>
          <span className="max-w-full truncate font-u-num text-eyebrow text-u-text3">{label}</span>
        </div>
      ))}
    </div>
  );
}

function Pie({ result, donut }: { result: ChartResult; donut: boolean }) {
  const total = result.values.reduce((sum, value) => sum + value, 0);
  if (total === 0) return <Empty />;
  const radius = donut ? 58 : 34;
  const stroke = donut ? 22 : 68;
  const circumference = 2 * Math.PI * radius;
  let used = 0;
  return (
    <div className="mt-3.5 flex flex-wrap items-center gap-4">
      <div className="relative size-[150px] flex-none">
        <svg viewBox="0 0 160 160" className="size-full -rotate-90" aria-hidden="true">
          {result.values.map((value, index) => {
            const length = (value / total) * circumference;
            const offset = -used;
            used += length;
            return (
              <circle
                key={keyOf(result.labels[index], index)}
                cx="80"
                cy="80"
                r={radius}
                fill="none"
                strokeWidth={stroke}
                strokeDasharray={`${length} ${circumference - length}`}
                strokeDashoffset={offset}
                style={{ stroke: hue(index) }}
              />
            );
          })}
        </svg>
        {donut && (
          <div className="absolute inset-0 grid place-items-center text-center">
            <div>
              <div className="font-u-num text-subhead font-bold">{formatFigure(total)}</div>
              <div className="text-meta text-u-text3">{(result.measure || "total").toLowerCase()}</div>
            </div>
          </div>
        )}
      </div>
      <ul className="flex min-w-[140px] flex-1 flex-col gap-1.5">
        {result.labels.map((label, index) => (
          <li key={keyOf(label, index)} className="flex items-center gap-2 text-note">
            <i className="size-2.5 flex-none rounded-sm" style={{ background: hue(index) }} />
            <span className="min-w-0 flex-1 truncate text-u-text2">{label}</span>
            <span className="font-u-num font-semibold">{formatFigure(result.values[index])}</span>
            <span className="w-9 text-right font-u-num text-meta text-u-text3">
              {Math.round((result.values[index] / total) * 100)}%
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

const WIDTH = 560;
const LEFT = 34;
const BASE = 176;
const HEIGHT = 150;

function LineChart({ result, area }: { result: ChartResult; area: boolean }) {
  if (result.values.length === 0) return <Empty />;
  const max = Math.max(...result.values, 1);
  const count = result.values.length;
  const x = (index: number) => LEFT + (count > 1 ? (index * (WIDTH - LEFT - 10)) / (count - 1) : (WIDTH - LEFT) / 2);
  const y = (value: number) => BASE - (value / max) * HEIGHT;
  const points = result.values.map((value, index) => `${x(index).toFixed(1)},${y(value).toFixed(1)}`).join(" ");
  const grid = [0, 0.5, 1];
  return (
    <div className="relative mt-3">
      <svg viewBox="0 0 560 200" className="block h-auto w-full overflow-visible" role="img"
           aria-label={`${result.measure || "Count"} per ${result.dimension.toLowerCase()}`}>
        {grid.map((step) => (
          <line key={step} x1={LEFT} x2={550} y1={BASE - step * HEIGHT} y2={BASE - step * HEIGHT}
                className="stroke-u-grid-line" strokeWidth={1} />
        ))}
        {area && (
          <polygon points={`${LEFT},${BASE} ${points} ${x(count - 1).toFixed(1)},${BASE}`}
                   className="fill-u-accent opacity-15" />
        )}
        <polyline points={points} fill="none" strokeLinejoin="round" strokeLinecap="round"
                  className="stroke-u-accent" strokeWidth={2} />
        {result.values.map((value, index) => (
          <circle key={keyOf(result.labels[index], index)} cx={x(index)} cy={y(value)} r={3.2}
                  className="fill-u-accent stroke-u-surface" strokeWidth={1.5}>
            <title>{`${result.labels[index]}: ${formatFigure(value)}`}</title>
          </circle>
        ))}
      </svg>
      {grid.map((step) => (
        <span key={step} className="absolute -translate-y-1/2 font-u-num text-eyebrow text-u-text3"
              style={{ right: `${100 - ((LEFT - 6) / WIDTH) * 100}%`, top: `${((BASE - step * HEIGHT) / 200) * 100}%` }}>
          {formatFigure(max * step)}
        </span>
      ))}
      {result.labels.map((label, index) =>
        count <= 10 || index % 2 === 0 ? (
          <span key={keyOf(label, index)} className="absolute -translate-x-1/2 whitespace-nowrap font-u-num text-eyebrow text-u-text3"
                style={{ left: `${(x(index) / WIDTH) * 100}%`, top: `${((BASE + 8) / 200) * 100}%` }}>
            {label}
          </span>
        ) : null,
      )}
    </div>
  );
}

function StackedRows({ result }: { result: ChartResult }) {
  if (result.labels.length === 0) return <Empty />;
  const totals = result.labels.map((_, row) => result.series.reduce((sum, series) => sum + series.values[row], 0));
  const max = Math.max(...totals, 1);
  return (
    <div>
      <ul className="mt-3 flex flex-wrap gap-x-3.5 gap-y-1.5 text-meta text-u-text2">
        {result.series.map((series, index) => (
          <li key={keyOf(series.name, index)} className="inline-flex items-center gap-1.5">
            <i className="size-2.5 rounded-sm" style={{ background: hue(index) }} />
            {series.name}
          </li>
        ))}
      </ul>
      <ul className="mt-3 flex flex-col gap-3">
        {result.labels.map((label, row) => {
          const hidden = result.suppressed[row];
          return (
            <li key={keyOf(label, row)}>
              <div className="flex justify-between gap-2.5 text-note">
                <span className="font-semibold">{label}</span>
                <span className="text-u-text3">
                  {hidden ? "Fewer than 3 recorded" : `${formatFigure(totals[row])}${result.percent ? " recorded" : ""}`}
                </span>
              </div>
              {hidden ? (
                <div className="mt-1.5 h-2.5 rounded-full bg-[repeating-linear-gradient(135deg,var(--color-u-raised)_0_6px,transparent_6px_10px)]" />
              ) : (
                <div className="mt-1.5 h-2.5 rounded-full bg-u-raised">
                  <div className="flex h-full overflow-hidden rounded-full"
                       style={{ width: result.percent ? "100%" : `${(totals[row] / max) * 100}%` }}>
                    {result.series.map((series, index) => (
                      <div key={keyOf(series.name, index)} title={`${series.name}: ${formatFigure(series.values[row])}`}
                           className="h-full"
                           style={{
                             background: hue(index),
                             width: `${totals[row] ? (series.values[row] / totals[row]) * 100 : 0}%`,
                           }} />
                    ))}
                  </div>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function ResultTable({ result }: { result: ChartResult }) {
  let columns: string[];
  let rows: string[][];
  if (result.shape === "RANKED") {
    columns = result.columns;
    rows = result.rows;
  } else if (result.shape === "MATRIX") {
    columns = [result.dimension, ...result.series.map((series) => series.name), "Total"];
    rows = result.labels.map((label, row) =>
      result.suppressed[row]
        ? [label, ...result.series.map(() => "—"), "Fewer than 3"]
        : [
            label,
            ...result.series.map((series) => formatFigure(series.values[row])),
            formatFigure(result.series.reduce((sum, series) => sum + series.values[row], 0)),
          ],
    );
  } else if (result.shape === "KPI" && result.kpi) {
    columns = ["Figure", "Value"];
    rows = [[result.kpi.sub, `${result.kpi.value}${result.kpi.unit}`]];
  } else {
    const total = result.values.reduce((sum, value) => sum + value, 0);
    const shares = result.shape === "CATEGORICAL";
    columns = [result.dimension, result.measure || "Count", ...(shares && total ? ["Share"] : [])];
    rows = result.labels.map((label, index) => [
      label,
      formatFigure(result.values[index], result.unit),
      ...(shares && total ? [`${Math.round((result.values[index] / total) * 100)}%`] : []),
    ]);
  }
  if (rows.length === 0) return <Empty />;
  return (
    <div className="mt-3 overflow-x-auto">
      <table className="w-full border-collapse text-note">
        <thead>
          <tr>
            {columns.map((column, index) => (
              <th key={keyOf(column, index)} scope="col"
                  className={cn("type-micro-label whitespace-nowrap border-b border-u-border-strong px-2 py-1.5",
                    index === 0 ? "text-left" : "text-right")}>
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, index) => (
                <td key={index}
                    className={cn("border-b border-u-border px-2 py-2 tabular-nums",
                      index === 0 ? "text-left font-semibold" : "text-right")}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
