import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { FoldBody } from "../../../components/ui/FoldBody";
import { cn } from "../../../lib/cn";
import type { CandidateStatus } from "../../candidates/api/types";
import * as runApi from "../api/runApi";
import type { OutreachRun, OutreachStep, RunStatus } from "../api/runApi";
import { isLive, RUN_STATES, sendTimeOf, STOP_NOTES } from "../lib/runVocabulary";
import { useStopRun } from "../lib/useStopRun";
import { findStatus } from "../../candidates/lib/pipelineStatus";
import { usePipelineCatalog, useStatusStyle } from "../../workspace/lib/usePipelineCatalog";
import { StopRunDialog } from "./StopRunDialog";

/** What a reply asks the consultant to record — three built-in statuses. Uncava never infers it from what they wrote. */
const REPLY_CHOICES: CandidateStatus[] = ["engaged", "interested", "notInterested"];

/**
 * The executive drawer's Sequence: their latest run on this position as one row — name, where it stands, progress —
 * that opens on its steps, why the rest never went, Stop while it runs, and the ways to the sequence and the
 * conversation. Once they replied, their words and the three statuses a reply can mean sit above it. Staff-only;
 * drawn only for someone a sequence reached for or who replied.
 */
export function OutreachSection({
  projectId,
  candidateId,
  firstName,
  candidateStatus,
  open,
  onToggle,
  isSettingStatus,
  onSetStatus,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  /** The choice is offered only while they are still Contacted, the table's rule: never move someone back. */
  candidateStatus: CandidateStatus;
  open: boolean;
  onToggle: () => void;
  isSettingStatus: boolean;
  onSetStatus: (status: CandidateStatus) => void;
}) {
  const location = useLocation();
  const outreach = useQuery({
    queryKey: runApi.CANDIDATE_OUTREACH_KEY(projectId, candidateId),
    queryFn: ({ signal }) => runApi.getCandidateOutreach(projectId, candidateId, signal),
  });
  const stop = useStopRun(projectId);
  const styleOf = useStatusStyle();
  const { catalog } = usePipelineCatalog();
  // A workspace may have retired any of the three; one it retired is not offered.
  const replyChoices = REPLY_CHOICES.filter((choice) => {
    const held = findStatus(catalog, choice);
    return held !== null && !held.status.retired && !held.stage.retired;
  });
  const [isConfirmingStop, setIsConfirmingStop] = useState(false);
  const run = outreach.data?.run ?? null;
  const reply = outreach.data?.latestReply ?? null;
  if (!run && !reply) {
    return null;
  }
  const conversationPath = `/projects/${projectId}/outreach?candidate=${candidateId}`;
  // Over the Outreach page itself the drawer opens on top of this very conversation: no link back to it.
  const isOnOutreach = /\/projects\/[^/]+\/outreach\/?$/.test(location.pathname);
  // A reply to an email sent by hand, or one after its run ended, is theirs to read here as much as a run's.
  const repliedAt = reply?.at ?? (run?.status === "REPLIED" ? run.endedAt : null);
  const hasReplied = reply !== null || run?.status === "REPLIED";
  const replyText = reply?.text ?? null;

  return (
    <section aria-label={run ? "Sequence" : "Latest reply"} className="flex flex-col gap-2">
      <h3 className="type-micro-label text-u-text3">{run ? "Sequence" : "Latest reply"}</h3>
      {hasReplied && (
        <div className="rounded-u-chip bg-u-direct-tint px-3 py-2.5 text-body">
          <b className="text-u-direct">
            {firstName} replied{repliedAt ? ` ${sendTimeOf(repliedAt)}` : ""}.
          </b>{" "}
          {replyText ? (
            <>
              <q className="mt-1 block whitespace-pre-wrap text-u-text2">{replyText}</q>
              {candidateStatus === "contacted" ? "Record where they stand." : null}
            </>
          ) : (
            <>
              Read it in your inbox
              {candidateStatus === "contacted" ? ", then record where they stand." : "."}
            </>
          )}
          {candidateStatus === "contacted" && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {replyChoices.map((choice) => (
                <Button
                  key={choice}
                  variant="secondary"
                  size="xs"
                  disabled={isSettingStatus}
                  onClick={() => onSetStatus(choice)}
                >
                  {styleOf(choice).label}
                </Button>
              ))}
            </div>
          )}
          {!run && !isOnOutreach && (
            <Link to={conversationPath} className="mt-2 inline-flex items-center gap-1 font-medium text-u-accent hover:underline">
              Open conversation
              <Icon d={ICONS.arrowRight} size={12} />
            </Link>
          )}
        </div>
      )}
      {run && (
        <RunRow
          run={run}
          steps={outreach.data?.steps ?? []}
          firstName={firstName}
          open={open}
          onToggle={onToggle}
          sequencePath={`/projects/${projectId}/outreach/sequences/${run.sequenceId}`}
          conversationPath={isOnOutreach ? null : conversationPath}
          isStopping={stop.isPending}
          onStop={() => setIsConfirmingStop(true)}
        />
      )}
      {run && (
        <StopRunDialog
          open={isConfirmingStop}
          name={firstName}
          pending={stop.isPending}
          onConfirm={() =>
            stop.mutate({ id: run.id, fullName: null }, { onSettled: () => setIsConfirmingStop(false) })
          }
          onClose={() => setIsConfirmingStop(false)}
        />
      )}
    </section>
  );
}

function RunRow({
  run,
  steps,
  firstName,
  open,
  onToggle,
  sequencePath,
  conversationPath,
  isStopping,
  onStop,
}: {
  run: OutreachRun;
  steps: readonly OutreachStep[];
  firstName: string;
  open: boolean;
  onToggle: () => void;
  sequencePath: string;
  conversationPath: string | null;
  isStopping: boolean;
  onStop: () => void;
}) {
  const state = RUN_STATES[run.status];
  const tile = TILES[run.status];
  const ending = endingOf(run, steps, firstName);
  const bodyId = "outreach-section-body";
  return (
    <div className="rounded-u-card border border-u-border">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={bodyId}
        onClick={onToggle}
        className={cn(
          "grid w-full grid-cols-[34px_minmax(0,1fr)_auto_16px] items-center gap-3 px-3 py-2.5 text-start transition hover:bg-u-raised",
          open ? "rounded-t-u-card bg-u-canvas" : "rounded-u-card",
        )}
      >
        <span aria-hidden="true" className={cn("grid size-[34px] place-items-center rounded-u-chip", tile.tone)}>
          <Icon d={tile.icon} size={16} />
        </span>
        <span className="min-w-0">
          <span className="block truncate text-body font-semibold text-u-text">{run.sequenceName ?? "A sequence"}</span>
          <span className="block truncate text-note text-u-text3">
            {[run.senderName, whereItStandsOf(run, firstName)].filter(Boolean).join(" · ")}
          </span>
        </span>
        <span className="flex flex-col items-end gap-1">
          <span className={cn("rounded-full px-2 py-px text-meta font-semibold", state.className)}>{state.label}</span>
          <StepDots run={run} />
        </span>
        <Icon
          d={ICONS.chevronDown}
          size={16}
          className={cn(
            "text-u-text3 transition-transform duration-200 motion-reduce:transition-none",
            open && "rotate-180",
          )}
        />
      </button>
      <FoldBody id={bodyId} open={open}>
        <div className="flex flex-col gap-3.5 border-t border-u-border px-4 py-3.5">
          <ol
            aria-label="Steps"
            className="grid"
            style={{ gridTemplateColumns: `repeat(${Math.max(steps.length, 1)}, minmax(0, 1fr))` }}
          >
            {steps.map((step, index) => (
              <StepNode key={step.number} step={step} isLast={index === steps.length - 1} />
            ))}
          </ol>
          {ending && (
            <p className={cn("rounded-u-chip px-2.5 py-2 text-body text-u-text2", ending.tone)}>
              <b className={ending.leadClassName}>{ending.lead}</b> {ending.rest}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-note">
            {isLive(run) && (
              <Button variant="secondary" size="xs" disabled={isStopping} onClick={onStop}>
                Stop sequence
              </Button>
            )}
            <Link to={sequencePath} className="inline-flex items-center gap-1 font-medium text-u-accent hover:underline">
              Open sequence
              <Icon d={ICONS.arrowRight} size={12} />
            </Link>
            {conversationPath && (
              <Link
                to={conversationPath}
                className="inline-flex items-center gap-1 font-medium text-u-accent hover:underline"
              >
                Open conversation
                <Icon d={ICONS.arrowRight} size={12} />
              </Link>
            )}
          </div>
        </div>
      </FoldBody>
    </div>
  );
}

function StepDots({ run }: { run: OutreachRun }) {
  return (
    <span aria-label={`${run.sentCount} of ${run.stepCount} sent`} role="img" className="flex gap-[3px]">
      {Array.from({ length: run.stepCount }, (_, index) => (
        <span
          key={index}
          className={cn(
            "h-1 w-3.5 rounded-full",
            index < run.sentCount
              ? "bg-u-accent"
              : index === run.sentCount && isLive(run)
                ? "bg-u-accent-ring"
                : "bg-u-border-strong",
          )}
        />
      ))}
    </span>
  );
}

function StepNode({ step, isLast }: { step: OutreachStep; isLast: boolean }) {
  const subject = step.subject ?? "Follow-up in the same thread";
  const meta = stepMetaOf(step);
  return (
    <li className="flex min-w-0 flex-col gap-1.5" title={subject}>
      <span className="flex items-center">
        <span
          aria-hidden="true"
          className={cn(
            "grid size-[22px] flex-none place-items-center rounded-full text-meta font-semibold",
            step.state === "SENT"
              ? "bg-u-accent text-white"
              : step.state === "SCHEDULED"
                ? "border-2 border-u-accent text-u-accent"
                : step.state === "WAITING"
                  ? "border-[1.5px] border-u-border-strong text-u-text3"
                  : "border-[1.5px] border-dashed border-u-border-strong text-u-text3",
          )}
        >
          {step.state === "SENT" ? (
            <Icon d={ICONS.check} size={12} />
          ) : step.state === "SCHEDULED" ? (
            <Icon d={ICONS.clock} size={11} />
          ) : (
            step.number
          )}
        </span>
        {!isLast && <span aria-hidden="true" className="mx-1.5 h-0.5 flex-1 rounded-full bg-u-raised" />}
      </span>
      <span className={cn("text-note font-semibold", step.state === "NOT_SENT" ? "text-u-text3" : "text-u-text")}>
        Step {step.number} · {STEP_LABELS[step.state]}
      </span>
      {meta && <span className="truncate pe-2 font-mono text-meta text-u-text3">{meta}</span>}
    </li>
  );
}

const STEP_LABELS: Record<OutreachStep["state"], string> = {
  SENT: "sent",
  SCHEDULED: "scheduled",
  WAITING: "waiting",
  NOT_SENT: "not sent",
};

function stepMetaOf(step: OutreachStep): string | null {
  if (step.state === "SENT" || step.state === "SCHEDULED") return step.at ? sendTimeOf(step.at) : null;
  if (step.state === "WAITING") return step.number > 1 ? `After step ${step.number - 1}` : null;
  return null;
}

/** The folded row's second line: what happens next, or how it ended. */
function whereItStandsOf(run: OutreachRun, firstName: string): string {
  switch (run.status) {
    case "SCHEDULED":
    case "ACTIVE":
      return run.nextSendAt
        ? `step ${run.sentCount + 1} goes ${sendTimeOf(run.nextSendAt)}`
        : `${run.sentCount} of ${run.stepCount} sent`;
    case "REPLIED":
      return `ended with a reply after step ${run.sentCount}`;
    case "BOOKED":
      return `${run.bookedViaLink ? `${firstName} booked a call` : "Call booked"}${run.endedAt ? ` ${sendTimeOf(run.endedAt)}` : ""}`;
    case "BOUNCED":
      return `the address bounced after step ${run.sentCount}`;
    case "STOPPED":
      return `stopped after step ${run.sentCount}${run.stopReason ? ` — ${lowerFirst(STOP_NOTES[run.stopReason])}` : ""}`;
    case "COMPLETED":
      return `all ${run.stepCount} emails sent`;
  }
}

interface Ending {
  tone: string;
  leadClassName: string;
  lead: string;
  rest: string;
}

/** Why the rest of the sequence never went, said once rather than under every step. */
function endingOf(run: OutreachRun, steps: readonly OutreachStep[], firstName: string): Ending | null {
  const unsent = steps.filter((step) => step.state === "NOT_SENT").map((step) => step.number);
  const rest = unsent.length === 0 ? "Their sequence stopped." : `${unsentPhraseOf(unsent)} not sent.`;
  const when = run.endedAt ? ` ${sendTimeOf(run.endedAt)}.` : ".";
  switch (run.status) {
    case "BOOKED":
      return {
        tone: "bg-u-direct-tint",
        leadClassName: "text-u-direct",
        lead: `${run.bookedViaLink ? `${firstName} booked a call through your link` : `A call was booked with ${firstName}`}${when}`,
        rest,
      };
    case "BOUNCED":
      return { tone: "bg-u-offlimits-tint", leadClassName: "text-u-offlimits", lead: "The address bounced.", rest };
    case "STOPPED":
      return {
        tone: "bg-u-signal-tint",
        leadClassName: "text-u-signal",
        lead: `${run.stopReason ? STOP_NOTES[run.stopReason] : "Stopped"}${when}`,
        rest,
      };
    default:
      return null;
  }
}

function unsentPhraseOf(numbers: readonly number[]): string {
  if (numbers.length === 1) return `Step ${numbers[0]} was`;
  const head = numbers.slice(0, -1).join(", ");
  return `Steps ${head} and ${numbers[numbers.length - 1]} were`;
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1);
}

const TILES: Record<RunStatus, { icon: string; tone: string }> = {
  SCHEDULED: { icon: ICONS.clock, tone: "bg-u-raised text-u-text2" },
  ACTIVE: { icon: ICONS.clock, tone: "bg-u-signal-tint text-u-signal" },
  REPLIED: { icon: ICONS.reply, tone: "bg-u-direct-tint text-u-direct" },
  BOUNCED: { icon: ICONS.warning, tone: "bg-u-offlimits-tint text-u-offlimits" },
  STOPPED: { icon: ICONS.listOrdered, tone: "bg-u-sunken text-u-text3" },
  COMPLETED: { icon: ICONS.check, tone: "bg-u-raised text-u-text2" },
  BOOKED: { icon: ICONS.calendar, tone: "bg-u-direct-tint text-u-direct" },
};
