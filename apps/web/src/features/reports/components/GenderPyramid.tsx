import type { GenderStats } from "../lib/diversityStats";

/**
 * Gender through the seniority ladder, drawn as a population pyramid.
 *
 * <p>Bar <b>length</b> is scaled to headcount rather than to share, so a level of five is not drawn
 * the width of a level of fifty: the whole point of the chart is that "20% female" means one person
 * at Board and nine at N-2, and a percentage bar hides exactly that.
 *
 * <p>A third gender is counted in the caption, never given a third wing — two people in a column
 * would be drawn as a wing the eye reads as a third of the level.
 */
export function GenderPyramid({ stats }: { stats: GenderStats }) {
  return (
    <div className="mt-3.5 flex flex-col gap-3">
      {stats.levels.map((level) => (
        <div key={level.level}>
          <div className="mb-1.5 flex items-baseline justify-between gap-3">
            <span className="text-[11.5px] font-semibold">{level.level}</span>
            <span className="font-mono text-[10.5px] text-text3">
              {level.recorded === 0
                ? "none recorded"
                : `${level.femalePct}% female · ${level.recorded} recorded${level.other > 0 ? ` · ${level.other} other` : ""}`}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="flex flex-1 items-center justify-end gap-2">
              <span className="font-mono text-[11px] font-semibold text-text2">{level.female}</span>
              <span
                className="h-4 rounded-l-[4px] bg-sky"
                style={{ width: `${(level.female / stats.widest) * 100}%` }}
              />
            </span>
            <span aria-hidden className="h-[22px] w-px flex-none bg-text3" />
            <span className="flex flex-1 items-center gap-2">
              <span
                className="h-4 rounded-r-[4px] bg-line"
                style={{ width: `${(level.male / stats.widest) * 100}%` }}
              />
              <span className="font-mono text-[11px] font-semibold text-text2">{level.male}</span>
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
