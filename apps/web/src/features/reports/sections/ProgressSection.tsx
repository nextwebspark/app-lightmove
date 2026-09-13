import { useState } from "react";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import type { ReportProgress } from "../api/types";
import { CoverageChart } from "../components/CoverageChart";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { DailyMomentumChart, WeeklyMomentumChart } from "../components/MomentumChart";
import { ChartLegend, ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { formatShortDate, percent } from "../lib/figures";
import { type Projection, type ProjectionBasis, projectCoverage, weeklyPace } from "../lib/projection";

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
      heading={<ProgressFinding projection={projection} target={target} />}
      lede={
        <>
          New executives per week have gone from ~{Math.round(pace.firstMonth)} in the first month to ~
          {Math.round(pace.recent)} recently. Company coverage is moving at {projection.pace.toFixed(1)} a week
          {projection.targetPace !== null ? ` against the ${projection.targetPace.toFixed(1)} a week the plan needed` : ""}. A
          cumulative view alone would still look healthy — this is why a slowdown gets caught here.
        </>
      }
    >
      <KpiTileRow>
        <KpiTile
          label="Companies mapped"
          value={covered}
          unit={`/ ${progress.targetCompanies}`}
          sub={`${percent(covered, progress.targetCompanies)}% of the scoped universe`}
        />
        <KpiTile label="Executives identified" value={pace.total} sub={`across ${progress.weekly.length} weeks`} />
        <KpiTile
          label="Recent pace"
          value={projection.pace.toFixed(1)}
          unit="/ wk"
          valueClass={isBehind ? "text-red" : undefined}
          sub={projection.targetPace !== null ? `vs ${projection.targetPace.toFixed(1)} / wk needed` : "no target date set"}
        />
        <KpiTile
          label="Since last new company"
          value={progress.daysSinceLastCompany ?? "—"}
          unit={progress.daysSinceLastCompany === null ? undefined : "days"}
          valueClass={(progress.daysSinceLastCompany ?? 0) >= 5 ? "text-red" : undefined}
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
            { label: "Actual", swatchClass: "bg-sky", shape: "line" },
            { label: "Projected", swatchClass: "bg-red", shape: "dashed" },
            { label: "Target", swatchClass: "bg-line", shape: "line" },
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
                { label: "At or above average", swatchClass: "bg-sky" },
                { label: `Below average (${Math.round(pace.average)} / wk)`, swatchClass: "bg-amber" },
              ]}
            />
            <WeeklyMomentumChart progress={progress} average={pace.average} />
          </>
        ) : (
          <>
            <ChartLegend
              items={[
                { label: "Daily count", swatchClass: "bg-line" },
                { label: "7-day rolling average", swatchClass: "bg-sky", shape: "line" },
              ]}
            />
            <DailyMomentumChart progress={progress} />
          </>
        )}
      </ReportCard>
    </ReportSection>
  );
}

function ProgressFinding({ projection, target }: { projection: Projection; target: string | null }) {
  if (projection.remaining === 0) {
    return <>Every company of the universe has at least one executive mapped — coverage is complete.</>;
  }
  if (projection.projectedDate === null) {
    return (
      <>
        <Figure>{projection.remaining} companies</Figure> still have no executive, and no company gained a first one in the
        last three weeks — there is no pace to project from.
      </>
    );
  }
  if (target === null || projection.daysLate === null) {
    return (
      <>
        At the current pace the remaining <Figure>{projection.remaining} companies</Figure> clear on{" "}
        <Figure>{formatShortDate(projection.projectedDate)}</Figure>. No target date is set on the mandate.
      </>
    );
  }
  if (projection.daysLate > 0) {
    return (
      <>
        At the current pace the remaining <Figure>{projection.remaining} companies</Figure> clear on{" "}
        <Figure>{formatShortDate(projection.projectedDate)}</Figure> — <Figure>{projection.daysLate} days</Figure> behind the{" "}
        {target} target.
      </>
    );
  }
  return (
    <>
      At the current pace the remaining <Figure>{projection.remaining} companies</Figure> clear on{" "}
      <Figure>{formatShortDate(projection.projectedDate)}</Figure>, inside the {target} target.
    </>
  );
}
