import { Icon } from "../../../components/layout/Icon";
import type { AssistantStep, LiveStep } from "../api/types";

/** What the assistant did, one line per step: a spinner while it runs, a check once it is done. */
export function AssistantSteps({ steps }: { steps: (AssistantStep | LiveStep)[] }) {
  if (steps.length === 0) return null;
  return (
    <ul className="mb-3 rounded-[9px] border border-line-soft bg-panel2 p-2">
      {steps.map((step, index) => {
        const running = "done" in step && !step.done;
        return (
          <li key={index} className="flex items-start gap-2 py-1">
            <span className="mt-0.5 grid h-[13px] w-[13px] flex-none place-items-center">
              {running ? (
                <Icon d="M21 12a9 9 0 1 1-6.2-8.6" size={11} className="animate-spin text-ai" />
              ) : (
                <Icon d="M20 6 9 17l-5-5" size={11} className="text-green" />
              )}
            </span>
            <span className="flex-1 font-sans text-[11.5px] leading-[1.45] text-text2">
              {step.label}
              {step.detail && <span className="text-text3"> — {step.detail}</span>}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
