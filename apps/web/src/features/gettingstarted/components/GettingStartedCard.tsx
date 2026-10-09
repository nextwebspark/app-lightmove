import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as gettingStartedApi from "../api/gettingStartedApi";
import type { GettingStarted, GettingStartedStep } from "../api/gettingStartedApi";
import { stepCopyOf } from "../lib/steps";

/** Endowed progress: the two steps signup already did are shown done, so nobody starts at zero. */
const ALREADY_DONE = ["Create your account", "Set up your workspace"] as const;

/**
 * My positions' "Get your first map" card. Its rows tick from what the workspace actually holds, read by the
 * server, never from a click here; a row can be skipped and the card put away, both remembered per person.
 */
export function GettingStartedCard({
  onOpenPosition,
  intro,
  fallback = null,
}: {
  onOpenPosition: () => void;
  /** Leads the card in an empty workspace, where the card is the whole page. */
  intro?: ReactNode;
  /** What stands in once the card is put away or finished. */
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
        toast.success("Getting started is hidden.", { undo: () => dismiss.mutate(false) });
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

  if (isPending) return null;
  if (!data || data.dismissed || data.steps.every((step) => step.done || step.skipped)) return <>{fallback}</>;

  const total = ALREADY_DONE.length + data.steps.length;
  const done = ALREADY_DONE.length + data.steps.filter((step) => step.done).length;

  return (
    <section
      aria-labelledby="getting-started-title"
      className="mb-5 rounded-[10px] border border-u-border-strong bg-u-surface p-4 sm:p-5"
    >
      {intro && <div className="mb-4 border-b border-u-border pb-4">{intro}</div>}
      <div className="mb-3 flex flex-wrap items-start gap-x-4 gap-y-2">
        <div className="min-w-0 flex-1">
          <h2 id="getting-started-title" className="text-lead font-semibold">
            Get your first map in 30 minutes
          </h2>
          <p className="mt-0.5 text-note text-u-text3">
            Each step ticks itself when the work is done.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="font-mono text-meta text-u-text3">
            {done} of {total}
          </span>
          <div
            role="progressbar"
            aria-label="Getting started progress"
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={done}
            className="h-1.5 w-24 overflow-hidden rounded-full bg-u-border"
          >
            <div className="h-full rounded-full bg-u-direct" style={{ width: `${(done / total) * 100}%` }} />
          </div>
        </div>
      </div>

      <ol className="divide-y divide-u-border">
        {ALREADY_DONE.map((title) => (
          <li key={title} className="flex items-center gap-3 py-2">
            <DoneMark />
            <span className="text-body text-u-text3 line-through decoration-u-text3/40">{title}</span>
          </li>
        ))}
        {data.steps.map((step) => (
          <StepRow
            key={step.step}
            step={step}
            focusProjectId={data.focusProjectId}
            onOpenPosition={onOpenPosition}
            onSkip={(skipped) => skip.mutate({ step: step.step, skipped })}
            busy={skip.isPending}
          />
        ))}
      </ol>

      <div className="mt-3 flex justify-end">
        <button
          type="button"
          onClick={() => dismiss.mutate(true)}
          disabled={dismiss.isPending}
          className="text-note text-u-text3 hover:text-u-text2 hover:underline"
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
  onOpenPosition,
  onSkip,
  busy,
}: {
  step: GettingStartedStep;
  focusProjectId: string | null;
  onOpenPosition: () => void;
  onSkip: (skipped: boolean) => void;
  busy: boolean;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const copy = stepCopyOf(step.step, vocabulary.unitLower);
  const blocked = copy.needsPosition && !focusProjectId;
  const settled = step.done || step.skipped;

  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-1.5 py-2.5">
      {step.done ? <DoneMark /> : <OpenMark />}
      <div className="min-w-0 flex-1 basis-[220px]">
        <div className={cn("text-body font-medium", settled && "text-u-text3", step.done && "line-through decoration-u-text3/40")}>
          {copy.title}
          {step.skipped && !step.done && <span className="ml-2 font-normal text-note text-u-text3">Skipped</span>}
        </div>
        {!settled && <div className="text-note text-u-text3">{blocked ? "Open a position first." : copy.detail}</div>}
      </div>
      {!step.done && (
        <div className="flex items-center gap-3 pl-8 sm:pl-0">
          {step.skipped ? (
            <button
              type="button"
              onClick={() => onSkip(false)}
              disabled={busy}
              className="text-note text-u-text3 hover:text-u-text2 hover:underline"
              aria-label={`Undo skip: ${copy.title}`}
            >
              Undo
            </button>
          ) : (
            <>
              {!blocked &&
                (copy.pathFor ? (
                  <Link
                    to={copy.pathFor(focusProjectId ?? "")}
                    className="inline-flex items-center gap-1 text-note font-medium text-u-accent hover:underline"
                  >
                    {copy.action}
                    <Icon d={ICONS.arrowRight} size={13} />
                  </Link>
                ) : (
                  <button
                    type="button"
                    onClick={onOpenPosition}
                    className="inline-flex items-center gap-1 text-note font-medium text-u-accent hover:underline"
                  >
                    {copy.action}
                    <Icon d={ICONS.arrowRight} size={13} />
                  </button>
                ))}
              <button
                type="button"
                onClick={() => onSkip(true)}
                disabled={busy}
                className="text-note text-u-text3 hover:text-u-text2 hover:underline"
                aria-label={`Skip: ${copy.title}`}
              >
                Skip
              </button>
            </>
          )}
        </div>
      )}
    </li>
  );
}

function DoneMark() {
  return (
    <span className="grid size-5 shrink-0 place-items-center text-u-direct">
      <Icon d={ICONS.checkCircle} size={18} />
      <span className="sr-only">Done:</span>
    </span>
  );
}

function OpenMark() {
  return (
    <span aria-hidden="true" className="grid size-5 shrink-0 place-items-center">
      <span className="size-[15px] rounded-full border-[1.5px] border-u-border-strong" />
    </span>
  );
}
