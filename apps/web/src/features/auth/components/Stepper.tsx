import { cn } from "../../../lib/cn";

/**
 * The progress indicator from Signup.dc.html, plus the Verify step the mockup does not model.
 *
 * A completed step is a green tick and is clickable — the mockup lets you go back, and only back.
 * Forward navigation happens by completing the step you are on; nothing after Verify is reachable
 * until the emailed link is clicked, which the router and the API both enforce.
 */

export interface Step {
  n: number;
  label: string;
}

export const SIGNUP_STEPS: Step[] = [
  { n: 1, label: "Account" },
  { n: 2, label: "Verify" },
  { n: 3, label: "Organization" },
  { n: 4, label: "Invite team" },
];

export function Stepper({
  steps,
  current,
  onGoBack,
  backableSteps,
}: {
  steps: Step[];
  current: number;
  /** Omitted when going back is not possible — e.g. once the account has actually been created. */
  onGoBack?: (step: number) => void;
  /**
   * Which completed steps may actually be returned to. Defaults to all of them.
   *
   * <p>"Completed" and "revisitable" are not the same thing, and conflating them is how a signup wizard
   * offers someone a door that opens onto a wall. Step 1 is the case: it created an account, and there
   * is nothing to go back and do differently — so it is a tick, not a link, and must not present itself
   * as one.
   */
  backableSteps?: number[];
}) {
  return (
    <nav
      aria-label="Signup progress"
      className="flex animate-fade-up items-center [animation-delay:40ms]"
    >
      {steps.map((step, index) => {
        const done = step.n < current;
        const active = step.n === current;
        const canGoBack =
          done && !!onGoBack && (backableSteps ? backableSteps.includes(step.n) : true);

        return (
          <div key={step.n} className="flex items-center">
            {index > 0 && (
              <span
                className={cn(
                  "mx-1.5 h-px w-4 sm:mx-2 sm:w-8",
                  step.n <= current ? "bg-amber-btn" : "bg-line",
                )}
                aria-hidden="true"
              />
            )}

            <button
              type="button"
              disabled={!canGoBack}
              onClick={() => canGoBack && onGoBack?.(step.n)}
              aria-current={active ? "step" : undefined}
              className="flex items-center gap-2 rounded-md outline-none focus-visible:ring-2 focus-visible:ring-sky disabled:cursor-default"
            >
              <span
                className={`grid size-6 place-items-center rounded-full border font-mono text-[11px] font-semibold ${
                  done
                    ? "border-transparent bg-green-dim text-green"
                    : active
                      ? "border-amber-btn bg-amber-btn text-on-amber"
                      : "border-line bg-panel2 text-text3"
                }`}
              >
                {done ? "✓" : step.n}
              </span>

              <span
                className={cn(
                  "text-xs font-medium",
                  active ? "inline text-text" : "hidden sm:inline",
                  done ? "text-text2" : !active && "text-text3",
                )}
              >
                {step.label}
              </span>
            </button>
          </div>
        );
      })}
    </nav>
  );
}
