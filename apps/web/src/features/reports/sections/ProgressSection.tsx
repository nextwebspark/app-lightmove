import { useState } from "react";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import type { ReportProgress } from "../api/types";
import { CoverageChart } from "../components/CoverageChart";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { DailyMomentumChart, WeeklyMomentumChart } from "../components/MomentumChart";
import { ChartLegend, ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { formatShortDate, percent } from "../lib/figures";
import { type Projection, type ProjectionBasis, projectCoverage, type WeeklyPace, weeklyPace } from "../lib/projection";

type MomentumView = "weeks" | "days";

const BASIS_OPTIONS = [
  { value: "recent" as const, label: "Last 3 weeks" },
  { value: "full" as const, label: "Full-mandate avg" },
];
const MOMENTUM_OPTIONS = [
  { value: "weeks" as const, label: "Weeks" },
  { value: "days" as const, label: "Days" },
];

/** 01 — are we going to hit the deadline? Projected from actual recent pace, not the plan. */
export function ProgressSection({ progress }: { progress: ReportProgress }) {
  const [basis, setBasis] = useState<ProjectionBasis>("recent");
  const [momentum, setMomentum] = useState<MomentumView>("weeks");
  const projection = projectCoverage(progress, basis);
  const pace = weeklyPace(progress);
  const covered = progress.companiesCumulative[projection.lastWeek];
  const target = progress.targetDate ? formatShortDate(progress.targetDate) : null;
  const isBehind = projection.targetPace !== null && projection.pace < projection.targetPace;

  return (
    <ReportSection
      id="progress"
      ordinal="01"
      eyebrow="Mapping progress"
      question="Are we going to hit the deadline?"
      lede="Coverage against the scoped universe, weekly research momentum, and a completion date projected from actual recent pace — not the original plan."
      findingLabel="At the current pace"
      findingTone={isBehind || projection.daysLate !== null && projection.daysLate > 0 ? "alarm" : "info"}
      finding={<ProgressFinding projection={projection} pace={pace} target={target} />}
    >
      <KpiTileRow>
        <KpiTile
          tone="accent"
          label="Companies mapped"
          value={covered}
          unit={`/ ${progress.targetCompanies}`}
          sub={`${percent(covered, progress.targetCompanies)}% of the scoped universe`}
        />
        <KpiTile label="Executives identified" value={pace.total} sub={`across ${progress.weekly.length} weeks`} />
        <KpiTile
          tone={isBehind ? "alarm" : "plain"}
          label="Recent pace"
          value={projection.pace.toFixed(1)}
          unit="/ wk"
          sub={projection.targetPace !== null ? `vs ${projection.targetPace.toFixed(1)} / wk needed` : "no target date set"}
        />
        <KpiTile
          tone={(progress.daysSinceLastCompany ?? 0) >= 5 ? "alarm" : "plain"}
          label="Since last new company"
          value={progress.daysSinceLastCompany ?? "—"}
          unit={progress.daysSinceLastCompany === null ? undefined : "days"}
          sub={progress.daysSinceLastCompany === null ? "no company mapped yet" : "gap since the last first executive"}
        />
      </KpiTileRow>

      <ReportCard
        title="Coverage vs. target"
        caption={`cumulative companies mapped · ${formatShortDate(progress.kickoff)}${projection.projectedDate ? ` – projected ${formatShortDate(projection.projectedDate)}` : ""}`}
        action={<SegmentedControl label="Projection basis" options={BASIS_OPTIONS} value={basis} onChange={setBasis} />}
        note={
          basis === "recent" ? (
            <>
              Projected from the average pace of the <b>last 3 weeks</b> ({projection.pace.toFixed(1)} / wk). A slowdown
              this recent would be masked by blending it with the faster early weeks — switch to “Full-mandate avg” to
              see how much difference that makes.
            </>
          ) : (
            <>
              Projected from the <b>full-mandate average</b> ({projection.pace.toFixed(1)} / wk). This blends in the
              early weeks and understates any recent slowdown. “Last 3 weeks” is the more honest basis for a live
              decision.
            </>
          )
        }
      >
        <ChartLegend
          items={[
            { label: "Actual", swatchClass: "bg-u-chart-1", shape: "line" },
            { label: "Projected", swatchClass: "bg-u-offlimits", shape: "dashed" },
            { label: "Target", swatchClass: "bg-u-sunken", shape: "line" },
          ]}
        />
        <CoverageChart progress={progress} projection={projection} />
      </ReportCard>

      <ReportCard
        title="Recent momentum"
        caption={`executives identified ${momentum === "weeks" ? "per week" : "per day"} · not cumulative`}
        action={<SegmentedControl label="Resolution" options={MOMENTUM_OPTIONS} value={momentum} onChange={setMomentum} />}
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
            <ChartLegend
              items={[
                { label: "At or above average", swatchClass: "bg-u-chart-1" },
                { label: `Below average (${Math.round(pace.average)} / wk)`, swatchClass: "bg-u-signal" },
              ]}
            />
            <WeeklyMomentumChart progress={progress} average={pace.average} />
          </>
        ) : (
          <>
            <ChartLegend
              items={[
                { label: "Daily count", swatchClass: "bg-u-sunken" },
                { label: "7-day rolling average", swatchClass: "bg-u-chart-1", shape: "line" },
              ]}
            />
            <DailyMomentumChart progress={progress} />
          </>
        )}
      </ReportCard>
    </ReportSection>
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
  const momentum =
    pace.firstMonth > 0 ? (
      <>
        Weekly pace has gone from <Figure>~{Math.round(pace.firstMonth)}/week</Figure> in the first month to{" "}
        <Figure>~{Math.round(pace.recent)}/week</Figure> recently.{" "}
      </>
    ) : null;

  if (projection.remaining === 0) {
    return (
      <>
        {momentum}Every company of the universe has at least one executive mapped — coverage is complete.
      </>
    );
  }
  if (projection.projectedDate === null) {
    return (
      <>
        {momentum}
        <Figure>{projection.remaining} companies</Figure> still have no executive, and no company gained a first one in the
        last three weeks — there is no pace to project from.
      </>
    );
  }
  if (target === null || projection.daysLate === null) {
    return (
      <>
        {momentum}At <Figure>{projection.pace.toFixed(1)} companies/week</Figure>, the remaining{" "}
        <Figure>{projection.remaining} companies</Figure> clear on{" "}
        <Figure>{formatShortDate(projection.projectedDate)}</Figure>. No target date is set on the mandate.
      </>
    );
  }
  if (projection.daysLate > 0) {
    return (
      <>
        {momentum}At <Figure>{projection.pace.toFixed(1)} companies/week</Figure>
        {projection.targetPace !== null ? (
          <>
            {" "}
            against the <Figure>{projection.targetPace.toFixed(1)}/week</Figure> originally needed
          </>
        ) : null}
        , the remaining <Figure>{projection.remaining} companies</Figure> won't clear until{" "}
        <Figure>{formatShortDate(projection.projectedDate)}</Figure> — <Figure>{projection.daysLate} days</Figure> behind the{" "}
        {target} target.
      </>
    );
  }
  return (
    <>
      {momentum}At <Figure>{projection.pace.toFixed(1)} companies/week</Figure>, the remaining{" "}
      <Figure>{projection.remaining} companies</Figure> clear on{" "}
      <Figure>{formatShortDate(projection.projectedDate)}</Figure>, inside the {target} target.
    </>
  );
}
