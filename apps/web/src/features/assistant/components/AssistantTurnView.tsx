import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import type { TriageCompanyStatus } from "../../triage/api/types";
import * as assistantApi from "../api/assistantApi";
import type { AssistantTurn, ProposalOutcome } from "../api/types";
import { AssistantAnswer } from "./AssistantAnswer";
import { AssistantProposalCard, outcomeLine } from "./AssistantProposalCard";
import { AssistantSteps } from "./AssistantSteps";

/** One question and its answer, with the card it proposed. Filing the card happens here. */
export function AssistantTurnView({ turn, projectId }: { turn: AssistantTurn; projectId: string }) {
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();
  const toast = useToast();

  const filing = useMutation({
    mutationFn: ({ ids, status }: { ids: string[]; status: TriageCompanyStatus }) =>
      assistantApi.acceptProposal(turn.id, ids, status),
    onSuccess: async (result, { status }) => {
      await rowsChanged(projectId);
      void queryClient.invalidateQueries({ queryKey: assistantApi.ASSISTANT_THREAD_KEY(turn.threadId) });
      toast(outcomeLine({ status, added: result.added, skipped: result.skipped }));
    },
  });

  // The stored outcome wins; until the thread refetches, this panel's own accept stands in for it.
  const outcome: ProposalOutcome | null =
    turn.proposalAccepted ??
    (filing.data && filing.variables
      ? { status: filing.variables.status, added: filing.data.added, skipped: filing.data.skipped }
      : null);

  return (
    <div>
      <QuestionBubble question={turn.question} />
      <AssistantSteps steps={turn.steps} />

      {turn.answer && <AssistantAnswer text={turn.answer} />}

      {turn.proposal && (
        <div className="mt-3">
          <AssistantProposalCard
            proposal={turn.proposal}
            outcome={outcome}
            filing={filing.isPending}
            onAccept={(ids, status) => filing.mutate({ ids, status })}
          />
        </div>
      )}

      {filing.isError && (
        <p role="alert" className="mt-2 font-sans text-[11.5px] text-red">
          {messageFor(filing.error)}
        </p>
      )}
    </div>
  );
}

export function QuestionBubble({ question }: { question: string }) {
  return (
    <div className="mb-3.5 flex justify-end">
      <div className="max-w-[86%] rounded-[12px_12px_3px_12px] bg-panel2 px-3 py-2 font-sans text-[13px] leading-[1.5] text-text">
        {question}
      </div>
    </div>
  );
}
