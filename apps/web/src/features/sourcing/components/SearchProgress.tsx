import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { SourcingRun } from "../api/sourcingApi";
import { companiesOf, peopleOf } from "../lib/sourcingSummary";

/** The search in progress: how far it has got, each company as done, searching or waiting, and Stop. */
export function SearchProgress({ run, onStop, stopping }: { run: SourcingRun; onStop: () => void; stopping: boolean }) {
  const done = new Map(run.outcomes.map((outcome) => [outcome.triageCompanyId, outcome]));
  const firstWaiting = run.companies.find((company) => !done.has(company.triageCompanyId));
  const share = run.companiesTotal === 0 ? 0 : Math.round((run.companiesDone / run.companiesTotal) * 100);
  return (
    <div role="status" className="mt-3 flex-none border-y border-u-border">
      <div className="flex items-center gap-3 px-[18px] py-2.5">
        <span className="size-3.5 flex-none animate-spin rounded-full border-2 border-u-border border-t-u-accent" />
        <span className="text-body font-medium text-u-text">Searching {companiesOf(run.companiesTotal)}</span>
        <span className="font-mono text-note text-u-text3">
          {run.companiesDone} done · {peopleOf(run.suggested)} found so far
        </span>
        <span className="h-1 flex-1 overflow-hidden rounded-full bg-u-sunken">
          <span className="block h-full bg-u-accent-solid" style={{ width: `${share}%` }} />
        </span>
        <button
          type="button"
          onClick={onStop}
          disabled={stopping}
          className="h-7 rounded-md border border-u-border-strong px-2.5 text-note font-medium hover:bg-u-raised disabled:opacity-50"
        >
          {stopping ? "Stopping…" : "Stop"}
        </button>
      </div>
      <div className="flex flex-wrap gap-1.5 border-t border-u-border px-[18px] py-2">
        {run.companies.map((company) => {
          const outcome = done.get(company.triageCompanyId);
          const active = !outcome && company.triageCompanyId === firstWaiting?.triageCompanyId;
          return (
            <span
              key={company.triageCompanyId}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full border border-u-border px-2.5 py-[3px] text-note font-medium",
                outcome ? "text-u-text" : active ? "bg-u-accent-tint text-u-text" : "text-u-text3",
              )}
            >
              {outcome && <Icon d={ICONS.check} size={11} className="text-u-direct" />}
              {company.companyName}
              <span className="font-mono text-meta text-u-text3">
                {outcome ? `${outcome.fits} fit` : active ? "searching" : "waiting"}
              </span>
            </span>
          );
        })}
      </div>
    </div>
  );
}
