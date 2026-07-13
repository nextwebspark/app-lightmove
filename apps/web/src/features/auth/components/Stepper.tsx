/**
 * The three-step progress indicator from Signup.dc.html.
 *
 * A completed step is a green tick and is clickable — the mockup lets you go back, and only back.
 * Forward navigation happens by completing the step you are on, because step 2 cannot run until step 1
 * has created the account it needs.
 */

export interface Step {
  n: number;
  label: string;
}

export const SIGNUP_STEPS: Step[] = [
  { n: 1, label: "Account" },
  { n: 2, label: "Organization" },
  { n: 3, label: "Invite team" },
];

export function Stepper({
  steps,
  current,
  onGoBack,
}: {
  steps: Step[];
  current: number;
  /** Omitted when going back is not possible — e.g. once the account has actually been created. */
  onGoBack?: (step: number) => void;
}) {
  return (
    <nav aria-label="Signup progress" className="flex animate-fade-up items-center [animation-delay:40ms]">
      {steps.map((step, index) => {
        const done = step.n < current;
        const active = step.n === current;
        const canGoBack = done && !!onGoBack;

        return (
          <div key={step.n} className="flex items-center">
            {index > 0 && (
              <span
                className={`mx-2 h-px w-8 ${step.n <= current ? "bg-amber-btn" : "bg-line"}`}
                aria-hidden="true"
              />
            )}

            <button
              type="button"
              disabled={!canGoBack}
              onClick={() => canGoBack && onGoBack(step.n)}
              aria-current={active ? "step" : undefined}
              className="flex items-center gap-2 disabled:cursor-default"
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
                className={`text-xs font-medium ${
                  active ? "text-text" : done ? "text-text2" : "text-text3"
                }`}
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
