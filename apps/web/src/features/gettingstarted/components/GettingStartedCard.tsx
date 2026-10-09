import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { Billing } from "../../billing/api/types";
import { daysLeftLabel, formatBillingDate, trialOf } from "../../billing/lib/billingView";
import { useBilling } from "../../billing/lib/useBilling";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as gettingStartedApi from "../api/gettingStartedApi";
import type { GettingStarted, GettingStartedStep } from "../api/gettingStartedApi";
import { stepCopyOf } from "../lib/steps";
import { usePositionsOriginState } from "../../projects/lib/positionsOrigin";

/** Endowed progress: the two steps signup already did are shown done, so nobody starts at zero. */
const ALREADY_DONE = ["Create your account", "Set up your workspace"] as const;

const TEXT_ACTION =
  "rounded py-1 text-note hover:underline focus-visible:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent";

/**
 * My positions' "Get your first map" card. Rows tick from what the workspace holds, read by the server, never from
 * a click here; a row can be skipped and the card put away, both remembered per person.
 */
export function GettingStartedCard({
  onOpenPosition,
  intro,
  fallback = null,
}: {
  onOpenPosition: () => void;
  /** Leads the card in an empty workspace, where the card is the whole page; its row for the first position then
   *  offers no second New position. */
  intro?: ReactNode;
  /** What stands in once the card is put away, finished, or could not be read. */
  fallback?: ReactNode;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const { data, isPending } = useQuery({
    queryKey: gettingStartedApi.GETTING_STARTED_KEY,
    queryFn: gettingStartedApi.gettingStarted,
    // Every return to My positions re-reads it: the work that ticks a step happens on other screens.
    staleTime: 0,
  });

  const store = (fresh: GettingStarted) => queryClient.setQueryData(gettingStartedApi.GETTING_STARTED_KEY, fresh);
  const dismiss = useMutation({
    mutationFn: (dismissed: boolean) => gettingStartedApi.setDismissed(dismissed),
    onSuccess: (fresh, dismissed) => {
      store(fresh);
      if (dismissed) {
        toast.success("Getting started is hidden. Bring it back from Help.", { undo: () => dismiss.mutate(false) });
      }
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const skip = useMutation({
    mutationFn: ({ step, skipped }: { step: GettingStartedStep["step"]; skipped: boolean }) =>
      gettingStartedApi.setSkipped(step, skipped),
    onSuccess: store,
    onError: (error) => toast.error(messageFor(error)),
  });

  const billing = useBilling();

  if (isPending) return null;
  if (!data || data.dismissed || data.steps.every((step) => step.done || step.skipped)) return <>{fallback}</>;

  const total = ALREADY_DONE.length + data.steps.length;
  const done = ALREADY_DONE.length + data.steps.filter((step) => step.done).length;
  const positionOpened = data.steps.some((step) => step.step === "OPEN_POSITION" && step.done);

  return (
    <section
      aria-labelledby="getting-started-title"
      className="mb-5 rounded-u-card border border-u-border-strong bg-u-surface p-4 sm:p-5"
    >
      {intro && <div className="mb-4 border-b border-u-border pb-4">{intro}</div>}
      <div className="mb-3 flex flex-wrap items-start gap-x-4 gap-y-2">
        <div className="min-w-0 flex-1">
          <h2 id="getting-started-title" className="text-lead font-semibold">
            Get your first map in 30 minutes
          </h2>
          <p className="mt-0.5 text-note text-u-text3">Each step ticks itself when the work is done.</p>
        </div>
        <div className="flex items-center gap-2.5">
          <span aria-hidden="true" className="font-mono text-meta text-u-text3">
            {done} of {total}
          </span>
          <div
            role="progressbar"
            aria-label="Getting started progress"
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={done}
            aria-valuetext={`${done} of ${total} steps done`}
            className="h-1.5 w-24 overflow-hidden rounded-full bg-u-border"
          >
            <div className="h-full rounded-full bg-u-direct" style={{ width: `${(done / total) * 100}%` }} />
          </div>
        </div>
      </div>

      {billing.data && <TrialNote billing={billing.data} />}

      <ol className="divide-y divide-u-border">
        {ALREADY_DONE.map((title) => (
          <li key={title} className="flex items-center gap-3 py-2.5">
            <StepMark state="done" />
            <span className="text-body text-u-text3">{title}</span>
          </li>
        ))}
        {data.steps.map((step) => (
          <StepRow
            key={step.step}
            step={step}
            focusProjectId={data.focusProjectId}
            positionOpened={positionOpened}
            offersNewPosition={!intro}
            onOpenPosition={onOpenPosition}
            onSkip={(skipped) => skip.mutate({ step: step.step, skipped })}
            busy={skip.isPending}
          />
        ))}
      </ol>

      <div className="mt-2 flex justify-end">
        <button
          type="button"
          onClick={() => dismiss.mutate(true)}
          disabled={dismiss.isPending}
          className={cn(TEXT_ACTION, "text-u-text3 hover:text-u-text2")}
        >
          I know my way around
        </button>
      </div>
    </section>
  );
}

function StepRow({
  step,
  focusProjectId,
  positionOpened,
  offersNewPosition,
  onOpenPosition,
  onSkip,
  busy,
}: {
  step: GettingStartedStep;
  focusProjectId: string | null;
  positionOpened: boolean;
  offersNewPosition: boolean;
  onOpenPosition: () => void;
  onSkip: (skipped: boolean) => void;
  busy: boolean;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const copy = stepCopyOf(step.step, vocabulary.unitLower);
  const { target } = copy;
  const blocked = target.kind === "position" && !focusProjectId;
  const blockedHint = positionOpened ? "Ask a lead to add you to a position first." : "Open a position first.";
  // The first position is what every later step needs, so it can't be put aside.
  const skippable = step.step !== "OPEN_POSITION";

  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2.5">
      <StepMark state={step.done ? "done" : step.skipped ? "skipped" : "open"} />
      <div className="min-w-0 flex-1 basis-[220px]">
        <div className={cn("text-body font-medium", (step.done || step.skipped) && "font-normal text-u-text3")}>
          {copy.title}
          {step.skipped && !step.done && <span className="ml-2 text-note text-u-text3">· Skipped</span>}
        </div>
        {!step.done && !step.skipped && (
          <div className="text-note text-u-text3">
            {copy.detail}
            {blocked && <span className="text-u-text2"> {blockedHint}</span>}
          </div>
        )}
      </div>
      {!step.done && (
        <div className="flex items-center gap-3 pl-8 sm:pl-0">
          {!step.skipped && !blocked && (
            <StepAction
              copy={copy.action}
              target={target}
              focusProjectId={focusProjectId}
              offersNewPosition={offersNewPosition}
              onOpenPosition={onOpenPosition}
            />
          )}
          {/* One button whose label turns, so the keyboard keeps its place between Skip and Undo. */}
          {skippable && !(blocked && !step.skipped) && (
            <button
              type="button"
              onClick={() => onSkip(!step.skipped)}
              disabled={busy}
              className={cn(TEXT_ACTION, "text-u-text3 hover:text-u-text2")}
              aria-label={step.skipped ? `Undo skip: ${copy.title}` : `Skip: ${copy.title}`}
            >
              {step.skipped ? "Undo" : "Skip"}
            </button>
          )}
        </div>
      )}
    </li>
  );
}

/** Sets out what the trial includes before anything is refused for want of it. Not a step: nothing ticks it. */
function TrialNote({ billing }: { billing: Billing }) {
  const trial = trialOf(billing);
  if (!trial || trial.ended) return null;
  const plan = billing.plan ? `${billing.plan.name} trial` : "trial";

  return (
    <div className="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-u-accent-tint px-3 py-2.5">
      <Icon d={ICONS.clock} size={15} className="shrink-0 text-u-accent" />
      <p className="min-w-0 flex-1 basis-[240px] text-note text-u-text2">
        <span className="font-medium text-u-text">
          Your {plan}: {daysLeftLabel(trial.daysLeft)}.
        </span>{" "}
        Search, AI and {billing.credits.monthly} contact credits are included until {formatBillingDate(trial.endsAt).replaceAll(" ", "\u00a0")}.
        Choose a plan to keep them.
      </p>
      <Link to="/settings/billing" className={cn(TEXT_ACTION, "inline-flex items-center gap-1 font-medium text-u-accent")}>
        See your plan
        <Icon d={ICONS.arrowRight} size={13} />
      </Link>
    </div>
  );
}

function StepAction({
  copy,
  target,
  focusProjectId,
  offersNewPosition,
  onOpenPosition,
}: {
  copy: string;
  target: ReturnType<typeof stepCopyOf>["target"];
  focusProjectId: string | null;
  offersNewPosition: boolean;
  onOpenPosition: () => void;
}) {
  const className = cn(TEXT_ACTION, "inline-flex items-center gap-1 font-medium text-u-accent");
  const label = (
    <>
      {copy}
      <Icon d={ICONS.arrowRight} size={13} />
    </>
  );
  const originState = usePositionsOriginState();
  switch (target.kind) {
    case "newPosition":
      return offersNewPosition ? (
        <button type="button" onClick={onOpenPosition} className={className}>
          {label}
        </button>
      ) : null;
    case "page":
      return (
        <Link to={target.path} className={className}>
          {label}
        </Link>
      );
    case "position":
      return focusProjectId ? (
        <Link to={target.pathFor(focusProjectId)} state={originState} className={className}>
          {label}
        </Link>
      ) : null;
  }
}

function StepMark({ state }: { state: "done" | "open" | "skipped" }) {
  if (state === "done") {
    return (
      <span className="grid size-5 shrink-0 place-items-center text-u-direct">
        <Icon d={ICONS.checkCircle} size={18} />
        <span className="sr-only">Done:</span>
      </span>
    );
  }
  return (
    <span aria-hidden="true" className="grid size-5 shrink-0 place-items-center">
      {state === "skipped" ? (
        <span className="h-[1.5px] w-2.5 rounded-full bg-u-text3" />
      ) : (
        <span className="size-[15px] rounded-full border-[1.5px] border-u-border-strong" />
      )}
    </span>
  );
}
