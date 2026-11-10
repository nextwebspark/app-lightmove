import { useQuery, useQueryClient } from "@tanstack/react-query";
import { type ComponentProps, useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import type { Ranking } from "../api/types";
import { RankingTable } from "../components/RankingTable";
import { CompetencyWeights, GroupWeighting } from "../components/RankingWeighting";
import { ReportPanel } from "../components/ReportCard";
import { ReportGap } from "../components/ReportGap";
import { Figure, ReportFinding, ReportSection } from "../components/ReportSection";
import { SaveScenarioDialog } from "../components/SaveScenarioDialog";
import { ShortlistComparison } from "../components/ShortlistComparison";
import { leaderFlips, rankRows, rankStandings, type RankedRow } from "../lib/ranking";
import { insightOf, sensitivityOf, splitLabel } from "../lib/rankingWording";
import { useRankingScenario } from "../lib/useRankingScenario";

const MAX_COMPARED = 2;
const TWO_COLUMNS = "grid items-start gap-x-4 @min-[900px]:grid-cols-[minmax(0,1fr)_296px]";
type PoolFilter = "all" | "reviewed";

/**
 * The Ranking chapter: the position's assessed candidates in order under the weighting in force, the agreed
 * baseline beside it, and two of them compared competency by competency. Staff-only, with its own read.
 */
export function RankingSection({ project }: { project: Project }) {
  const { data: ranking, isPending, isError } = useQuery({
    queryKey: reportApi.RANKING_KEY(project.id),
    queryFn: ({ signal }) => reportApi.getRanking(project.id, signal),
    // An assessment saved in the drawer a moment ago must be in the order on the next visit.
    staleTime: 0,
  });

  if (isPending) return <LinesSkeleton lines={6} />;
  if (isError) {
    return (
      <ReportGap icon={ICONS.lock} title="Couldn't load the ranking">
        <div className="rounded-lg bg-u-sunken px-4 py-3.5 text-note text-u-text2">
          You may no longer have access to this position, or the request failed. Reload the page, and ask the lead if it
          keeps happening.
        </div>
      </ReportGap>
    );
  }
  return <RankingBody project={project} ranking={ranking} />;
}

function RankingBody({ project, ranking }: { project: Project; ranking: Ranking }) {
  const queryClient = useQueryClient();
  const scenario = useRankingScenario(project.id, ranking);
  const [filter, setFilter] = useState<PoolFilter>("all");
  const [hideOutOfProcess, setHideOutOfProcess] = useState(false);
  const [chosenIds, setChosenIds] = useState<string[] | null>(null);
  const [openCandidateId, setOpenCandidateId] = useState<string | null>(null);
  const [isSaveOpen, setSaveOpen] = useState(false);

  if (ranking.criteria.length === 0) {
    return (
      <Shell>
        <ReportGap icon={ICONS.team} title="The brief names no competencies yet">
          <p className="text-body text-u-text2">
            Candidates are ranked on their assessments against the brief's competencies.{" "}
            <Link to={`/projects/${project.id}?step=assessment`} className="underline underline-offset-2">
              Add them in Assessment Criteria
            </Link>
            .
          </p>
        </ReportGap>
      </Shell>
    );
  }

  const pool = ranking.candidates.filter(
    (candidate) =>
      (filter === "all" || candidate.assessment.status === "reviewed") && !(hideOutOfProcess && candidate.outOfProcess),
  );
  const rows = rankRows(pool, ranking.criteria, scenario.baseline, scenario.weighting);
  const selectedIds = (chosenIds ?? rows.slice(0, MAX_COMPARED).map((row) => row.candidate.candidateId)).filter((id) =>
    rows.some((row) => row.candidate.candidateId === id),
  );
  const toggle = (candidateId: string) => {
    const next = selectedIds.includes(candidateId)
      ? selectedIds.filter((id) => id !== candidateId)
      : [...selectedIds, candidateId].slice(-MAX_COMPARED);
    setChosenIds(next);
  };
  const pair = rows.filter((row) => selectedIds.includes(row.candidate.candidateId));
  const standings = rankStandings(pool, ranking.criteria, scenario.weighting);
  const insight = insightOf(rows, ranking.criteria, scenario.baseline, scenario.weighting, scenario.atBaseline);
  const sensitivity = sensitivityOf(leaderFlips(standings, ranking.criteria), rows[0]?.candidate);
  // Saving is offered for a change, and for a saved scenario whose rationale the team may want to revisit.
  const canSave = (scenario.isDirty || ranking.scenario !== null) && scenario.offHundred.length === 0;

  return (
    <Shell
      action={
        <>
          <Button variant="secondary" size="sm" onClick={scenario.reset} disabled={scenario.atBaseline}>
            <Icon d={ICONS.reset} size={14} className="text-u-text2" />
            Reset entire scenario
          </Button>
          <Button size="sm" onClick={() => setSaveOpen(true)} disabled={!canSave}>
            Save scenario
          </Button>
        </>
      }
    >
      <div className={TWO_COLUMNS}>
        <div className="flex min-w-0 flex-col">
          {insight && (
            <ReportFinding
              label={scenario.atBaseline ? "Agreed baseline" : "Exploration scenario"}
              aside={
                insight.lead && (
                  <span className="rounded-full bg-u-inferred-tint px-2.5 py-0.5 text-meta font-medium text-u-inferred">
                    {insight.lead}
                  </span>
                )
              }
              className="mt-4"
            >
              <Figure>{insight.title}</Figure>. {insight.text}
            </ReportFinding>
          )}
          <ScenarioStatus ranking={ranking} scenario={scenario} />

          {ranking.candidates.length === 0 ? (
            <ReportPanel>
              <p className="text-body text-u-text2">
                {ranking.mappedCount} mapped · none assessed yet. Score candidates in their drawer's Assessment tab to
                rank them here.
              </p>
            </ReportPanel>
          ) : (
            <RankingTable
              projectId={project.id}
              rows={rows}
              selectedIds={selectedIds}
              notAssessedCount={ranking.notAssessedCount}
              onToggle={toggle}
              onClearSelection={() => setChosenIds([])}
              onOpen={setOpenCandidateId}
              filters={
                <PoolFilters
                  filter={filter}
                  onFilter={setFilter}
                  hideOutOfProcess={hideOutOfProcess}
                  onHideOutOfProcess={setHideOutOfProcess}
                />
              }
            />
          )}
          {ranking.truncated && (
            <p className="mt-3 text-meta text-u-text3">
              Only the {ranking.candidates.length} best-scored at the baseline are listed.
            </p>
          )}

          {pair.length === MAX_COMPARED ? (
            <ShortlistComparison
              pair={pair as [RankedRow, RankedRow]}
              criteria={ranking.criteria}
              baseline={scenario.baseline}
              scenario={scenario.weighting}
              atBaseline={scenario.atBaseline}
              sensitivity={sensitivity}
              onOpenAssessment={setOpenCandidateId}
            />
          ) : (
            <ReportPanel dashed>
              <p className="text-body text-u-text2">Tick two candidates in the ranking to compare them side by side.</p>
            </ReportPanel>
          )}
        </div>

        <aside aria-label="Scenario weighting" className="min-w-0">
          <GroupWeighting
            criteria={ranking.criteria}
            baseline={scenario.baseline}
            weighting={scenario.weighting}
            onChange={scenario.change}
          />
          <CompetencyWeights criteria={ranking.criteria} weighting={scenario.weighting} onChange={scenario.change} />
        </aside>
      </div>

      <div className="mt-5 flex flex-wrap justify-between gap-x-4 gap-y-2 text-meta text-u-text3">
        <span>Decision support, not an automated hiring decision</span>
        <span>{scenario.atBaseline ? "Showing the agreed baseline" : "Agreed baseline unchanged"}</span>
      </div>

      <SaveScenarioDialog
        open={isSaveOpen}
        criteria={ranking.criteria}
        baseline={scenario.baseline}
        weighting={scenario.weighting}
        atBaseline={scenario.atBaseline}
        rationale={scenario.rationale}
        saving={scenario.isSaving}
        onRationale={scenario.setRationale}
        onCancel={() => setSaveOpen(false)}
        onSave={() => scenario.save(() => setSaveOpen(false))}
      />

      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        focusTab="assessment"
        onClose={() => setOpenCandidateId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: reportApi.RANKING_KEY(project.id) })}
      />
    </Shell>
  );
}

type ScenarioState = ReturnType<typeof useRankingScenario>;

function Shell({ children, ...section }: Omit<ComponentProps<typeof ReportSection>, "question" | "lede">) {
  return (
    <div className="@container">
      <ReportSection
        question="Candidate ranking"
        lede="Rank under current priorities. Compare the tradeoffs behind your shortlist."
        {...section}
      >
        {children}
      </ReportSection>
    </div>
  );
}

function ScenarioStatus({ ranking, scenario }: { ranking: Ranking; scenario: ScenarioState }) {
  const saved = ranking.scenario;
  return (
    <div className="mt-3 flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-note text-u-text3">
        {scenario.isDirty && (
          <span className="rounded-full bg-u-signal-tint px-2.5 py-0.5 text-meta font-medium text-u-signal">
            Unsaved changes
          </span>
        )}
        <span>
          {scenario.atBaseline
            ? `${splitLabel(scenario.baseline.share)} · the brief's own weights`
            : `Baseline ${splitLabel(scenario.baseline.share)} · assessment scores unchanged`}
        </span>
        {saved && !scenario.isDirty && saved.updatedByName && <span>Saved by {saved.updatedByName}</span>}
        {saved?.rationale && !scenario.isDirty && <span className="italic">“{saved.rationale}”</span>}
      </div>
      {saved?.briefChangedSinceSaved && (
        <p className="text-note text-u-signal">
          The brief's competencies changed since this scenario was saved
          {saved.droppedCriteria.length > 0 && ` — it no longer weighs ${saved.droppedCriteria.join(", ")}`}. New
          competencies take the brief's weight.
        </p>
      )}
      {scenario.offHundred.length > 0 && (
        <p role="alert" className="text-note text-u-offlimits">
          Each group you change must total 100% before the scenario can be saved.
        </p>
      )}
      {scenario.isStale && (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-md bg-u-signal-tint px-3.5 py-2.5 text-note">
          <span className="flex-1">Someone saved this scenario after you opened it.</span>
          <Button size="xs" variant="secondary" onClick={() => void scenario.reloadKeepingMine()}>
            Reload, keeping mine
          </Button>
        </div>
      )}
    </div>
  );
}

function PoolFilters({
  filter,
  onFilter,
  hideOutOfProcess,
  onHideOutOfProcess,
}: {
  filter: PoolFilter;
  onFilter: (filter: PoolFilter) => void;
  hideOutOfProcess: boolean;
  onHideOutOfProcess: (hide: boolean) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {(
        [
          ["all", "All assessed"],
          ["reviewed", "Reviewed only"],
        ] as const
      ).map(([value, label]) => (
        <button
          key={value}
          type="button"
          aria-pressed={filter === value}
          onClick={() => onFilter(value)}
          className={
            filter === value
              ? "rounded-full bg-u-inferred-tint px-3 py-1 text-note font-medium text-u-inferred"
              : "rounded-full bg-u-raised px-3 py-1 text-note text-u-text2 transition hover:text-u-text"
          }
        >
          {label}
        </button>
      ))}
      <label className="ml-1 flex items-center gap-2 text-note text-u-text2">
        <input
          type="checkbox"
          checked={hideOutOfProcess}
          onChange={(event) => onHideOutOfProcess(event.target.checked)}
          className="size-3.5 accent-u-accent"
        />
        Hide out of process
      </label>
    </div>
  );
}
