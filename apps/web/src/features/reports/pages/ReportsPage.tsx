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
import { compensationStats, ALL_COUNTRIES, ALL_NATIONALITIES } from "../lib/compensationStats";
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
  { key: "dei", ordinal: "04", label: "Diversity & DEI" },
];

/** The Reports tab: loads the mandate's report, then renders it. */
export function ReportsPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: report, isPending, isError } = useQuery({
    queryKey: reportApi.REPORT_KEY(project.id),
    queryFn: () => reportApi.getReport(project.id),
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
  const comp = compensationStats(report.remuneration, {
    measure: "package",
    country: ALL_COUNTRIES,
    nationality: ALL_NATIONALITIES,
  });
  const dei = diversityStats(report.diversity);
  const target = project.targetDate ?? report.progress.targetDate;

  return (
    <div className="flex animate-fade-up flex-wrap items-start gap-x-[38px] gap-y-5">
      <ReportNav items={NAV_ITEMS} footer={`Synced ${formatRelativeTime(report.head.syncedAt)}. Figures reflect the map at last sync.`} />

      <div className="min-w-0 max-w-[900px] flex-1 basis-[300px]">
        <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
          Talent mapping report · {report.head.region}
        </div>
        <h1 className="mt-2.5 text-[28px] font-bold leading-[1.15] tracking-[-0.02em]">
          {project.positionTitle} — {project.clientName}
        </h1>
        <div className="mt-2 font-mono text-xs text-text3">
          {report.head.universeCount}-company universe · {report.head.executivesMapped} executives mapped · target{" "}
          {formatDate(target)} · confidential
        </div>

        <div className="my-[22px] h-px bg-line" />

        <h2 className="max-w-[780px] text-xl font-semibold leading-[1.42]">
          The map is <Figure>{percent(covered, report.progress.targetCompanies)}% complete</Figure> but pace has slowed to{" "}
          <Figure>{projection.pace.toFixed(1)} companies a week</Figure>. Talent sits in three hubs, and our package
          ceiling is priced below{" "}
          <Figure>
            {comp.aboveBand} of {comp.disclosures.length}
          </Figure>{" "}
          verified disclosures.
        </h2>
        <p className="mt-3 max-w-[780px] text-[13.5px] leading-[1.65] text-text2">
          {covered} of {report.progress.targetCompanies} target companies have at least one executive mapped;{" "}
          {report.progress.targetCompanies - covered} still have none. At the recent pace full coverage lands on{" "}
          {formatShortDate(projection.projectedDate)}, {projection.daysLate} days after the target. Board-level coverage is
          thin, and female representation thins sharply toward the top.
        </p>

        <div className="mb-1 mt-[22px]">
          <StatRibbon
            stats={[
              { label: "Companies mapped", value: `${covered}/${report.progress.targetCompanies}`, valueClass: "text-sky" },
              { label: "Executives mapped", value: String(report.head.executivesMapped) },
              { label: "Recent pace", value: `${projection.pace.toFixed(1)}/wk`, valueClass: "text-red" },
              { label: "Behind target", value: `+${projection.daysLate}d`, valueClass: "text-red" },
              {
                label: "Ceiling percentile",
                value: comp.ceilingPercentile === null ? "—" : ordinal(comp.ceilingPercentile),
                valueClass: comp.ceilingPercentile !== null && comp.ceilingPercentile < 50 ? "text-red" : undefined,
              },
              { label: "Female", value: `${percent(dei.femaleTotal, dei.total)}%` },
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
          Generated {formatDate(report.progress.asOf)} · figures reflect the map at last sync · prepared for{" "}
          {project.clientName}, confidential
        </div>
      </div>
    </div>
  );
}
