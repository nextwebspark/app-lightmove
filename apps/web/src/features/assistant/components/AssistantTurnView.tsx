import { Icon } from "../../../components/layout/Icon";
import type { TurnProgress } from "../lib/useAssistantTurn";

/**
 * One exchange as it happens: what was asked, what the assistant is doing, and what it has said.
 *
 * <p>The trace is not decoration. A turn runs 30–180s, and saying "Searching the company universe"
 * is what makes that tolerable — it is also how somebody notices the assistant is about to do
 * something they did not want, while there is still time to say so.
 */
export function AssistantTurnView({
  question,
  progress,
}: {
  question: string;
  progress: TurnProgress;
}) {
  const running = progress.status === "RUNNING";
  const failed = progress.status === "FAILED" || progress.status === "CANCELLED";

  return (
    <div>
      <div className="mb-3.5 flex justify-end">
        <div className="max-w-[86%] rounded-[12px_12px_3px_12px] bg-panel2 px-3 py-2 font-sans text-[13px] leading-[1.5] text-text">
          {question}
        </div>
      </div>

      {progress.steps.length > 0 && (
        <ul className="mb-3 rounded-[9px] border border-line-soft bg-panel2 p-2">
          {progress.steps.map((step) => (
            <li key={step.seq} className="flex items-start gap-2 py-1">
              <span className="mt-0.5 grid h-[13px] w-[13px] flex-none place-items-center">
                {step.running ? (
                  <Icon d="M21 12a9 9 0 1 1-6.2-8.6" size={11} className="animate-spin text-ai" />
                ) : (
                  <Icon d="M20 6 9 17l-5-5" size={11} className="text-green" />
                )}
              </span>
              <span className="flex-1 font-sans text-[11.5px] leading-[1.45] text-text2">
                {step.label}
              </span>
              {/* Every tool the assistant has today reads data the firm already holds. A paid step
                  says otherwise, and none exists yet — the web search is #464. */}
              <span className="flex-none font-mono text-[10px] text-text3">free</span>
            </li>
          ))}
        </ul>
      )}

      {progress.answer && (
        <div className="font-sans text-[13px] leading-[1.6] text-text">
          {progress.answer}
          {running && (
            <span className="ms-px inline-block h-3.5 w-0.5 -translate-y-px animate-pulse bg-ai align-[-2px]" />
          )}
        </div>
      )}

      {running && !progress.answer && progress.steps.length === 0 && (
        <p className="font-mono text-[11px] text-text3">Thinking…</p>
      )}

      {failed && (
        <div className="flex gap-2.5 rounded-[9px] border border-amber/40 bg-amber-dim px-3 py-2.5">
          <Icon
            d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"
            size={14}
            className="mt-0.5 flex-none text-amber"
          />
          <p className="font-sans text-[11.5px] leading-[1.5] text-text2">
            {failureMessage(progress.errorCode)}
          </p>
        </div>
      )}
    </div>
  );
}

/**
 * What a failed turn says.
 *
 * <p>Not {@code messageFor}: that takes a thrown {@code ApiRequestError} and reads its `code` off
 * the object, so handing it a bare code string always falls through to the generic sentence. These
 * are the codes a turn ends on, which are not the ones a request fails with.
 */
function failureMessage(code: string | null): string {
  // Only the two a turn actually ends on: the sweep's, and everything else. A branch for a code the
  // server cannot emit is a branch nobody will ever see be wrong.
  return code === "ASSISTANT_TURN_STRANDED"
    ? "That answer stopped part way through. Ask again."
    : "That answer did not finish. Ask again.";
}
