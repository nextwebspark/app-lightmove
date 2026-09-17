import type { GenderLevel, GenderStats } from "../lib/diversityStats";

const W = 720;
const ROW_H = 58;
const PAD_TOP = 22;
const CENTER = W / 2;
const HALF_W = W / 2 - 80;
const BAR_H = 22;

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
  const height = PAD_TOP + stats.levels.length * ROW_H + 6;
  const scale = (count: number) => (stats.widest === 0 ? 0 : (count / stats.widest) * HALF_W);

  return (
    <div className="mt-2 overflow-x-auto">
      <svg viewBox={`0 0 ${W} ${height}`} role="img" aria-label="Female and male executives by seniority level" className="block h-auto w-full min-w-[520px]">
        <text x={CENTER - 10} y={14} textAnchor="end" className="fill-u-accent text-[10px] font-bold tracking-[0.06em]">
          FEMALE
        </text>
        <text x={CENTER + 10} y={14} className="fill-u-text3 text-[10px] font-bold tracking-[0.06em]">
          MALE
        </text>
        <line x1={CENTER} x2={CENTER} y1={20} y2={height - 4} className="stroke-u-border-strong" strokeWidth={1} strokeDasharray="2 3" />
        {stats.levels.map((level, index) => {
          const rowTop = PAD_TOP + index * ROW_H;
          const barY = rowTop + 16;
          const female = scale(level.female);
          const male = scale(level.male);
          return (
            <g key={level.level}>
              <text x={CENTER} y={rowTop + 10} textAnchor="middle" className="fill-u-text text-[11px] font-bold">
                {level.level}
              </text>
              <rect x={CENTER - female} y={barY} width={female} height={BAR_H} rx={4} className="fill-u-accent" />
              <rect x={CENTER} y={barY} width={male} height={BAR_H} rx={4} className="fill-u-border-strong" />
              <text x={CENTER - female - 8} y={barY + BAR_H / 2 + 4} textAnchor="end" className="fill-u-accent text-[11px] font-bold">
                {level.female}
              </text>
              <text x={CENTER + male + 8} y={barY + BAR_H / 2 + 4} className="fill-u-text2 text-[11px] font-bold">
                {level.male}
              </text>
              <text
                x={CENTER}
                y={barY + BAR_H + 14}
                textAnchor="middle"
                className={level.recorded === 0 ? "fill-u-text3 text-[9.5px] font-semibold" : "fill-u-accent text-[9.5px] font-semibold"}
              >
                {captionOf(level)}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function captionOf(level: GenderLevel): string {
  if (level.recorded === 0) return "none recorded";
  return `${level.femalePct}% female${level.other > 0 ? ` · ${level.other} other` : ""}`;
}
