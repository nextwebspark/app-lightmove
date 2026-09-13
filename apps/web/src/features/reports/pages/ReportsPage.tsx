import { useQuery } from "@tanstack/react-query";
import { useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState, Spinner } from "../../../components/ui";
import { formatDate, formatRelativeTime } from "../../../lib/format";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import type { Report } from "../api/types";
import { ReportNav, type ReportNavItem } from "../components/ReportNav";
import { Figure } from "../components/ReportSection";
import { StatRibbon } from "../components/StatRibbon";
import { ALL_COUNTRIES, ALL_NATIONALITIES, compensationStats } from "../lib/compensationStats";
import { diversityStats } from "../lib/diversityStats";
import { formatShortDate, ordinal, percent } from "../lib/figures";
import { projectCoverage } from "../lib/projection";
import { DiversitySection } from "../sections/DiversitySection";
import { MarketSection } from "../sections/MarketSection";
import { ProgressSection } from "../sections/ProgressSection";
import { RemunerationSection } from "../sections/RemunerationSection";

const NAV_ITEMS: ReportNavItem[] = [
  { key: "progress", ordinal: "01", label: "Mapping progress" },
  { key: "market", ordinal: "02", label: "Shape of the market" },
  { key: "comp", ordinal: "03", label: "Remuneration" },
  { key: "dei", ordinal: "04", label: "Nationality & localisation" },
];

/** The Reports tab: loads the mandate's report, then renders it. */
export function ReportsPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: report, isPending, isError } = useQuery({
    queryKey: reportApi.REPORT_KEY(project.id),
    queryFn: ({ signal }) => reportApi.getReport(project.id, signal),
  });

  if (isPending) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  // A report is nothing but stated figures, so a refused read must not fall through to a rendered
  // one: every number on this page would read as a measurement of the search rather than a 403.
  if (isError) {
    return (
      <EmptyState
        icon={<Icon d={ICONS.lock} size={24} />}
        title="Couldn't load this report"
        body="You may no longer have access to this mandate, or the request failed. Reload the page, and ask the project lead if it keeps happening."
      />
    );
  }

  return <ReportBody project={project} report={report} />;
}

function ReportBody({ project, report }: { project: Project; report: Report }) {
  const projection = projectCoverage(report.progress, "recent");
  const covered = report.progress.companiesCumulative[projection.lastWeek];
  const coveredPct = percent(covered, report.progress.targetCompanies);
  const comp = compensationStats(report.remuneration, {
    measure: "package",
    country: ALL_COUNTRIES,
    nationality: ALL_NATIONALITIES,
  });
  const dei = diversityStats(report.diversity);
  const target = project.targetDate ?? report.progress.targetDate;
  const isLate = projection.daysLate !== null && projection.daysLate > 0;

  return (
    <div className="flex animate-fade-up flex-wrap items-start gap-x-[38px] gap-y-5">
      <ReportNav
        items={NAV_ITEMS}
        footer={`Generated ${formatRelativeTime(report.head.generatedAt)}, live from the mandate's rows.${
          report.head.truncated ? " Row caps were hit: the chapters describe a sample." : ""
        }`}
      />

      <div className="min-w-0 max-w-[900px] flex-1 basis-[300px]">
        <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
          Talent mapping report
        </div>
        <h1 className="mt-2.5 text-[28px] font-bold leading-[1.15] tracking-[-0.02em]">
          {project.positionTitle} — {project.clientName}
        </h1>
        <div className="mt-2 font-mono text-xs text-text3">
          {report.head.universeCount}-company universe · {report.head.executivesMapped} executives mapped
          {target ? ` · target ${formatDate(target)}` : " · no target date"} · confidential
        </div>

        <div className="my-[22px] h-px bg-line" />

        <h2 className="max-w-[780px] text-xl font-semibold leading-[1.42]">
          The map is <Figure>{coveredPct}% complete</Figure>
          {projection.projectedDate && isLate ? (
            <>
              {" "}
              and, at <Figure>{projection.pace.toFixed(1)} companies a week</Figure>, lands{" "}
              <Figure>{projection.daysLate} days</Figure> past the target
            </>
          ) : (
            <>
              {" "}
              at <Figure>{projection.pace.toFixed(1)} companies a week</Figure>
            </>
          )}
          .{" "}
          {comp.ceilingPercentile !== null ? (
            <>
              Our package ceiling sits at the <Figure>{ordinal(comp.ceilingPercentile)} percentile</Figure> of what the
              market disclosed.
            </>
          ) : (
            <>Compensation cannot be ranked yet — the brief states no band or too few packages are on file.</>
          )}
        </h2>
        <p className="mt-3 max-w-[780px] text-[13.5px] leading-[1.65] text-text2">
          {covered} of {report.progress.targetCompanies} universe companies have at least one executive mapped;{" "}
          {report.progress.targetCompanies - covered} still have none.
          {projection.projectedDate ? ` At the recent pace full coverage lands on ${formatShortDate(projection.projectedDate)}.` : ""}{" "}
          {dei.largest ? `${dei.nationalityCount} nationalities are represented; GCC nationals are ${dei.gccPct}% of the pool.` : ""}
        </p>

        <div className="mb-1 mt-[22px]">
          <StatRibbon
            stats={[
              { label: "Companies mapped", value: `${covered}/${report.progress.targetCompanies}`, valueClass: "text-sky" },
              { label: "Executives mapped", value: String(report.head.executivesMapped) },
              {
                label: "Recent pace",
                value: `${projection.pace.toFixed(1)}/wk`,
                valueClass: projection.targetPace !== null && projection.pace < projection.targetPace ? "text-red" : undefined,
              },
              {
                label: "Vs target",
                value: projection.daysLate === null ? "—" : projection.daysLate > 0 ? `+${projection.daysLate}d` : `${projection.daysLate}d`,
                valueClass: isLate ? "text-red" : undefined,
              },
              {
                label: "Ceiling percentile",
                value: comp.ceilingPercentile === null ? "—" : ordinal(comp.ceilingPercentile),
                valueClass: comp.ceilingPercentile !== null && comp.ceilingPercentile < 50 ? "text-red" : undefined,
              },
              { label: "GCC nationals", value: `${dei.gccPct}%` },
            ]}
          />
        </div>

        <div className="flex flex-col gap-[34px] pt-[38px]">
          <ProgressSection progress={report.progress} />
          <MarketSection market={report.market} universeCount={report.head.universeCount} projectId={project.id} />
          <RemunerationSection remuneration={report.remuneration} />
          <DiversitySection diversity={report.diversity} />
        </div>

        <div className="mt-[34px] border-t border-line pt-[18px] font-mono text-[11px] leading-[1.6] text-text3">
          Generated {formatDate(report.progress.asOf)} · every figure is aggregated from the mandate's rows at read time ·
          prepared for {project.clientName}, confidential
        </div>
      </div>
    </div>
  );
}
