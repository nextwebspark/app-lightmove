import { useEffect, useState, type ReactNode } from "react";
import { cn } from "../../../lib/cn";
import type { SourcingRun } from "../api/sourcingApi";
import { consideredOf, secondsLeftOf, timeLeftLine } from "../lib/searchProgress";
import { peopleOf } from "../lib/sourcingSummary";

/** What the panel is doing now, in the order it happens. */
export type SearchPhase =
  | { kind: "drafting"; step: string }
  | { kind: "draftFailed"; onRetry: () => void }
  | { kind: "counting" }
  | { kind: "searching"; run: SourcingRun; parallelism: number; stopping: boolean; onStop: () => void }
  | { kind: "found"; fits: number; more: number }
  | { kind: "ready"; perCompany: number };

/**
 * The one line that says what is happening, always in the same place above the companies: the level tabs on the
 * left once people are found, and on the right the drafting, the free counting, the search's progress with time
 * left and Stop, or what was found. A thin bar along its foot fills as companies finish.
 */
export function SearchStatus({ phase, tabs }: { phase: SearchPhase; tabs: ReactNode }) {
  const busy = phase.kind === "drafting" || phase.kind === "counting" || phase.kind === "searching";
  const share =
    phase.kind === "searching" && phase.run.companiesTotal > 0
      ? Math.round((phase.run.companiesDone / phase.run.companiesTotal) * 100)
      : 0;
  return (
    <div className="relative flex flex-none flex-wrap items-end gap-x-0.5 border-b border-u-border px-[18px] pt-2">
      {tabs}
      <span className="flex-1" />
      <div role="status" aria-live="polite" className="flex min-h-[34px] items-center gap-2 pb-2 text-note">
        {busy && (
          <span className="size-[11px] flex-none animate-spin rounded-full border-2 border-u-border border-t-u-accent" />
        )}
        <PhaseLine phase={phase} />
      </div>
      {phase.kind === "searching" && (
        <span className="absolute inset-x-0 -bottom-px h-0.5 bg-u-sunken">
          <span className="block h-full bg-u-accent-solid transition-[width] duration-500" style={{ width: `${share}%` }} />
        </span>
      )}
    </div>
  );
}

function PhaseLine({ phase }: { phase: SearchPhase }) {
  const elapsed = useElapsedSeconds(phase.kind);
  const now = useClock(phase.kind === "searching");
  switch (phase.kind) {
    case "drafting":
      return <span className="font-medium text-u-text2">{phase.step}</span>;
    case "draftFailed":
      return (
        <>
          <span className="font-medium text-u-signal">Couldn&apos;t draft the search from the brief</span>
          <button
            type="button"
            onClick={phase.onRetry}
            className="ml-1 h-6 rounded-md border border-u-border-strong px-2 text-note font-medium hover:bg-u-raised"
          >
            Try again
          </button>
        </>
      );
    case "counting":
      return (
        <span className="text-u-text2">
          <span className="font-medium">Counting who matches at each company</span>
          <span className="text-u-text3"> · free, nothing is bought</span>
          {elapsed >= 3 && <span className="font-mono tabular-nums text-u-text3"> · {elapsed}s</span>}
          {elapsed >= 20 && <span className="text-u-text3"> · still working</span>}
        </span>
      );
    case "searching": {
      const { run } = phase;
      const considered = consideredOf(run);
      return (
        <>
          <span className="text-u-text2">
            <span className="font-medium text-u-text">
              Searching · {run.companiesDone} of {run.companiesTotal} done
            </span>
            <span className="font-mono text-u-text3">
              {" "}
              · {peopleOf(run.suggested)} fit so far
              {considered > 0 && ` · ${considered} looked at`}
            </span>
            <span className="text-u-text3"> · {timeLeftLine(secondsLeftOf(run, phase.parallelism, now))}</span>
          </span>
          <button
            type="button"
            onClick={phase.onStop}
            disabled={phase.stopping}
            title="Companies already being searched finish; the rest are left"
            className={cn(
              "ml-1 h-6 rounded-md border border-u-border-strong px-2 text-note font-medium hover:bg-u-raised",
              "disabled:opacity-50",
            )}
          >
            {phase.stopping ? "Stopping…" : "Stop"}
          </button>
        </>
      );
    }
    case "found":
      return (
        <span className="font-mono text-u-text3">
          {phase.fits} fit the role
          {phase.more > 0 && ` · ${phase.more} more found`}
        </span>
      );
    case "ready":
      return (
        <span className="font-mono text-u-text3">
          Counting is free · Search buys at most {phase.perCompany} profiles a company
        </span>
      );
  }
}

/** Seconds since the panel entered this phase. */
function useElapsedSeconds(phaseKind: SearchPhase["kind"]): number {
  const [since, setSince] = useState(() => ({ kind: phaseKind, at: Date.now() }));
  const [now, setNow] = useState(() => Date.now());
  if (since.kind !== phaseKind) setSince({ kind: phaseKind, at: Date.now() });
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  return Math.max(0, Math.floor((now - since.at) / 1000));
}

function useClock(ticking: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!ticking) return;
    const timer = window.setInterval(() => setNow(Date.now()), 5_000);
    return () => window.clearInterval(timer);
  }, [ticking]);
  return now;
}
