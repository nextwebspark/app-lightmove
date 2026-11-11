import type { RankedCandidate, RankingCriterion } from "../api/types";
import { criteriaOf, PANELS, scoreOf } from "../lib/ranking";

const COLUMNS = "grid-cols-[minmax(0,110px)_minmax(0,1fr)_64px] @min-[640px]:grid-cols-[minmax(0,200px)_minmax(0,1fr)_80px]";
const TICKS = [0, 2, 4, 6, 8, 10];
const PANEL_NAME = { technical: "TECHNICAL", behavioural: "BEHAVIOURAL" } as const;

/**
 * Two candidates' competency scores on one 0–10 scale, A over B, grouped by panel. The weighting moves neither bar:
 * it changes how much a competency counts, never what was assessed. An unscored competency draws no bar and a dash.
 */
export function CompetencyBars({
  a,
  b,
  criteria,
}: {
  a: RankedCandidate;
  b: RankedCandidate;
  criteria: readonly RankingCriterion[];
}) {
  return (
    <div className="flex flex-col">
      <div className={`grid items-center gap-4 pb-2.5 text-eyebrow tracking-[0.04em] text-u-text2 ${COLUMNS}`}>
        <span className="uppercase">Shared performance scale</span>
        <div className="flex justify-between font-u-num" aria-hidden="true">
          {TICKS.map((tick) => (
            <span key={tick}>{tick}</span>
          ))}
        </div>
        <span>SCORE</span>
      </div>
      {PANELS.map((panel) => {
        const panelCriteria = criteriaOf(criteria, panel);
        if (panelCriteria.length === 0) return null;
        return (
          <div key={panel}>
            <div className="mt-1.5 rounded bg-u-raised px-2 py-[7px] text-meta font-semibold tracking-[0.06em] text-u-text2">
              {PANEL_NAME[panel]}
            </div>
            {panelCriteria.map((criterion) => (
              <BarRow key={criterion.key} criterion={criterion} a={scoreOf(a, criterion.key)} b={scoreOf(b, criterion.key)} />
            ))}
          </div>
        );
      })}
    </div>
  );
}

function BarRow({ criterion, a, b }: { criterion: RankingCriterion; a: number | null; b: number | null }) {
  return (
    <div className={`grid items-center gap-4 py-4 ${COLUMNS}`}>
      <span className="text-body">{criterion.name}</span>
      <div className="relative flex flex-col gap-2 py-0.5">
        <Bar score={a} tone="bg-u-inferred" />
        <Bar score={b} tone="bg-u-signal" />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -inset-y-1.5 inset-x-0 bg-[linear-gradient(to_right,var(--color-u-border-strong)_1px,transparent_1px)] bg-[length:20%_100%] bg-repeat-x"
        />
      </div>
      <div className="flex flex-col gap-1 border-l border-u-border-strong pl-3.5 font-u-num text-note">
        <span className="text-u-inferred">{a === null ? "—" : `${a.toFixed(1)} / 10`}</span>
        <span className="text-u-signal">{b === null ? "—" : `${b.toFixed(1)} / 10`}</span>
      </div>
    </div>
  );
}

function Bar({ score, tone }: { score: number | null; tone: string }) {
  return (
    <div className="h-3">
      {score !== null && <div className={`h-3 rounded-sm ${tone}`} style={{ width: `${score * 10}%` }} />}
    </div>
  );
}
