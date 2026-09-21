import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import type { TriageCompanyStatus } from "../../triage/api/types";
import * as assistantApi from "../api/assistantApi";
import type { AssistantProposal } from "../api/types";
import type { TurnProgress } from "../lib/useAssistantTurn";
import { AssistantProposalCard, outcomeLine } from "./AssistantProposalCard";

/**
 * One exchange: what was asked, what the assistant is doing, what it said, and what it is offering.
 *
 * <p>The trace is not decoration. A turn runs 30–180s, and saying "Searching the company universe"
 * is what makes that tolerable — it is also how somebody notices the assistant is about to do
 * something they did not want, while there is still time to say so.
 *
 * <p><b>This is where the proposal is written, and the card is not.</b> The card hands up the refs
 * a person ticked and the stage they pressed; everything with a consequence — the request, the
 * toast, and the reads that go stale — happens here.
 *
 * <p><b>Dismissing is this reader's, and only for as long as they are looking.</b> It is component
 * state, so it survives neither a reload nor a remount — and the panel remounts on crossing between
 * the three layouts, so dismissing a card and walking from Strategy to Clients brings it back. That
 * is the accepted answer rather than an oversight: nothing server-side records a dismissal, and
 * making one durable means first deciding whether dismissing is a fact about the mandate or about
 * the person reading it. The case that matters is already durable — once the proposal is *filed*,
 * its outcome is a stored event and the card never offers its buttons again.
 */
export function AssistantTurnView({
  turnId,
  threadId,
  question,
  progress,
}: {
  turnId: string;
  threadId: string;
  question: string;
  progress: TurnProgress;
}) {
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();
  const toast = useToast();
  const [dismissed, setDismissed] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const running = progress.status === "RUNNING";
  const failed = progress.status === "FAILED" || progress.status === "CANCELLED";

  const filing = useMutation({
    mutationFn: ({ refs, status }: { refs: string[]; status: TriageCompanyStatus }) =>
      assistantApi.acceptProposal(turnId, refs, status),
    onSuccess: async (result, { refs, status }) => {
      setFailure(null);
      // The mandate the proposal was authorised against, which is not necessarily the one on screen:
      // a conversation about one client can be open while the reader is looking at another.
      if (progress.proposal) await rowsChanged(progress.proposal.projectId);
      void queryClient.invalidateQueries({ queryKey: assistantApi.ASSISTANT_THREAD_KEY(threadId) });
      toast(outcomeLine({ status, refs, added: result.added, skipped: result.skipped }));
    },
    onError: (error) => setFailure(messageFor(error)),
  });

  const proposal = withOutcome(progress.proposal, filing.data, filing.variables);

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

      {proposal && !dismissed && (
        <div className="mt-3">
          <AssistantProposalCard
            proposal={proposal}
            running={running}
            filing={filing.isPending}
            onAccept={(refs, status) => filing.mutate({ refs, status })}
            onDismiss={() => setDismissed(true)}
          />
        </div>
      )}

      {failure && (
        <p role="alert" className="mt-2 font-sans text-[11.5px] text-red">
          {failure}
        </p>
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
 * What the card shows once this panel has filed it.
 *
 * <p>The server announces an acceptance as a `proposal.accepted` event, which only reaches a stream
 * that is still open — and a turn has to have finished before it can be filed at all, by which time
 * its stream has completed. So the outcome of *this* panel's own accept comes from the response to
 * it. A reload reads the stored one instead, which is why the stored value always wins.
 */
function withOutcome(
  proposal: AssistantProposal | null,
  result: { added: number; skipped: number } | undefined,
  filed: { refs: string[]; status: TriageCompanyStatus } | undefined,
): AssistantProposal | null {
  if (!proposal || proposal.accepted || !result || !filed) {
    return proposal;
  }
  return {
    ...proposal,
    accepted: { status: filed.status, refs: filed.refs, ...result },
  };
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
