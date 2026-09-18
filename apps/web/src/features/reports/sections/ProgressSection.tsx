import { useState } from "react";
import { ICONS } from "../../../components/layout/Icon";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import type { ReportProgress } from "../api/types";
import { CoverageChart } from "../components/CoverageChart";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { DailyMomentumChart, WeeklyMomentumChart } from "../components/MomentumChart";
import { Legend } from "../components/Legend";
import { ReportCard } from "../components/ReportCard";
import { ReportGap } from "../components/ReportGap";
import { Figure, ReportSection } from "../components/ReportSection";
import { formatShortDate, percent } from "../lib/figures";
import {
  FIRST_MONTH_WEEKS,
  type Projection,
  type ProjectionBasis,
  projectCoverage,
  RECENT_WEEKS,
  type WeeklyPace,
  weeklyPace,
} from "../lib/projection";

type MomentumView = "weeks" | "days";

const BASIS_OPTIONS = [
  { value: "recent" as const, label: "Last 3 weeks" },
  { value: "full" as const, label: "Full-mandate avg" },
];
/** Complete weeks before a week is worth calling above or below the run's average. */
const JUDGEABLE_WEEKS = 3;
const MOMENTUM_OPTIONS = [
  { value: "weeks" as const, label: "Weeks" },
  { value: "days" as const, label: "Days" },
];

const LEDE = (
  <>
    Coverage against the scoped universe, weekly research momentum, and a completion date{" "}
    <b>projected from actual recent pace</b> — not the original plan.
  </>
);

/** Are we going to hit the deadline? Projected from actual recent pace, not the plan. */
export function ProgressSection({ progress }: { progress: ReportProgress }) {
  const [basis, setBasis] = useState<ProjectionBasis>("recent");
  const [momentum, setMomentum] = useState<MomentumView>("weeks");
  const projection = projectCoverage(progress, basis);
  const pace = weeklyPace(progress);
  const covered = projection.covered;
  const target = progress.targetDate ? formatShortDate(progress.targetDate) : null;
  const gap = progress.daysSinceLastExecutive;
  const projects = projection.status === "projected";
  // Stalled keeps the toggle: a mandate that moved early and stopped has no recent pace but a real
  // full-mandate one, and comparing the two is the whole point of the control.
  const canChooseBasis = projection.completeWeeks > 0 && projection.remaining > 0;
  // One complete week's average is that week, and two weeks' is just which of the two was bigger.
  // Below three, a bar drawn red or green would be reporting the data back as a judgement of it.
  const average = projection.completeWeeks >= JUDGEABLE_WEEKS ? pace.average : null;
  // The figure follows the toggle, so the label has to as well: it was reading "Recent pace" over
  // the full-mandate average the moment anyone switched.
  const paceLabel = basis === "recent" ? "Recent pace" : "Full-mandate pace";

  // Every figure in this chapter is measured against the universe, so with none there is nothing to
  // measure rather than a mandate at zero percent of its scope.
  if (projection.status === "no-universe") {
    return (
      <ReportSection question="Are we going to hit the deadline?" lede={LEDE}>
        <ReportGap icon={ICONS.searchX} title="No companies scoped to this mandate yet.">
          <p className="mx-auto max-w-[520px] text-[13px] leading-[1.65] text-u-text2">
            Coverage, pace and a completion date are all measured against the mandate's universe. Add companies from
            Strategy or the Companies screen and this chapter fills in.
          </p>
        </ReportGap>
      </ReportSection>
    );
  }

  return (
    <ReportSection
      question="Are we going to hit the deadline?"
      lede={LEDE}
      findingLabel="At the current pace"
      finding={<ProgressFinding projection={projection} pace={pace} target={target} />}
    >
      <KpiTileRow>
        <KpiTile
          tone="lead"
          label="Companies mapped"
          value={covered}
          unit={`/${progress.targetCompanies}`}
          sub={`${percent(covered, progress.targetCompanies)}% of the scoped universe`}
        />
        <KpiTile
          label="Executives identified"
          value={pace.total}
          sub={`across ${progress.weekly.length} ${progress.weekly.length === 1 ? "week" : "weeks"}`}
        />
        {projection.pace === null ? (
          // An unmeasured pace is not a risk, so it does not wear the alarm tone. "0.0/wk" here read
          // as a stall the rows never recorded.
          <KpiTile label={paceLabel} value="—" sub="needs a full week of history" />
        ) : (
          <KpiTile
            tone="alarm"
            label={paceLabel}
            value={projection.pace.toFixed(1)}
            unit="/wk"
            sub={projection.targetPace !== null ? `vs ${projection.targetPace.toFixed(1)}/wk needed` : "no target date set"}
          />
        )}
        <KpiTile
          tone="alarm"
          label="Since last new executive"
          value={gap ?? "—"}
          unit={gap === null ? undefined : "d"}
          sub={gap === null ? "no executive mapped yet" : "gap since the last one filed"}
        />
      </KpiTileRow>

      <ReportCard
        title={projection.status === "insufficient" ? "Coverage so far" : "Coverage vs. target"}
        caption={
          projects && projection.projectedDate
            ? `cumulative companies mapped · ${formatShortDate(progress.kickoff)} – projected ${formatShortDate(projection.projectedDate)}`
            : `cumulative companies mapped · from ${formatShortDate(progress.kickoff)}`
        }
        action={
          canChooseBasis ? (
            <SegmentedControl variant="uncava" label="Projection basis" options={BASIS_OPTIONS} value={basis} onChange={setBasis} />
          ) : undefined
        }
        note={<CoverageNote projection={projection} basis={basis} />}
      >
        <Legend
          className="mb-1 mt-2.5"
          items={[
            { label: "Actual", swatchClass: "bg-u-accent", shape: "line" },
            ...(projects ? [{ label: "Projected", swatchClass: "bg-u-offlimits", shape: "dashed" as const }] : []),
            { label: "Target", swatchClass: "bg-u-border-strong", shape: "line" },
          ]}
        />
        <CoverageChart progress={progress} projection={projection} />
      </ReportCard>

      <ReportCard
        title="Recent momentum"
        caption={`executives identified ${momentum === "weeks" ? "per week" : "per day"} · not cumulative`}
        action={<SegmentedControl variant="uncava" label="Resolution" options={MOMENTUM_OPTIONS} value={momentum} onChange={setMomentum} />}
        note={
          momentum === "weeks" ? (
            <>
              A cumulative chart would still be climbing and look healthy through a slowdown. This view is what catches
              one <b>before</b> it costs a missed company milestone.
            </>
          ) : (
            <>
              Daily counts are noisy on their own — the GCC weekend (Fri–Sat) shows near-zero every week by design, not
              as a signal. The <b>7-day rolling average</b> is what shows the trend.
            </>
          )
        }
      >
        {momentum === "weeks" ? (
          <>
            <Legend
              className="mb-1 mt-2.5"
              items={[
                ...(average === null
                  ? [{ label: "Executives identified", swatchClass: "bg-u-accent", shape: "dot" as const }]
                  : [
                      { label: "At or above average", swatchClass: "bg-u-direct", shape: "dot" as const },
                      { label: `Below average (${Math.round(average)}/wk)`, swatchClass: "bg-u-offlimits", shape: "dot" as const },
                    ]),
                ...(average !== null && progress.weekly.length > projection.completeWeeks
                  ? [{ label: "Week in progress", swatchClass: "bg-u-accent", shape: "dot" as const }]
                  : []),
              ]}
            />
            <WeeklyMomentumChart progress={progress} average={average} completeWeeks={projection.completeWeeks} />
          </>
        ) : (
          <>
            <Legend
              className="mb-1 mt-2.5"
              items={[
                { label: "Daily count", swatchClass: "border border-u-border-strong bg-u-sunken", shape: "dot" },
                { label: "7-day rolling average", swatchClass: "bg-u-accent", shape: "line" },
              ]}
            />
            <DailyMomentumChart progress={progress} />
          </>
        )}
      </ReportCard>
    </ReportSection>
  );
}

/** How old the mandate is, said the way a sentence needs it rather than as a bare number. */
function mandateAge(elapsedDays: number): string {
  if (elapsedDays === 0) return "started today";
  return `is ${elapsedDays} day${elapsedDays === 1 ? "" : "s"} old`;
}

/** The weeks a pace actually averaged, so no line claims three that never happened. */
function paceWindow(projection: Projection): string {
  if (projection.basis === "full") return "the whole mandate";
  return projection.paceWeeks === 1 ? "the last complete week" : `the last ${projection.paceWeeks} complete weeks`;
}

/**
 * What the chart underneath means. It switches with the projection's status and not only with the
 * basis, because describing a projection basis under a chart that projects nothing is how "0.0/wk"
 * came to be printed as a measured pace.
 */
function CoverageNote({ projection, basis }: { projection: Projection; basis: ProjectionBasis }) {
  if (projection.status === "insufficient") {
    return (
      <>
        Too little history to project — this mandate {mandateAge(projection.elapsedDays)}. A pace needs a full week
        behind it; this fills in on its own.
      </>
    );
  }
  if (projection.status === "complete") {
    return (
      <>
        Every company of the scoped universe has an executive mapped, so there is <b>nothing left to project</b>. The
        line is what the mandate did, not a forecast.
      </>
    );
  }
  if (projection.pace === null || projection.status === "stalled") {
    return (
      <>
        The line stops at today because <b>nothing has been added across {paceWindow(projection)}</b> — a dashed
        projection would have to invent the rate it runs at. The week in progress is not counted towards the pace.
      </>
    );
  }
  return basis === "recent" ? (
    <>
      Projected from the average pace of <b>{paceWindow(projection)}</b> ({projection.pace.toFixed(1)}/wk). A slowdown
      this recent would be masked by blending it with the faster early weeks — switch to “Full-mandate avg” to see how
      much difference that makes.
    </>
  ) : (
    <>
      Projected from the <b>full-mandate average</b> ({projection.pace.toFixed(1)}/wk). This blends in the early weeks
      and understates any recent slowdown. “Last 3 weeks” is the more honest basis for a live decision.
    </>
  );
}

function ProgressFinding({
  projection,
  pace,
  target,
}: {
  projection: Projection;
  pace: WeeklyPace;
  target: string | null;
}) {
  // "From the first month to recently" is a comparison, so it needs two spans that do not overlap:
  // the first four complete weeks and the last three are the same weeks until there are seven. Below
  // that it printed one week's average as both halves — "from ~8/week … to ~8/week".
  const comparable = pace.completeWeeks >= FIRST_MONTH_WEEKS + RECENT_WEEKS;
  const momentum =
    comparable && pace.firstMonth !== null && pace.recent !== null ? (
      <>
        Weekly pace has gone from <Figure>~{Math.round(pace.firstMonth)}/week</Figure> in the first month to{" "}
        <Figure>~{Math.round(pace.recent)}/week</Figure> recently.{" "}
      </>
    ) : null;

  // Before anything is said about a pace: a mandate with no complete week behind it has no pace,
  // and a zero is not the honest way to say so.
  if (projection.status === "insufficient") {
    return (
      <>
        Nothing to project yet — this mandate {mandateAge(projection.elapsedDays)}.{" "}
        <Figure>
          {projection.covered} of {projection.covered + projection.remaining} companies
        </Figure>{" "}
        have an executive mapped. A completion date needs a full week of pace behind it.
      </>
    );
  }
  if (projection.status === "complete") {
    return <>{momentum}Every company of the universe has at least one executive mapped — coverage is complete.</>;
  }
  if (projection.status === "stalled") {
    return (
      <>
        {momentum}
        <Figure>{projection.remaining} companies</Figure> still have no executive, and none gained a first one across{" "}
        {paceWindow(projection)} — there is no pace to project from.
      </>
    );
  }
  if (projection.status !== "projected") return null;
  const paceFigure = <Figure>{projection.pace.toFixed(1)} companies/week</Figure>;
  const projectedDate = <Figure>{formatShortDate(projection.projectedDate)}</Figure>;
  if (target === null || projection.daysLate === null) {
    return (
      <>
        {momentum}At {paceFigure}, the remaining <Figure>{projection.remaining} companies</Figure> clear on{" "}
        {projectedDate}. No target date is set on the mandate.
      </>
    );
  }
  if (projection.daysLate > 0) {
    return (
      <>
        {momentum}At {paceFigure}
        {projection.targetPace !== null ? (
          <>
            {" "}
            against the <Figure>{projection.targetPace.toFixed(1)}/week</Figure> originally needed
          </>
        ) : null}
        , the remaining <Figure>{projection.remaining} companies</Figure> won't clear until {projectedDate} —{" "}
        <Figure>{projection.daysLate} days</Figure> behind the {target} target.
      </>
    );
  }
  return (
    <>
      {momentum}At {paceFigure}, the remaining <Figure>{projection.remaining} companies</Figure> clear on{" "}
      {projectedDate}, inside the {target} target.
    </>
  );
}
