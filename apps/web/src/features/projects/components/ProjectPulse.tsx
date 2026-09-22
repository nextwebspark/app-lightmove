import { formatDate } from "../../../lib/format";
import type { Project } from "../api/types";
import { PhaseBar } from "./PhaseBar";

/**
 * Where a mandate has got to and how much time it has left — the drawer's answer to "is this one
 * fine?", above the detail that explains it.
 */
export function PhasePipeline({ project }: { project: Project }) {
  const { progress } = project;
  const engaging = progress.activePhase === "ENGAGE";
  const remaining = progress.daysRemaining;

  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <SectionLabel>Phase pipeline</SectionLabel>
        <span
          className={`font-mono text-[11px] font-semibold ${engaging ? "text-amber" : "text-green"}`}
        >
          {engaging ? "Engage · active" : "Map · active"}
        </span>
      </div>

      <PhaseBar progress={progress} />

      <div className="mt-2 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 font-mono text-[11.5px] text-text3">
        <span>Started {formatDate(project.startDate)}</span>
        <span className={progress.mappingComplete ? "font-semibold text-green" : undefined}>
          {progress.mappingComplete
            ? "Map complete ✓"
            : `Map ${formatDate(project.mappingTargetDate)}`}
        </span>
        {project.projectType === "EXECUTIVE_SEARCH" && (
          <span>Shortlist {formatDate(project.shortlistTargetDate)}</span>
        )}
      </div>

      {remaining !== null && (
        <p
          className={`mt-1.5 font-mono text-[11.5px] font-semibold ${
            remaining < 0 ? "text-red" : remaining <= 14 ? "text-amber" : "text-text3"
          }`}
        >
          {remaining < 0
            ? `${Math.abs(remaining)} day${Math.abs(remaining) === 1 ? "" : "s"} past target`
            : `${remaining} day${remaining === 1 ? "" : "s"} remaining`}
        </p>
      )}
    </div>
  );
}

/**
 * Why a mandate reads as it does, in the one sentence its lead would say. Shown only when something
 * is off: a notice on every mandate is a notice nobody reads.
 */
export function PaceNotice({ project }: { project: Project }) {
  if (project.health !== "RISK" && project.health !== "OFF") return null;

  const { progress } = project;
  const alarming = project.health === "OFF";
  const sentence =
    progress.activePhase === "ENGAGE"
      ? `Engagement pace is below target. ${progress.candidatesEngaged} of ${progress.candidatesMapped} executives contacted.`
      : `Mapping pace is below target. ${progress.companiesResearched} of ${progress.universeCompanies} companies researched.`;

  return (
    <div
      role="status"
      className={`mt-3 rounded-r-lg border-l-[3px] px-3 py-2.5 text-[12.5px] leading-snug ${
        alarming ? "border-red bg-red-dim text-text2" : "border-amber bg-amber-dim text-text2"
      }`}
    >
      {sentence}
    </div>
  );
}

function SectionLabel({ children }: { children: string }) {
  return (
    <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
      {children}
    </span>
  );
}
