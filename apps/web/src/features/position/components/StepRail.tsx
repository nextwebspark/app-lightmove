import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import type { Position } from "../api/types";
import { POSITION_STEPS, completion, doneSteps, type StepKey } from "../lib/steps";

/**
 * The profile summary beside the form: how far the brief has got, and one clickable line per step
 * reading back what that step currently holds.
 *
 * Below `md` it sits above the form rather than beneath it — it is the wizard's navigation, and
 * navigation under the fold is navigation nobody finds.
 */
export function StepRail({
  position,
  currentStep,
  furthestStep,
  onSelectStep,
  onPublish,
  onSaveDraft,
  onGoToStrategy,
  onEditPosition,
  editing,
  publishing,
}: {
  position: Position;
  currentStep: StepKey;
  /** The furthest step reached: nothing beyond it is reported done. */
  furthestStep: StepKey;
  onSelectStep: (key: StepKey) => void;
  onPublish: () => void;
  onSaveDraft: () => void;
  onGoToStrategy: () => void;
  onEditPosition: () => void;
  /** Whether a published brief has been opened for changes — see ReviewStep's `canEdit`. */
  editing: boolean;
  publishing: boolean;
}) {
  const published = Boolean(position.publication.publishedAt);
  // A published brief nobody has opened for changes is being read, not edited — the step in view is
  // simply the page you are on, and labelling it "Editing" would describe something not happening.
  const readingBack = published && !editing;
  const done = doneSteps(position, furthestStep);
  const donePercent = completion(position, furthestStep);

  return (
    <aside className="order-1 w-full rounded-[10px] border border-line-soft bg-panel2 p-4 md:order-2 md:max-w-[340px] md:flex-[1_1_280px] lg:sticky lg:top-0">
      <div className="flex items-center gap-2.5">
        <span className="text-[13px] font-bold uppercase tracking-[0.07em] text-text">
          Profile summary
        </span>
        <span
          className={cn(
            "ms-auto rounded-full border px-2.5 py-[3px] font-mono text-[11px] font-semibold",
            donePercent === 100
              ? "border-green bg-green-dim text-green"
              : "border-sky bg-sky-dim text-sky",
          )}
        >
          {donePercent}% Done
        </span>
      </div>

      <div className="my-3.5 flex gap-1" aria-hidden="true">
        {POSITION_STEPS.map((step, index) => (
          <span
            key={step.key}
            className={cn(
              "h-[3px] flex-1 rounded-full",
              done[index] ? "bg-green" : step.key === currentStep ? "bg-sky" : "bg-line",
            )}
          />
        ))}
      </div>

      <ol className="flex flex-col gap-2">
        {POSITION_STEPS.map((step, index) => {
          const stepDone = done[index];
          const current = step.key === currentStep;
          const label = current && !readingBack ? "Editing" : stepDone ? "✓" : "";
          return (
            <li key={step.key}>
              <button
                type="button"
                aria-current={current ? "step" : undefined}
                onClick={() => onSelectStep(step.key)}
                className={cn(
                  "block w-full rounded-[9px] border px-3.5 py-[11px] text-start transition",
                  current
                    ? "border-solid border-sky bg-sky-dim"
                    : stepDone
                      ? "border-solid border-line-soft bg-transparent hover:border-text3"
                      : "border-dashed border-line bg-transparent hover:border-text3",
                )}
              >
                <span className="flex items-center gap-2">
                  <span
                    className={cn(
                      "font-mono text-[10px] font-bold uppercase tracking-[0.1em]",
                      current ? "text-sky" : "text-text3",
                    )}
                  >
                    {index + 1}. {step.name}
                  </span>
                  <span
                    className={cn(
                      "ms-auto whitespace-nowrap font-mono text-[10.5px] font-semibold",
                      label === "Editing" ? "text-sky" : "text-green",
                    )}
                  >
                    {label}
                  </span>
                </span>
                <span
                  className={cn(
                    "mt-[7px] block truncate text-[13px] font-semibold",
                    current || stepDone ? "text-text" : "text-text3",
                  )}
                >
                  {step.summary(position)}
                </span>
                <span className="mt-[3px] block truncate font-mono text-[11.5px] text-text3">
                  {step.detail(position)}
                </span>
              </button>
            </li>
          );
        })}
      </ol>

      {/* The lead button is whatever the brief is waiting for. Unpublished, that is publishing it.
          Published and read back, it is getting on with the search. Published and being changed, it
          is publishing again — so the way out of editing is the same act that got the brief here. */}
      {published && !editing ? (
        <>
          <Button onClick={onGoToStrategy} className="mt-4 w-full py-[11px]">
            Move to strategy
            <Icon d={ICONS.arrowRight} size={15} />
          </Button>
          <Button variant="secondary" onClick={onEditPosition} className="mt-2 w-full py-2.5">
            Edit position
          </Button>
        </>
      ) : (
        <>
          <Button onClick={onPublish} loading={publishing} className="mt-4 w-full py-[11px]">
            {published ? "Publish changes" : "Publish position profile"}
          </Button>
          {published ? (
            <Button variant="secondary" onClick={onGoToStrategy} className="mt-2 w-full py-2.5">
              Move to strategy
            </Button>
          ) : (
            <Button variant="secondary" onClick={onSaveDraft} className="mt-2 w-full py-2.5">
              Save draft
            </Button>
          )}
        </>
      )}
    </aside>
  );
}
