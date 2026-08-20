import { useQuery } from "@tanstack/react-query";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState, Spinner } from "../../../components/ui";
import { abbreviateAmount } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import type { CompensationBand, Report } from "../api/types";
import { BarList } from "../components/BarList";
import { ReportNav, type ReportNavItem } from "../components/ReportNav";
import { ReportSection } from "../components/ReportSection";
import { ScopeCaveatsNotice } from "../components/ScopeCaveatsNotice";
import { SectionUnavailable } from "../components/SectionUnavailable";
import { StatRibbon } from "../components/StatRibbon";
import { nextActionsFor } from "../lib/nextActions";

const NAV_ITEMS: ReportNavItem[] = [
  { key: "progress", ordinal: "01", label: "Mapping progress" },
  { key: "market", ordinal: "02", label: "Shape of the market" },
  { key: "geography", ordinal: "03", label: "Where talent sits" },
  { key: "people", ordinal: "04", label: "Diversity & DEI" },
  { key: "interest", ordinal: "05", label: "Interest & availability" },
  { key: "comp", ordinal: "06", label: "Remuneration" },
  { key: "quality", ordinal: "07", label: "Data quality" },
  { key: "actions", ordinal: "·", label: "Next actions" },
];

/** The Reports tab: loads the mandate's report, then renders it. */
export function ReportsPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const {
    data: report,
    isPending,
    isError,
  } = useQuery({
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
  const { user } = useAuth();
  const scoped = report.sectorsInScope > 0;

  // Next actions is the firm's own worklist — it tells the reader to go set a scope or price the
  // mandate, on screens a client seat holds WORK_VIEW over and PROJECT_EDIT on none of. The rest of
  // the report is written for them; this section is not, so a pure client is shown neither it nor
  // its nav entry.
  const showsNextActions = !isPureClient(user?.workspace?.roles ?? []);
  const navItems = showsNextActions ? NAV_ITEMS : NAV_ITEMS.filter((item) => item.key !== "actions");

  return (
    <div className="flex animate-fade-up flex-wrap items-start gap-x-[38px] gap-y-5">
      <ReportNav items={navItems} />

      <div className="min-w-0 max-w-[900px] flex-1 basis-[300px]">
        <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
          Talent mapping report
        </div>
        <h1 className="mt-2.5 text-[28px] font-bold leading-[1.15] tracking-[-0.02em]">
          {project.positionTitle} — {project.clientName}
        </h1>
        <div className="mt-2 font-mono text-xs text-text3">
          {report.universeCount}-company universe · {report.sectorsInScope} sectors selected ·{" "}
          {/* An empty geography filter does not narrow the search — it leaves every market in. */}
          {report.marketsInScope === 0 ? "all markets" : `${report.marketsInScope} markets`}
        </div>

        <div className="my-[22px] h-px bg-line" />

        <h2 className="max-w-[780px] text-xl font-semibold leading-[1.42]">
          {scoped ? (
            <>
              {/* No sector or market count here: the breakdowns below are top-N, so their lengths are
                  a truncated view, and stating one as a total is the same lie as a count read off a
                  refused list. */}
              The saved scope covers <span className="text-sky">{report.universeCount} companies</span>.
              No executive has been mapped against them yet.
            </>
          ) : (
            <>
              This mandate has <span className="text-sky">no search scope</span> yet, so there is
              nothing to report on.
            </>
          )}
        </h2>
        <p className="mt-3 max-w-[780px] text-[13.5px] leading-[1.65] text-text2">
          Everything below is measured live from the mandate's Strategy scope and its position brief.
          The sections that describe people — mapping progress, diversity, interest and captured
          compensation — stay empty until candidate mapping is built.
        </p>

        <div className="mb-1 mt-[22px]">
          <StatRibbon
            stats={[
              { label: "Companies in scope", value: String(report.universeCount) },
              { label: "Sectors in scope", value: String(report.sectorsInScope) },
              { label: "Markets in scope", value: String(report.marketsInScope) },
              {
                label: "Off-limits",
                value: String(report.offLimitsCompanies),
                valueClass: report.offLimitsCompanies > 0 ? "text-red" : undefined,
              },
            ]}
          />
          <ScopeCaveatsNotice caveats={report.caveats} />
        </div>

        <div className="flex flex-col gap-[34px] pt-[38px]">
          <ReportSection
            id="progress"
            ordinal="01"
            eyebrow="Mapping progress"
            heading="No executives have been mapped against this universe."
            lede="Progress, pace and researcher contribution are measured from mapped executives."
          >
            <SectionUnavailable body="Mapping velocity and per-researcher contribution arrive with the Candidates screen. Until then this mandate has a universe but no one mapped into it." />
          </ReportSection>

          <ReportSection
            id="market"
            ordinal="02"
            eyebrow="Shape of the market"
            heading={
              scoped ? (
                <>
                  <span className="text-sky">{report.sectors[0]?.label ?? "No sector"}</span> leads the
                  universe this search is scoped to.
                </>
              ) : (
                "Pick a sector on Strategy and the market takes shape here."
              )
            }
            lede="Where the companies this search is scoped to actually sit — by sector, and by the place they are run from."
          >
            {scoped ? (
              <div className="grid gap-[30px] [grid-template-columns:repeat(auto-fit,minmax(272px,1fr))]">
                {/* "Largest"/"Top", not "by": the server caps these lists, so a caption that read as
                    the full set would make the bars a complete picture they are not. */}
                <BarList caption="Largest sectors" rows={report.sectors} />
                <BarList caption="Largest markets" rows={report.countries} />
              </div>
            ) : (
              <SectionUnavailable body="The universe is empty until the Strategy tab names at least one sector." />
            )}
          </ReportSection>

          <ReportSection
            id="geography"
            ordinal="03"
            eyebrow="Where talent sits"
            heading={
              report.countries.length > 0 ? (
                <>
                  The universe is concentrated in{" "}
                  <span className="text-sky">{report.countries[0].label}</span>.
                </>
              ) : (
                "No headquarters are known for the companies in scope."
              )
            }
            lede="Where the companies are headquartered. Where their executives actually sit is a separate question, and needs mapped people to answer."
          >
            {report.countries.length > 0 ? (
              <div className="grid gap-[30px] [grid-template-columns:repeat(auto-fit,minmax(272px,1fr))]">
                <BarList caption="Top countries" rows={report.countries} />
                <BarList caption="Top hub cities" rows={report.cities} />
              </div>
            ) : (
              <SectionUnavailable body="No company in scope carries a headquarters country in the universe data." />
            )}
          </ReportSection>

          <ReportSection
            id="people"
            ordinal="04"
            eyebrow="Diversity & DEI"
            heading="Representation cannot be measured without mapped people."
            lede="Gender split by level, national representation and nationality mix are all attributes of executives."
          >
            <SectionUnavailable body="These figures describe the people mapped into the search, not the companies in it. They arrive with the Candidates screen." />
          </ReportSection>

          <ReportSection
            id="interest"
            ordinal="05"
            eyebrow="Interest & availability"
            heading="Nobody has been approached on this mandate yet."
            lede="Interest, availability and the shortlist funnel are recorded against executives during outreach."
          >
            <SectionUnavailable body="The funnel from mapped to shortlisted needs the Candidates and Outreach screens, neither of which is built." />
          </ReportSection>

          <ReportSection
            id="comp"
            ordinal="06"
            eyebrow="Remuneration"
            heading={
              report.mandateBand ? (
                <>
                  The mandate is pitched at{" "}
                  <span className="text-sky">{bandLabel(report.mandateBand)}</span>.
                </>
              ) : (
                "The position brief states no compensation band."
              )
            }
            lede="How the market sits against that band needs captured compensation on mapped executives."
          >
            {report.mandateBand ? (
              <>
                <div className="rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-4">
                  <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
                    Mandate band · from the position brief
                  </div>
                  <div className="mt-2 font-mono text-[21px] font-bold tracking-[-0.01em]">
                    {bandLabel(report.mandateBand)}
                  </div>
                </div>
                <div className="mt-3.5">
                  <SectionUnavailable body="Position against the band — how many mapped executives sit below, within and above it — needs captured compensation." />
                </div>
              </>
            ) : (
              <SectionUnavailable body="Add a salary range on the Position tab, and the band it targets is stated here." />
            )}
          </ReportSection>

          <ReportSection
            id="quality"
            ordinal="07"
            eyebrow="Data quality"
            heading="Verification and confidence are properties of mapped records."
            lede="What share of profiles is verified, and how much contact and compensation detail was captured."
          >
            <SectionUnavailable body="There are no mapped records to assess. The company universe itself is ETL reference data, refreshed by the pipeline rather than verified per mandate." />
          </ReportSection>

          {showsNextActions && (
          <ReportSection
            id="actions"
            ordinal="·"
            eyebrow="Next actions"
            heading="What would move this search forward."
          >
            <div className="flex flex-col">
              {nextActionsFor(report, project.id).map((action, index) => (
                <div
                  key={action.title}
                  className="flex items-baseline gap-3 border-b border-line-soft py-3 last:border-0"
                >
                  <span className="flex-none font-mono text-[10px] font-bold tracking-[0.06em] text-text3">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span className="flex-1">
                    <span className="block text-[13px] font-semibold">
                      {action.to ? (
                        <Link to={action.to} className="text-sky hover:underline">
                          {action.title}
                        </Link>
                      ) : (
                        action.title
                      )}
                    </span>
                    <span className="mt-0.5 block text-xs text-text2">{action.body}</span>
                  </span>
                </div>
              ))}
            </div>
          </ReportSection>
          )}
        </div>

        <div className="mt-[34px] border-t border-line pt-4 font-mono text-[10.5px] text-text3">
          Figures reflect the mandate's saved scope as it stands · prepared for {project.clientName}
        </div>
      </div>
    </div>
  );
}


function bandLabel(band: CompensationBand): string {
  const min = band.min === null ? null : abbreviateAmount(band.min);
  const max = band.max === null ? null : abbreviateAmount(band.max);
  if (min !== null && max !== null) return `${band.currency} ${min}–${max}`;
  return `${band.currency} ${min ?? max} ${min === null ? "and below" : "and above"}`;
}
