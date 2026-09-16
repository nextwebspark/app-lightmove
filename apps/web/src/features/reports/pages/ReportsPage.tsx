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
  const target = project.targetDate ?? report.progress.targetDate;

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

        <div className="flex flex-col gap-[34px]">
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
