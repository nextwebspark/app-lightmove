import { useQuery } from "@tanstack/react-query";
import { useOutletContext, useSearchParams } from "react-router-dom";
import { ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner } from "../../../components/ui";
import { formatRelativeTime } from "../../../lib/format";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import type { Report } from "../api/types";
import { ReportGap } from "../components/ReportGap";
import { ReportNav } from "../components/ReportNav";
import { DiversitySection } from "../sections/DiversitySection";
import { MarketSection } from "../sections/MarketSection";
import { ProgressSection } from "../sections/ProgressSection";
import { RemunerationSection } from "../sections/RemunerationSection";

const CHAPTER_PARAM = "chapter";

const CHAPTERS = [
  { key: "progress", label: "Mapping progress", icon: ICONS.trendingUp },
  { key: "market", label: "Shape of the market", icon: ICONS.globe },
  { key: "comp", label: "Remuneration", icon: ICONS.currency },
  { key: "dei", label: "Diversity & DEI", icon: ICONS.team },
] as const;

type ChapterKey = (typeof CHAPTERS)[number]["key"];

const GROUND = "flex flex-1 bg-u-bg text-u-text";

/** The Reports tab: loads the mandate's report, then renders it one chapter at a time. */
export function ReportsPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: report, isPending, isError } = useQuery({
    queryKey: reportApi.REPORT_KEY(project.id),
    queryFn: ({ signal }) => reportApi.getReport(project.id, signal),
    // Always stale, so opening the tab re-reads it. The report is an aggregate of companies, people,
    // the brief and the mandate's dates, and none of their writes invalidate it: under the app's 30s
    // default an executive edited a moment ago was missing from the chapter that counts them.
    staleTime: 0,
  });

  if (isPending) {
    return (
      <div className={`${GROUND} justify-center pt-24 text-u-text3`}>
        <Spinner />
      </div>
    );
  }

  // A report is nothing but stated figures, so a refused read must not fall through to a rendered
  // one: every number on this page would read as a measurement of the search rather than a 403.
  if (isError) {
    return (
      <div className={`${GROUND} items-start justify-center pt-16`}>
        <div className="max-w-[440px]">
          <ReportGap icon={ICONS.lock} title="Couldn't load this report">
            <div className="rounded-[9px] bg-u-sunken px-4 py-3.5 text-xs leading-[1.6] text-u-text2">
              You may no longer have access to this mandate, or the request failed. Reload the page, and ask the
              project lead if it keeps happening.
            </div>
          </ReportGap>
        </div>
      </div>
    );
  }

  return <ReportBody project={project} report={report} />;
}

function ReportBody({ project, report }: { project: Project; report: Report }) {
  const [searchParams] = useSearchParams();
  const requested = searchParams.get(CHAPTER_PARAM);
  const active = CHAPTERS[Math.max(CHAPTERS.findIndex((chapter) => chapter.key === requested), 0)];

  return (
    <div className={`${GROUND} flex-col lg:flex-row`}>
      <ReportNav
        chapters={CHAPTERS}
        activeKey={active.key}
        footer={
          <>
            Talent mapping report, prepared for {project.clientName} — confidential. Generated{" "}
            {formatRelativeTime(report.head.generatedAt)}, live from the mandate's rows.
            {report.head.truncated && " Row caps were hit: the chapters describe a sample."}
          </>
        }
      />
      <div className="min-w-0 flex-1">
        <div className="max-w-[900px] px-4 pb-[100px] pt-[34px] sm:px-10">
          <Chapter chapterKey={active.key} project={project} report={report} />
        </div>
      </div>
    </div>
  );
}

function Chapter({
  chapterKey,
  project,
  report,
}: {
  chapterKey: ChapterKey;
  project: Project;
  report: Report;
}) {
  switch (chapterKey) {
    case "progress":
      return <ProgressSection progress={report.progress} />;
    case "market":
      return (
        <MarketSection
          market={report.market}
          universeCount={report.head.universeCount}
          executivesMapped={report.head.executivesMapped}
          currency={report.remuneration.currency}
          projectId={project.id}
        />
      );
    case "comp":
      return <RemunerationSection remuneration={report.remuneration} />;
    case "dei":
      return <DiversitySection diversity={report.diversity} />;
  }
}
