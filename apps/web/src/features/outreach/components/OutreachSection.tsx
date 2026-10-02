import { useQuery } from "@tanstack/react-query";
import { CollapsibleSection } from "../../../components/ui/CollapsibleSection";
import { cn } from "../../../lib/cn";
import type { CandidateStatus } from "../../candidates/api/types";
import * as runApi from "../api/runApi";
import type { OutreachStep } from "../api/runApi";
import { isLive, RUN_STATES, sendTimeOf, STOP_NOTES } from "../lib/runVocabulary";
import { useStopRun } from "../lib/useStopRun";

/** What a reply asks the consultant to record. Uncava never infers it from what they wrote. */
const REPLY_CHOICES: { value: CandidateStatus; label: string }[] = [
  { value: "engaged", label: "Engaged" },
  { value: "interested", label: "Interested" },
  { value: "notInterested", label: "Not interested" },
];

/**
 * The executive drawer's Outreach fold (`Outreach.dc.html?drawer=`): their latest run on this position,
 * step by step — sent, scheduled, waiting, or not sent and why — with Stop while it runs and, once they
 * replied, the three statuses a reply can mean. Staff-only; drawn only for someone a sequence reached for.
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
  const outreach = useQuery({
    queryKey: runApi.CANDIDATE_OUTREACH_KEY(projectId, candidateId),
    queryFn: ({ signal }) => runApi.getCandidateOutreach(projectId, candidateId, signal),
  });
  const stop = useStopRun(projectId);
  const run = outreach.data?.run ?? null;
  if (!run) {
    return null;
  }
  const state = RUN_STATES[run.status];

  return (
    <CollapsibleSection
      id="outreach"
      open={open}
      onToggle={onToggle}
      title="Outreach"
      summary={`${state.label} · ${run.sentCount} of ${run.stepCount} sent`}
    >
      <div className="pb-3">
        {run.status === "REPLIED" && (
          <div className="mb-3 rounded-[8px] bg-u-direct-tint px-3 py-2.5 text-[12.5px]/[1.5]">
            <b className="text-u-direct">
              {firstName} replied{run.endedAt ? ` ${sendTimeOf(run.endedAt)}` : ""}.
            </b>{" "}
            Read it in your inbox{candidateStatus === "contacted" ? ", then record where they stand." : "."}
            {candidateStatus === "contacted" && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {REPLY_CHOICES.map((choice) => (
                  <button
                    key={choice.value}
                    type="button"
                    disabled={isSettingStatus}
                    onClick={() => onSetStatus(choice.value)}
                    className="rounded-[6px] border border-u-border bg-u-surface px-2.5 py-1 text-[12px] font-medium hover:border-u-text3 disabled:opacity-60"
                  >
                    {choice.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
        {run.status === "BOOKED" && (
          <div className="mb-3 rounded-[8px] bg-u-direct-tint px-3 py-2.5 text-[12.5px]/[1.5]">
            <b className="text-u-direct">A call was booked with {firstName}.</b>{" "}
            {run.endedAt ? `${sendTimeOf(run.endedAt)}. ` : ""}Their sequence stopped.
          </div>
        )}
        <div className="mb-1.5 flex flex-wrap items-center gap-2 text-[12.5px] font-medium text-u-text2">
          <span className={cn("rounded-full px-2 py-[2px] font-mono text-[10.5px] uppercase", state.className)}>
            {state.label}
          </span>
          <span>
            {run.sequenceName ?? "A sequence"}
            {run.senderName ? ` · from ${run.senderName}` : ""}
          </span>
        </div>
        <ol>
          {outreach.data?.steps.map((step) => (
            <StepLine key={step.number} step={step} />
          ))}
        </ol>
        {isLive(run) && (
          <button
            type="button"
            disabled={stop.isPending}
            onClick={() => stop.mutate({ id: run.id, fullName: null })}
            className="mt-2 rounded-[6px] border border-u-border px-[11px] py-[5px] text-[12px] font-medium text-u-text2 hover:border-u-offlimits hover:text-u-offlimits disabled:opacity-60"
          >
            Stop sequence
          </button>
        )}
      </div>
    </CollapsibleSection>
  );
}

function StepLine({ step }: { step: OutreachStep }) {
  const isEnded = step.state === "NOT_SENT";
  return (
    <li className="flex items-start gap-2.5 py-[7px]">
      <span
        aria-hidden="true"
        className={cn(
          "mt-1 size-2.5 flex-none rounded-full",
          step.state === "SENT"
            ? "bg-u-accent"
            : step.state === "SCHEDULED"
              ? "border-2 border-u-accent"
              : "border-[1.5px] border-u-border",
        )}
      />
      <span className="min-w-0 flex-1">
        <span className={cn("block text-[12.5px] font-medium", isEnded ? "text-u-text3" : "text-u-text")}>
          Step {step.number} · {step.subject ?? "Follow-up in the same thread"}
        </span>
        <span className="block font-mono text-[11.5px] text-u-text3">{stepMetaOf(step)}</span>
      </span>
    </li>
  );
}

function stepMetaOf(step: OutreachStep): string {
  switch (step.state) {
    case "SENT":
      return step.at ? `Sent ${sendTimeOf(step.at)}` : "Sent";
    case "SCHEDULED":
      return step.at ? `Scheduled ${sendTimeOf(step.at)}` : "Scheduled";
    case "WAITING":
      return "Waiting";
    case "NOT_SENT":
      return `Not sent: ${notSentReasonOf(step.notSentBecause)}`;
  }
}

function notSentReasonOf(reason: OutreachStep["notSentBecause"]): string {
  if (reason === "REPLIED") return "they replied";
  if (reason === "BOUNCED") return "the address bounced";
  if (reason === "BOOKED") return "they booked a call";
  if (reason === null || reason === "MANUAL") return "sequence stopped";
  return STOP_NOTES[reason].toLowerCase();
}
