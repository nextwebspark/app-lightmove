import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { SourcingCompanyOutcome, SourcingRun } from "../api/sourcingApi";
import { isSourcingInProgress } from "../api/sourcingApi";
import { summaryOf } from "../lib/sourcingSummary";

/** How each company's line reads, and the tone it takes. */
const OUTCOME_LINES: Record<SourcingCompanyOutcome["outcome"], { text: (outcome: SourcingCompanyOutcome) => string; tone: "good" | "muted" | "bad" }> = {
  FILED: { text: (o) => `Filed ${o.filed}${o.skipped > 0 ? `, ${o.skipped} already mapped` : ""}`, tone: "good" },
  NO_LINKEDIN_PAGE: { text: () => "No LinkedIn page on this company", tone: "muted" },
  NO_HITS: { text: () => "Nobody with a fitting title in the region — check the company's LinkedIn link", tone: "muted" },
  ALL_ALREADY_MAPPED: { text: () => "Everyone found is already mapped", tone: "muted" },
  NOTHING_FIT: { text: () => "Nobody fit the role", tone: "muted" },
  FAILED: { text: () => "Could not be searched", tone: "bad" },
  NOT_REACHED: { text: () => "Not reached before the run's deadline", tone: "bad" },
};

/** "10 of 27 matched" when the per-company cap cut the search short, nothing otherwise. */
function cappedLine(outcome: SourcingCompanyOutcome): string | null {
  if (outcome.matched == null || outcome.matched <= outcome.seen) return null;
  return `${outcome.seen} of ${outcome.matched} matched`;
}

/** "12 profiles bought, 8 reused" — what the run cost, since a reused profile is not billed again. */
function profilesLine(run: SourcingRun): string {
  const bought = `${run.vendorHits} ${run.vendorHits === 1 ? "profile" : "profiles"} bought`;
  return run.cachedHits > 0 ? `${bought}, ${run.cachedHits} reused` : bought;
}

/** The strip under the toolbar that shows a Find executives run; per-company lines sit behind "Show details". */
export function ExecutiveSourcingBanner({ run, onDismiss }: { run: SourcingRun; onDismiss: () => void }) {
  const [showDetails, setShowDetails] = useState(false);
  const running = isSourcingInProgress(run);
  const failed = run.status === "FAILED";
  const searching = running ? run.companyNames[run.companiesDone] : undefined;
  const percent = run.companiesTotal === 0 ? 0 : Math.round((run.companiesDone / run.companiesTotal) * 100);

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "flex flex-none flex-col gap-2 border-b px-3 py-2 sm:px-5",
        failed ? "border-u-offlimits bg-u-offlimits-tint" : "border-u-border bg-u-accent-tint",
      )}
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className={cn("grid size-5 flex-none place-items-center", failed ? "text-u-offlimits" : "text-u-accent")}>
          <Icon d={failed ? ICONS.warning : ICONS.search} size={14} className={cn(running && "animate-pulse")} />
        </span>
        <span className="min-w-0 flex-1 font-sans text-body text-u-text">
          {running && (
            <>
              <span className="font-medium">Finding executives</span>
              <span className="text-u-text3"> · {run.companiesDone}/{run.companiesTotal} companies</span>
              {searching && <span className="text-u-text2"> · Searching {searching}…</span>}
              {run.executivesFiled > 0 && (
                <span className="text-u-text2"> · {run.executivesFiled} filed so far</span>
              )}
            </>
          )}
          {run.status === "COMPLETED" && (
            <>
              <span className="font-medium">{summaryOf(run)}</span>
              <span className="text-u-text3"> · {profilesLine(run)}</span>
            </>
          )}
          {failed && (
            <span className="font-medium">
              Find executives stopped{run.error ? ` — ${run.error}` : ""}
              {run.executivesFiled > 0 ? ` · ${run.executivesFiled} filed before it did` : ""}
            </span>
          )}
        </span>
        {run.outcomes.length > 0 && (
          <button
            type="button"
            onClick={() => setShowDetails((open) => !open)}
            aria-expanded={showDetails}
            className="font-sans text-note font-medium text-u-text2 underline-offset-2 hover:text-u-text hover:underline"
          >
            {showDetails ? "Hide details" : "Show details"}
          </button>
        )}
        {!running && (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss"
            className="grid size-7 flex-none place-items-center rounded-[6px] text-u-text3 transition hover:bg-u-surface hover:text-u-text"
          >
            <Icon d={ICONS.close} size={13} />
          </button>
        )}
      </div>

      {running && (
        <div
          role="progressbar"
          aria-label="Companies searched"
          aria-valuemin={0}
          aria-valuemax={run.companiesTotal}
          aria-valuenow={run.companiesDone}
          className="h-1 w-full overflow-hidden rounded-full bg-u-border"
        >
          <div className="h-full rounded-full bg-u-accent transition-[width] duration-500" style={{ width: `${percent}%` }} />
        </div>
      )}

      {showDetails && run.searchedFor && (
        <p className="font-sans text-note text-u-text3">
          Titles with {run.searchedFor.seniorityWords.join(" / ")}
          {run.searchedFor.functionWords.length > 0 && <> and {run.searchedFor.functionWords.join(" / ")}</>}
          {run.searchedFor.excludedWords.length > 0 && <>, without {run.searchedFor.excludedWords.join(" / ")}</>}
        </p>
      )}

      {showDetails && (
        <ul className="flex flex-col gap-1 font-sans text-note">
          {run.outcomes.map((outcome) => {
            const line = OUTCOME_LINES[outcome.outcome];
            const capped = cappedLine(outcome);
            return (
              <li key={outcome.triageCompanyId} className="flex flex-wrap items-baseline gap-x-2">
                <span className="font-medium text-u-text">{outcome.companyName}</span>
                <span
                  className={cn(
                    line.tone === "good" && "text-u-accent",
                    line.tone === "muted" && "text-u-text3",
                    line.tone === "bad" && "text-u-offlimits",
                  )}
                >
                  {line.text(outcome)}
                </span>
                {capped && <span className="text-u-text3">({capped})</span>}
                {outcome.picks.length > 0 && (
                  <span className="text-u-text3">
                    — {outcome.picks.map((pick) => pick.name).join(", ")}
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
