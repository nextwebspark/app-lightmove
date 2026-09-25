import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import * as reportApi from "../api/reportApi";
import type { TeamRange } from "../api/reportApi";
import type { CompanyCoverage, Researcher, ReportProgress, TeamPerformance } from "../api/types";
import { daysBetween, formatShortDate, percent } from "../lib/figures";
import { projectCoverage } from "../lib/projection";
import { roleLabel, sinceLabel } from "../lib/teamLabels";
import { DEFAULT_TEAM_RANGE, TEAM_RANGES, type TeamRangeKey, teamRangeOf } from "../lib/teamRange";
import { ChartEmpty } from "./ChartEmpty";
import { CoverageDonut, type DonutSegment } from "./CoverageDonut";
import { ReportCard } from "./ReportCard";
import { CoverageCompanyDrawer, ResearcherDrawer } from "./TeamDrawers";

// The mock's four researcher hues first, then the categorical ramp; the palette has no ninth.
const SERIES = [
  { fill: "bg-u-direct", stroke: "stroke-u-direct" },
  { fill: "bg-u-adjacent", stroke: "stroke-u-adjacent" },
  { fill: "bg-u-inferred", stroke: "stroke-u-inferred" },
  { fill: "bg-u-signal", stroke: "stroke-u-signal" },
  { fill: "bg-u-chart-5", stroke: "stroke-u-chart-5" },
  { fill: "bg-u-chart-6", stroke: "stroke-u-chart-6" },
  { fill: "bg-u-chart-3", stroke: "stroke-u-chart-3" },
];
const TAIL = { fill: "bg-u-text3", stroke: "stroke-u-text3" };
const UNMAPPED = { fill: "bg-u-border-strong", stroke: "stroke-u-border-strong" };

const TABLE_COLUMNS = "grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,0.7fr)]";
const EYEBROW = "text-[9.5px] font-semibold uppercase tracking-[0.07em] text-u-text3";

type OpenDrawer = { kind: "researcher"; id: string } | { kind: "company"; id: string } | null;

/**
 * Who on the team is mapping the mandate, and how fully — staff-only, so it has its own read the
 * report's client-visible one never carries. Every figure is attributed by who filed the executive.
 */
export function ResearcherPerformanceCard({ projectId, progress }: { projectId: string; progress: ReportProgress }) {
  const [rangeKey, setRangeKey] = useState<TeamRangeKey>(DEFAULT_TEAM_RANGE);
  const [custom, setCustom] = useState<TeamRange>({ from: progress.kickoff, to: progress.asOf });
  const range = teamRangeOf(rangeKey, progress.asOf, custom);
  const { data: team, isPending, isError } = useQuery({
    queryKey: reportApi.TEAM_REPORT_KEY(projectId, range),
    queryFn: ({ signal }) => reportApi.getTeamPerformance(projectId, range, signal),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
  const [open, setOpen] = useState<OpenDrawer>(null);

  const rangeLabel = TEAM_RANGES.find((option) => option.key === rangeKey)?.label.toLowerCase() ?? "";
  const caption = team
    ? `share of the ${team.kpis.executivesInRange} executives filed ${rangeKey === "custom" ? `${formatShortDate(team.from)} – ${formatShortDate(team.to)}` : rangeLabel}, by researcher`
    : "executives filed, by researcher";

  return (
    <ReportCard
      title="Researcher performance"
      caption={caption}
      action={
        <RangePills
          active={rangeKey}
          onChange={setRangeKey}
          onReset={() => setRangeKey(DEFAULT_TEAM_RANGE)}
          custom={custom}
          onCustomChange={setCustom}
          bounds={{ min: progress.kickoff, max: progress.asOf }}
        />
      }
      note={
        team ? (
          <>
            Each executive counts for <b>whoever filed it</b>, and a company for whoever filed its <b>first</b>. Quality
            is what is on file — a contact, a verified one, a base salary — and a status is where someone stands now,
            not a step they converted through.
          </>
        ) : undefined
      }
    >
      {isError ? (
        <ChartEmpty>Researcher performance could not be loaded. Reload the page to try again.</ChartEmpty>
      ) : isPending ? (
        <div className="mt-[18px] h-[220px] animate-pulse rounded-[9px] bg-u-sunken" aria-label="Loading researcher performance" />
      ) : (
        <TeamBody team={team} progress={progress} onOpen={setOpen} />
      )}
      {team && open?.kind === "researcher" && (
        <ResearcherDrawer
          researcher={team.researchers.find((row) => row.userId === open.id) ?? null}
          days={team.days}
          onClose={() => setOpen(null)}
        />
      )}
      {team && open?.kind === "company" && (
        <CoverageCompanyDrawer
          company={team.companies.find((row) => row.triageCompanyId === open.id) ?? null}
          onClose={() => setOpen(null)}
        />
      )}
    </ReportCard>
  );
}

function TeamBody({
  team,
  progress,
  onOpen,
}: {
  team: TeamPerformance;
  progress: ReportProgress;
  onOpen: (drawer: OpenDrawer) => void;
}) {
  const seriesOf = new Map(team.researchers.map((row, index) => [row.userId, SERIES[index] ?? TAIL]));
  const { kpis } = team;
  const projection = projectCoverage(progress, "recent");
  const firstWeek = projection.lastWeek === 0;
  const remaining = Math.max(kpis.targetCompanies - kpis.coveredCompanies, 0);
  const lastGap = kpis.lastAddedAt ? daysBetween(kpis.lastAddedAt.slice(0, 10), progress.asOf) : null;

  const segments: DonutSegment[] = [
    ...team.coverage.map((share) => ({
      key: share.userId,
      label: share.name,
      value: share.companies,
      series: seriesOf.get(share.userId) ?? TAIL,
    })),
    ...(remaining > 0 ? [{ key: "unmapped", label: "Not yet mapped", value: remaining, series: UNMAPPED }] : []),
  ];

  return (
    <>
      <div className="mt-[18px] grid grid-cols-2 gap-3 lg:grid-cols-4">
        <TeamKpi label="Recent pace" value={`${kpis.rangePerWeek.toFixed(1)}/wk`} sub={`vs ${kpis.mandatePerWeek.toFixed(1)}/wk across the mandate`} />
        <TeamKpi
          label="Companies covered"
          value={`${kpis.coveredCompanies} / ${kpis.targetCompanies}`}
          sub={remaining === 0 ? "every company has an executive" : `${remaining} still to map`}
        />
        <TeamKpi
          label="Last new executive"
          value={lastGap === null ? "—" : lastGap === 0 ? "Today" : `${lastGap}d ago`}
          sub={kpis.lastAddedBy ? `${kpis.lastAddedBy} filed last` : "no executive mapped yet"}
        />
        <TeamKpi
          label="Projected completion"
          value={
            projection.remaining === 0
              ? "Complete"
              : firstWeek || projection.projectedDate === null
                ? "—"
                : formatProjectedDate(projection.projectedDate, progress.asOf)
          }
          sub={
            projection.remaining === 0
              ? "every company is covered"
              : firstWeek
                ? "needs a second week of pace"
                : projection.projectedDate === null
                  ? "no recent pace to project from"
                  : "at the last 3 weeks' pace"
          }
        />
      </div>

      <div className="mt-[18px] grid grid-cols-1 gap-5 md:grid-cols-[280px_minmax(0,1fr)]">
        <div className="rounded-[9px] border border-u-border p-4">
          <div className={EYEBROW}>Overall sourcing coverage</div>
          <CoverageDonut
            segments={segments}
            total={Math.max(kpis.targetCompanies, kpis.coveredCompanies)}
            centre={`${percent(kpis.coveredCompanies, kpis.targetCompanies)}%`}
            centreLabel="Mapped"
          />
        </div>
        <ResearcherTable team={team} seriesOf={seriesOf} onOpen={(id) => onOpen({ kind: "researcher", id })} />
      </div>

      <div className={cn(EYEBROW, "mt-[18px]")}>Company coverage status</div>
      {team.companies.length === 0 ? (
        <ChartEmpty>No company of the universe has an executive mapped yet.</ChartEmpty>
      ) : (
        <CompanyCoverageGrid
          companies={team.companies}
          total={team.companiesTotal}
          onOpen={(id) => onOpen({ kind: "company", id })}
        />
      )}
    </>
  );
}

function TeamKpi({ label, value, sub }: { label: string; value: string; sub: string }) {
  return (
    <div className="min-w-0 rounded-[9px] border border-u-border px-[15px] py-[13px]">
      <div className={EYEBROW}>{label}</div>
      <div className="mt-1.5 break-words font-u-num text-[21px] font-bold leading-[1.2]">{value}</div>
      <div className="mt-[3px] text-[11px] text-u-text3">{sub}</div>
    </div>
  );
}

function ResearcherTable({
  team,
  seriesOf,
  onOpen,
}: {
  team: TeamPerformance;
  seriesOf: Map<string, { fill: string }>;
  onOpen: (userId: string) => void;
}) {
  const most = Math.max(...team.researchers.map((row) => row.executives), 1);
  return (
    <div className="min-w-0 overflow-x-auto rounded-[9px] border border-u-border">
      <div className="min-w-[440px]">
        <div
          className={cn(
            "grid gap-2 border-b border-u-border bg-u-sunken px-3.5 py-[9px] text-[9.5px] font-semibold uppercase tracking-[0.06em] text-u-text3",
            TABLE_COLUMNS,
          )}
        >
          <span>Researcher</span>
          <span>Executives mapped</span>
          <span>Velocity</span>
          <span>Quality</span>
          <span className="text-right">Share</span>
        </div>
        {team.researchers.length === 0 && (
          <div className="px-3.5 py-4 text-xs text-u-text3">Nobody on the team has filed an executive yet.</div>
        )}
        {team.researchers.map((row) => (
          <button
            key={row.userId}
            type="button"
            onClick={() => onOpen(row.userId)}
            className={cn(
              "grid w-full items-center gap-2 border-t border-u-border px-3.5 py-[11px] text-left text-u-text transition first:border-t-0 hover:bg-u-raised",
              TABLE_COLUMNS,
            )}
          >
            <span className="flex min-w-0 items-center gap-2.5">
              <Avatar id={row.userId} name={row.name} src={row.avatarUrl} size="lg" />
              <span className="min-w-0">
                <span className="block truncate text-[12.5px] font-semibold">{row.name}</span>
                <span className="mt-px block truncate text-[10.5px] text-u-text3">{researcherMeta(row)}</span>
              </span>
            </span>
            <span>
              <span className="block font-u-num text-sm font-bold">{row.executives}</span>
              <span className="mt-1 block h-[5px] w-16 overflow-hidden rounded-[3px] bg-u-sunken">
                <span
                  className={cn("block h-full rounded-[3px]", seriesOf.get(row.userId)?.fill ?? TAIL.fill)}
                  style={{ width: `${(row.executives / most) * 100}%` }}
                />
              </span>
            </span>
            <span className="font-u-num text-xs font-medium text-u-text2">{row.perDay.toFixed(1)}/day</span>
            <QualityBadge researcher={row} />
            <span className="text-right font-u-num text-[12.5px] font-bold">{row.sharePct}%</span>
          </button>
        ))}
        <div className={cn("grid gap-2 border-t border-u-border-strong bg-u-sunken px-3.5 py-[11px] text-xs font-bold", TABLE_COLUMNS)}>
          <span>Total</span>
          <span>{team.kpis.executivesInRange} mapped</span>
          <span />
          <span />
          <span className="text-right">{team.kpis.executivesInRange > 0 ? "100%" : "—"}</span>
        </div>
      </div>
    </div>
  );
}

function QualityBadge({ researcher }: { researcher: Researcher }) {
  if (!researcher.quality) return <span className="text-xs text-u-text3">—</span>;
  const good = researcher.quality.level === "GOOD";
  return (
    <span
      title={`Contact ${researcher.quality.contactPct}% · verified ${researcher.quality.verifiedPct}% · comp ${researcher.quality.compPct}%`}
      className={cn(
        "w-fit rounded-full px-[9px] py-[3px] text-[10.5px] font-semibold",
        good ? "bg-u-direct-tint text-u-direct" : "bg-u-signal-tint text-u-signal",
      )}
    >
      {good ? "Good" : "Attention"}
    </span>
  );
}

function CompanyCoverageGrid({
  companies,
  total,
  onOpen,
}: {
  companies: CompanyCoverage[];
  total: number;
  onOpen: (id: string) => void;
}) {
  // No company carries a target of its own, so a bar is its executives against the most-mapped one.
  const most = Math.max(...companies.map((company) => company.executives), 1);
  return (
    <>
      <div className="mt-2.5 grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-2.5">
        {companies.map((company) => (
          <button
            key={company.triageCompanyId}
            type="button"
            onClick={() => onOpen(company.triageCompanyId)}
            className="rounded-lg border border-u-border px-[13px] py-[11px] text-left text-u-text transition hover:bg-u-raised"
          >
            <span className="flex justify-between gap-1.5 text-xs font-semibold">
              <span className="truncate">{company.name}</span>
              <span className="flex-none font-medium text-u-text3">{company.executives} execs</span>
            </span>
            <span className="mt-2 block h-[5px] w-full overflow-hidden rounded-[3px] bg-u-sunken">
              <span className="block h-full rounded-[3px] bg-u-direct" style={{ width: `${(company.executives / most) * 100}%` }} />
            </span>
          </button>
        ))}
      </div>
      {total > companies.length && (
        <div className="mt-2 text-[11px] text-u-text3">
          The {companies.length} most-mapped of {total} covered companies.
        </div>
      )}
    </>
  );
}

/** A projection past this year says its year — "9 May" alone would read as the coming May. */
function formatProjectedDate(isoDate: string, asOf: string): string {
  const short = formatShortDate(isoDate);
  return isoDate.slice(0, 4) === asOf.slice(0, 4) ? short : `${short} ${isoDate.slice(0, 4)}`;
}

function researcherMeta(row: Researcher): string {
  const role = row.role === "FORMER" ? `${roleLabel(row.role)} · ` : "";
  const companies = `${row.companies} ${row.companies === 1 ? "company" : "companies"}`;
  return `${role}${companies} · ${sinceLabel(row.lastAddedAt)}`;
}

function RangePills({
  active,
  onChange,
  onReset,
  custom,
  onCustomChange,
  bounds,
}: {
  active: TeamRangeKey;
  onChange: (key: TeamRangeKey) => void;
  onReset: () => void;
  custom: TeamRange;
  onCustomChange: (range: TeamRange) => void;
  bounds: { min: string; max: string };
}) {
  return (
    <div className="flex flex-col items-end gap-2">
      <div role="radiogroup" aria-label="Range" className="flex flex-wrap items-center gap-1.5">
        {TEAM_RANGES.map((option) => (
          <button
            key={option.key}
            type="button"
            role="radio"
            aria-checked={option.key === active}
            onClick={() => onChange(option.key)}
            className={cn(
              "rounded-[7px] border px-[11px] py-1.5 text-[11.5px] font-semibold transition",
              option.key === active
                ? "border-u-accent-ring bg-u-accent-tint text-u-accent"
                : "border-u-border-strong bg-transparent text-u-text2 hover:text-u-text",
            )}
          >
            {option.label}
          </button>
        ))}
        <button
          type="button"
          onClick={onReset}
          className="px-1 py-1.5 text-[11.5px] font-semibold text-u-text3 hover:text-u-text2"
        >
          Reset filters
        </button>
      </div>
      {active === "custom" && (
        <div className="flex items-center gap-2 text-[11.5px] text-u-text2">
          <label className="flex items-center gap-1.5">
            From
            <input
              type="date"
              value={custom.from ?? ""}
              min={bounds.min}
              max={custom.to ?? bounds.max}
              onChange={(event) => onCustomChange({ ...custom, from: event.target.value || undefined })}
              className="rounded-md border border-u-border-strong bg-u-surface px-2 py-1 font-u-num text-u-text"
            />
          </label>
          <label className="flex items-center gap-1.5">
            To
            <input
              type="date"
              value={custom.to ?? ""}
              min={custom.from ?? bounds.min}
              max={bounds.max}
              onChange={(event) => onCustomChange({ ...custom, to: event.target.value || undefined })}
              className="rounded-md border border-u-border-strong bg-u-surface px-2 py-1 font-u-num text-u-text"
            />
          </label>
        </div>
      )}
    </div>
  );
}
