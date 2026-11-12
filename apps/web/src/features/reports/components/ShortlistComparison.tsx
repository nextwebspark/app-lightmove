import { Avatar } from "../../../components/ui";
import { formatInstantDate } from "../../../lib/format";
import type { RankedCandidate, RankingCriterion } from "../api/types";
import { type RankedRow, rankStandings, type Standing, type Weighting } from "../lib/ranking";
import {
  differentiatorsOf,
  evidenceContextOf,
  oneDecimal,
  splitLabel,
  validateOf,
  whyOrderOf,
} from "../lib/rankingWording";
import { CompetencyBars } from "./CompetencyBars";
import { ReportCard } from "./ReportCard";

const EYEBROW = "text-eyebrow font-semibold uppercase tracking-[0.06em]";

/** The two selected candidates side by side: competency strengths first, then why the order is what it is. */
export function ShortlistComparison({
  pair,
  criteria,
  baseline,
  scenario,
  atBaseline,
  sensitivity,
  onOpenAssessment,
}: {
  /** The two rows in the scenario's order. */
  pair: readonly [RankedRow, RankedRow];
  criteria: readonly RankingCriterion[];
  baseline: Weighting;
  scenario: Weighting;
  atBaseline: boolean;
  sensitivity: string | null;
  onOpenAssessment: (candidateId: string) => void;
}) {
  const [a, b] = pair;
  const candidates = [a.candidate, b.candidate];
  const pairAt = (weighting: Weighting) => {
    const standings = rankStandings(candidates, criteria, weighting);
    const find = (candidate: RankedCandidate) =>
      standings.find((standing) => standing.candidate.candidateId === candidate.candidateId)!;
    return [find(a.candidate), find(b.candidate)] as [Standing, Standing];
  };
  const why = whyOrderOf(pairAt(scenario), pairAt(baseline), criteria, baseline, scenario, atBaseline);
  const baselineLeader = a.baselineTotal >= b.baselineTotal ? a.candidate : b.candidate;

  return (
    <ReportCard
      title="Shortlist comparison"
      caption="Competency strengths first. Priorities and decision checks below."
      action={
        <span className="rounded-full bg-u-inferred-tint px-2.5 py-0.5 text-meta font-medium text-u-inferred">
          {atBaseline ? "Agreed baseline" : "Current scenario"}
        </span>
      }
      note={<>Review source evidence before recording a hiring decision. Baseline is {splitLabel(baseline.share)}.</>}
    >
      <div className="mt-4 grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-3">
        {pair.map((row, index) => (
          <div key={row.candidate.candidateId} className="flex min-w-0 items-center gap-3 rounded-md bg-u-raised px-4 py-3.5">
            <span className={`h-[30px] w-[3px] flex-none rounded-sm ${index === 0 ? "bg-u-inferred" : "bg-u-signal"}`} />
            <Avatar id={row.candidate.personId} name={row.candidate.fullName} size="lg" />
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <span className="truncate text-body font-semibold">{row.candidate.fullName}</span>
              <span className="text-meta text-u-text3">
                #{row.rank} in scenario · {oneDecimal(row.baselineTotal)} baseline
              </span>
              <button
                type="button"
                onClick={() => onOpenAssessment(row.candidate.candidateId)}
                className="self-start text-meta text-u-text2 underline underline-offset-2 hover:text-u-text"
              >
                Open assessment
              </button>
            </div>
            <div className="flex flex-col items-end gap-0.5">
              <span className={`font-u-num text-title font-semibold ${index === 0 ? "text-u-inferred" : "text-u-signal"}`}>
                {oneDecimal(row.total)}
              </span>
              <span className="text-eyebrow uppercase tracking-[0.06em] text-u-text3">Overall / 100</span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-5 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1.5">
        <div className="text-body font-semibold">
          Competency scores <span className="font-normal text-u-text3">/ 10</span>
        </div>
        <div className="text-meta text-u-text3">Weighting changes influence, not assessed ability</div>
      </div>
      <div className="mt-3">
        <CompetencyBars a={a.candidate} b={b.candidate} criteria={criteria} />
      </div>

      <div className="mt-4 grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-x-7 gap-y-4 border-t border-u-border pt-4">
        <div className="flex flex-col gap-2">
          <div className={`${EYEBROW} text-u-inferred`}>Biggest differentiators</div>
          <p className="text-body">{differentiatorsOf(a.candidate, b.candidate, criteria)}</p>
        </div>
        <div className="flex flex-col gap-2">
          <div className={`${EYEBROW} text-u-signal`}>Evidence context</div>
          <p className="text-body">
            {evidenceContextOf(a.candidate, formatDay)} {evidenceContextOf(b.candidate, formatDay)}
          </p>
        </div>
      </div>

      <div className="mt-4 flex flex-col gap-3 rounded-lg bg-u-inferred-tint p-5">
        <div className="text-body font-semibold">{why.title}</div>
        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-eyebrow tracking-[0.04em] text-u-text2">{why.baselineEyebrow}</span>
            <span className="text-lead font-semibold">{why.baselineText}</span>
          </div>
          <span aria-hidden="true" className="text-u-inferred">
            →
          </span>
          <div className="flex flex-col gap-1">
            <span className="text-eyebrow tracking-[0.04em] text-u-text2">{why.scenarioEyebrow}</span>
            <span className="text-lead font-semibold text-u-inferred">{why.scenarioText}</span>
          </div>
        </div>
        <p className="text-note text-u-text2">{why.text}</p>
        {sensitivity && <p className="text-meta text-u-text2">{sensitivity}</p>}
      </div>

      <div className="mt-4 flex flex-col gap-2">
        <div className={`${EYEBROW} text-u-inferred`}>What to validate</div>
        <p className="text-body">{validateOf(pair, baselineLeader)}</p>
      </div>
    </ReportCard>
  );
}

const formatDay = (iso: string) => formatInstantDate(iso) ?? iso;
