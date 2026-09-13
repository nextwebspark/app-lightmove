import { SENIORITY_LEVELS, type ReportDiversity } from "../api/types";
import type { DiversityStats } from "../lib/diversityStats";

/**
 * Female and male headcount per level, bar length scaled to people rather than to share, so
 * Board's one woman in five is not drawn the same size as N-2's nine in twenty-two.
 */
export function GenderPyramid({ diversity, stats }: { diversity: ReportDiversity; stats: DiversityStats }) {
  const rows = SENIORITY_LEVELS.map((level) => {
    const female = diversity.femaleByLevel[level];
    const total = stats.levelTotals[level];
    return { level, female, male: total - female, total, pct: stats.femalePctByLevel[level] };
  });
  const max = Math.max(...rows.flatMap((r) => [r.female, r.male]), 1);
  const width = (n: number) => `${((n / max) * 100).toFixed(1)}%`;

  return (
    <div className="mt-3.5 flex flex-col gap-3" role="img" aria-label="Female and male headcount by seniority level">
      {rows.map((row) => (
        <div key={row.level}>
          <div className="mb-[5px] flex items-baseline justify-between">
            <span className="text-[11.5px] font-semibold">{row.level}</span>
            <span className="font-mono text-[10.5px] text-text3">
              {row.pct}% female · {row.total} executives
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="flex flex-1 items-center justify-end gap-2">
              <span className="font-mono text-[11px] font-semibold text-text2">{row.female}</span>
              <span className="h-4 rounded-s-[4px] bg-sky" style={{ width: width(row.female) }} />
            </span>
            <span className="h-[22px] w-px flex-none bg-text3" />
            <span className="flex flex-1 items-center gap-2">
              <span className="h-4 rounded-e-[4px] bg-line" style={{ width: width(row.male) }} />
              <span className="font-mono text-[11px] font-semibold text-text2">{row.male}</span>
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
