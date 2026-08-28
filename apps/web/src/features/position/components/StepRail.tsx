import { Button } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import type { Position } from "../api/types";
import { POSITION_STEPS, completion, type StepKey } from "../lib/steps";

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
  onSelectStep,
  onPublish,
  onSaveDraft,
  publishing,
}: {
  position: Position;
  currentStep: StepKey;
  onSelectStep: (key: StepKey) => void;
  onPublish: () => void;
  onSaveDraft: () => void;
  publishing: boolean;
}) {
  const donePercent = completion(position);
  const published = Boolean(position.publication.publishedAt);

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
        {POSITION_STEPS.map((step) => (
          <span
            key={step.key}
            className={cn(
              "h-[3px] flex-1 rounded-full",
              step.isDone(position) ? "bg-green" : step.key === currentStep ? "bg-sky" : "bg-line",
            )}
          />
        ))}
      </div>

      <ol className="flex flex-col gap-2">
        {POSITION_STEPS.map((step, index) => {
          const done = step.isDone(position);
          const current = step.key === currentStep;
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
                    : done
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
                      current ? "text-sky" : "text-green",
                    )}
                  >
                    {current ? "Editing" : done ? "✓" : ""}
                  </span>
                </span>
                <span
                  className={cn(
                    "mt-[7px] block truncate text-[13px] font-semibold",
                    current || done ? "text-text" : "text-text3",
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

      <Button onClick={onPublish} loading={publishing} className="mt-4 w-full py-[11px]">
        {published ? "Published" : "Publish position profile"}
      </Button>
      <Button variant="secondary" onClick={onSaveDraft} className="mt-2 w-full py-2.5">
        Save draft
      </Button>
    </aside>
  );
}
