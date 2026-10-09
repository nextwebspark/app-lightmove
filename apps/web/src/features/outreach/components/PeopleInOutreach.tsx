import { useQuery } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Skeleton } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { initials } from "../../../lib/format";
import * as runApi from "../api/runApi";
import type { OutreachOverview, OutreachRun } from "../api/runApi";
import {
  isLive,
  matchesFilter,
  RUN_FILTERS,
  RUN_STATES,
  runNoteOf,
  sendTimeOf,
  type RunFilter,
} from "../lib/runVocabulary";
import { useStopRun } from "../lib/useStopRun";
import { StopRunDialog } from "./StopRunDialog";

const ROW_GRID =
  "grid grid-cols-[minmax(240px,2.2fr)_minmax(150px,1.3fr)_110px_minmax(140px,1fr)_minmax(170px,1.3fr)_56px_90px] items-center gap-3";

/**
 * The Outreach page's counts and its People in outreach table (`Outreach.dc.html`), with the page's
 * sequence cards drawn between them as the mockup lays them out. A row opens the person's drawer; Stop
 * ends a live run, and a replied row whose status nobody has moved yet offers Set status, which opens
 * the drawer where the choice is made.
 */
export function PeopleInOutreach({
  projectId,
  onOpenCandidate,
  children,
}: {
  projectId: string;
  onOpenCandidate: (candidateId: string) => void;
  /** The sequence cards, which sit between the counts and the table. */
  children: ReactNode;
}) {
  const [filter, setFilter] = useState<RunFilter>("all");
  const overview = useQuery({
    queryKey: runApi.OUTREACH_PEOPLE_KEY(projectId),
    queryFn: ({ signal }) => runApi.getOutreachPeople(projectId, signal),
  });
  const stop = useStopRun(projectId);
  const [stopping, setStopping] = useState<OutreachRun | null>(null);

  const people = overview.data?.people ?? [];
  const hasPeople = (overview.data?.counts.enrolled ?? 0) > 0;
  const shown = people.filter((run) => matchesFilter(run, filter));

  // One shape whatever the read's state, so the sequence cards between the counts and the table are
  // never remounted when it settles.
  return (
    <>
      {overview.isPending && <Skeleton className="mb-[22px] h-[84px] w-full" />}
      {overview.data && hasPeople && <OutreachCounts overview={overview.data} />}
      <div className={cn(hasPeople && "mt-[22px]")}>{children}</div>
      {overview.isError && (
        <p role="alert" className="mt-[26px] text-[13px] text-u-text3">
          {messageFor(overview.error)}
        </p>
      )}
      {hasPeople && (
        <section className="mt-[26px]">
          <div className="mb-2.5 flex flex-wrap items-center gap-2.5">
            <h2 className="type-label text-u-text3">People in outreach</h2>
            <div role="group" aria-label="Show" className="ms-auto flex flex-wrap gap-1.5">
              {RUN_FILTERS.map((chip) => {
                const isOn = chip.key === filter;
                return (
                  <button
                    key={chip.key}
                    type="button"
                    aria-pressed={isOn}
                    onClick={() => setFilter(chip.key)}
                    className={cn(
                      "inline-flex items-center gap-1.5 rounded-full border px-[11px] py-[5px] font-mono text-[12px]",
                      isOn ? "border-u-accent bg-u-accent-tint text-u-text" : "border-u-border text-u-text2",
                    )}
                  >
                    {chip.label}
                    <span className="text-[11px] text-u-text3">
                      {people.filter((run) => matchesFilter(run, chip.key)).length}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="overflow-x-auto rounded-[8px] border border-u-border">
            <div role="table" aria-label="People in outreach" className="min-w-[1040px]">
              <div
                role="row"
                className={cn(
                  ROW_GRID,
                  "border-b border-u-border bg-u-raised px-3.5 py-[9px] font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-u-text3",
                )}
              >
                <span role="columnheader">Person</span>
                <span role="columnheader">Sequence</span>
                <span role="columnheader">Steps</span>
                <span role="columnheader">Next send</span>
                <span role="columnheader">Status</span>
                <span role="columnheader">From</span>
                <span role="columnheader" aria-label="Actions" />
              </div>
              {shown.length === 0 ? (
                <p className="px-3.5 py-4 text-[13px] text-u-text3">Nobody here.</p>
              ) : (
                shown.map((run) => (
                  <RunRow
                    key={run.id}
                    run={run}
                    isStopping={stop.isPending && stop.variables?.id === run.id}
                    onOpen={onOpenCandidate}
                    onStop={() => setStopping(run)}
                  />
                ))
              )}
            </div>
          </div>
          <p className="mx-0.5 mt-2.5 font-mono text-[11.5px] text-u-text3">
            The first email moves a person from Identified to Contacted. A reply stops their sequence and asks
            you where they stand. Uncava never guesses from what they wrote.
          </p>
        </section>
      )}
      <StopRunDialog
        open={stopping !== null}
        name={stopping?.fullName ?? null}
        pending={stop.isPending}
        onConfirm={() => stopping && stop.mutate(stopping, { onSettled: () => setStopping(null) })}
        onClose={() => setStopping(null)}
      />
    </>
  );
}

function OutreachCounts({ overview }: { overview: OutreachOverview }) {
  const { counts, nextSendAt } = overview;
  const answered = counts.replied + counts.booked;
  const replyShare = counts.reached > 0 ? Math.round((answered / counts.reached) * 100) : 0;
  const tiles = [
    { label: "Enrolled", value: counts.enrolled, sub: "on this position" },
    { label: "Emails sent", value: counts.emailsSent, sub: `${counts.reached} people reached` },
    { label: "Replied or booked", value: answered, sub: `${replyShare}% of people reached`, isGood: true },
    { label: "In flight", value: counts.inFlight, sub: nextSendAt ? `next send ${sendTimeOf(nextSendAt)}` : "nothing due" },
    { label: "Bounced", value: counts.bounced, sub: "fix the address and re-add" },
  ];
  return (
    <div aria-label="Counts" className="grid gap-2.5 [grid-template-columns:repeat(auto-fit,minmax(150px,1fr))]">
      {tiles.map((tile) => (
        <div
          key={tile.label}
          className={cn(
            "rounded-[10px] border border-u-border px-3.5 py-3",
            tile.isGood ? "bg-u-direct-tint" : "bg-u-surface",
          )}
        >
          <div className="type-label text-u-text3">{tile.label}</div>
          <div className={cn("mt-1.5 text-[22px] font-semibold tabular-nums", tile.isGood && "text-u-direct")}>
            {tile.value}
          </div>
          <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">{tile.sub}</div>
        </div>
      ))}
    </div>
  );
}

function RunRow({
  run,
  isStopping,
  onOpen,
  onStop,
}: {
  run: OutreachRun;
  isStopping: boolean;
  onOpen: (candidateId: string) => void;
  onStop: () => void;
}) {
  const candidateId = run.candidateId;
  const needsStatus = run.status === "REPLIED" && run.candidateStatus === "contacted" && candidateId !== null;
  const name = run.fullName ?? "Someone no longer on this position";
  const sub = [run.title, run.companyName].filter(Boolean).join(" · ");
  const state = RUN_STATES[run.status];

  return (
    <div
      role="row"
      tabIndex={candidateId ? 0 : undefined}
      onClick={candidateId ? () => onOpen(candidateId) : undefined}
      onKeyDown={
        candidateId
          ? (event) => {
              if (event.target !== event.currentTarget) return;
              if (event.key !== "Enter" && event.key !== " ") return;
              event.preventDefault();
              onOpen(candidateId);
            }
          : undefined
      }
      className={cn(
        ROW_GRID,
        "border-b border-u-border px-3.5 py-2.5 last:border-b-0",
        candidateId &&
          "cursor-pointer hover:bg-u-raised focus-visible:outline focus-visible:-outline-offset-2 focus-visible:outline-u-accent",
      )}
    >
      <span role="cell" className="flex min-w-0 items-center gap-2.5">
        <span
          aria-hidden="true"
          className="grid size-7 flex-none place-items-center rounded-full bg-u-accent-tint font-mono text-[10px] font-semibold text-u-accent"
        >
          {initials(name)}
        </span>
        <span className="min-w-0">
          {candidateId ? (
            <button
              type="button"
              // The row is the tab stop; the name stays a button for the pointer and the screen reader.
              tabIndex={-1}
              onClick={(event) => {
                event.stopPropagation();
                onOpen(candidateId);
              }}
              className="block max-w-full truncate text-start text-[13px] font-medium hover:underline"
            >
              {name}
            </button>
          ) : (
            <span className="block truncate text-[13px] font-medium text-u-text2">{name}</span>
          )}
          {sub && <span className="block truncate font-mono text-[11.5px] text-u-text3">{sub}</span>}
        </span>
      </span>
      <span role="cell" className="truncate text-[12.5px] text-u-text2">
        {run.sequenceName ?? "—"}
      </span>
      <span role="cell">
        <StepDots run={run} />
      </span>
      <span role="cell" className={cn("font-mono text-[12px]", isLive(run) ? "text-u-text" : "text-u-text3")}>
        {isLive(run) && run.nextSendAt ? sendTimeOf(run.nextSendAt) : "—"}
      </span>
      <span role="cell" className="flex min-w-0 items-center gap-2">
        <span className={cn("flex-none rounded-full px-2 py-[2px] font-mono text-[10.5px] font-medium uppercase", state.className)}>
          {state.label}
        </span>
        <span className="truncate font-mono text-[11px] text-u-text3">{runNoteOf(run)}</span>
      </span>
      <span role="cell">
        <span
          title={run.senderName ?? undefined}
          className="grid size-6 place-items-center rounded-full bg-u-signal-tint font-mono text-[9.5px] font-semibold text-u-signal"
        >
          {initials(run.senderName ?? "?")}
        </span>
      </span>
      <span role="cell" className="flex justify-end">
        {isLive(run) && (
          <button
            type="button"
            title="Stop this person's sequence — nothing more is sent"
            disabled={isStopping}
            onClick={(event) => {
              event.stopPropagation();
              onStop();
            }}
            className="rounded-[6px] border border-u-border px-[9px] py-1 text-[11.5px] font-medium text-u-text2 hover:border-u-offlimits hover:text-u-offlimits disabled:opacity-60"
          >
            Stop
          </button>
        )}
        {needsStatus && (
          <button
            type="button"
            title="They replied — record where they stand"
            onClick={(event) => {
              event.stopPropagation();
              onOpen(candidateId);
            }}
            className="rounded-[6px] bg-u-direct-tint px-[9px] py-1 text-[11.5px] font-semibold text-u-direct"
          >
            Set status
          </button>
        )}
      </span>
    </div>
  );
}

/** One dot per step: filled once sent, ringed for the next one due, greyed once the run has ended. */
export function StepDots({ run }: { run: Pick<OutreachRun, "stepCount" | "sentCount" | "status"> }) {
  const live = isLive(run);
  return (
    <span className="flex items-center gap-[5px]" title={`${run.sentCount} of ${run.stepCount} sent`}>
      {Array.from({ length: run.stepCount }, (_, step) => (
        <span
          key={step}
          aria-hidden="true"
          className={cn(
            "size-[9px] rounded-full",
            step < run.sentCount
              ? "bg-u-accent"
              : live && step === run.sentCount
                ? "border-2 border-u-accent"
                : live
                  ? "border-[1.5px] border-u-border"
                  : "bg-u-border",
          )}
        />
      ))}
      <span className="sr-only">{`${run.sentCount} of ${run.stepCount} sent`}</span>
    </span>
  );
}
