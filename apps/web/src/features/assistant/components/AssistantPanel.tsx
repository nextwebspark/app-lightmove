import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import * as assistantApi from "../api/assistantApi";
import { useAssistant } from "../AssistantProvider";
import type { LiveStep } from "../api/types";
import { AssistantSteps } from "./AssistantSteps";
import { AssistantTurnView, QuestionBubble } from "./AssistantTurnView";

const STARTERS = [
  "Top 10 retail companies in the United Arab Emirates",
  "Largest oil & energy companies in Saudi Arabia",
  "Construction companies in Qatar with 500 to 5,000 staff",
];

const READING_STEP: LiveStep = { index: 0, label: "Reading your question", detail: null, done: false };

/**
 * The assistant, docked beside the page rather than over it: `role="complementary"` with no scrim,
 * so the grid next to it stays usable while it is open.
 *
 * <p>One chat at a time. Asking waits for the whole answer — the model searches and proposes inside
 * that one request — and the chat is then read back from the server.
 */
export function AssistantPanel({ contextLabel, projectId }: { contextLabel: string; projectId: string }) {
  const { open, toggledByUser, closeAssistant, threadIdFor, showThread } = useAssistant();
  const threadId = threadIdFor(projectId);
  const [draft, setDraft] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);
  const composer = useRef<HTMLTextAreaElement>(null);
  const queryClient = useQueryClient();

  const thread = useQuery({
    queryKey: assistantApi.ASSISTANT_THREAD_KEY(threadId ?? ""),
    queryFn: () => assistantApi.getThread(threadId!),
    enabled: Boolean(threadId),
  });
  const turns = thread.data?.turns ?? [];

  const history = useQuery({
    queryKey: assistantApi.ASSISTANT_THREADS_KEY(projectId),
    queryFn: () => assistantApi.listThreads(projectId),
    enabled: historyOpen,
  });

  const [liveSteps, setLiveSteps] = useState<LiveStep[]>([]);
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null);
  const scroller = useRef<HTMLDivElement>(null);

  const asking = useMutation({
    mutationFn: (question: string) => {
      setLiveSteps([]);
      setPendingQuestion(question);
      return assistantApi.ask(projectId, question, threadId, (step) =>
        setLiveSteps((current) => [...current.filter((held) => held.index !== step.index), step]
          .sort((left, right) => left.index - right.index)),
      );
    },
    // The pending bubble and the saved turn swap in one synchronous block, so React draws them in one
    // frame: the question is never shown twice and the layout does not jump.
    onSuccess: async (turn) => {
      const fresh = await assistantApi.getThread(turn.threadId);
      setPendingQuestion(null);
      queryClient.setQueryData(assistantApi.ASSISTANT_THREAD_KEY(turn.threadId), fresh);
      showThread(projectId, turn.threadId);
      void queryClient.invalidateQueries({ queryKey: assistantApi.ASSISTANT_THREADS_KEY(projectId) });
    },
    onError: () => setPendingQuestion(null),
  });

  const handleSend = () => {
    const question = draft.trim();
    if (!question || asking.isPending) return;
    setDraft("");
    asking.mutate(question);
  };

  const handleOpenThread = (id: string | null) => {
    setHistoryOpen(false);
    asking.reset();
    setPendingQuestion(null);
    showThread(projectId, id);
  };

  useEscapeKey(open, closeAssistant);

  const lastTurnId = turns.at(-1)?.id;
  useEffect(() => {
    const list = scroller.current;
    list?.scrollTo?.({ top: list.scrollHeight, behavior: "smooth" });
  }, [pendingQuestion, liveSteps.length, lastTurnId, thread.data]);

  // Guarded on the toggle so restoring a remembered open panel never steals the caret.
  useEffect(() => {
    if (open && toggledByUser) composer.current?.focus();
  }, [open, toggledByUser]);

  const empty = turns.length === 0 && !pendingQuestion;

  return (
    <aside
      role="complementary"
      aria-label="Uncava Assistant"
      className="ms-2.5 flex h-full w-[400px] flex-none flex-col overflow-hidden rounded-[10px] border border-line bg-panel"
    >
      <div className="flex-none border-b border-line px-3 py-2.5">
        <div className="flex items-center gap-2">
          <span className="grid h-[26px] w-[26px] flex-none place-items-center rounded-[7px] bg-[linear-gradient(135deg,var(--color-ai),var(--color-ai2))] text-white">
            <Icon d={ICONS.sparkle} size={14} />
          </span>
          <span className="font-sans text-[13px] font-semibold text-text">Assistant</span>
          <button
            type="button"
            onClick={() => setHistoryOpen((current) => !current)}
            aria-expanded={historyOpen}
            className={cn(
              "ms-auto rounded-md px-2 py-1 font-sans text-[11.5px] text-text2 transition hover:bg-panel2 hover:text-text",
              historyOpen && "bg-panel2 text-text",
            )}
          >
            History
          </button>
          <button
            type="button"
            onClick={() => handleOpenThread(null)}
            title="New chat"
            aria-label="New chat"
            className="grid h-7 w-7 place-items-center rounded-md text-text3 transition hover:bg-panel2 hover:text-text"
          >
            <Icon d={ICONS.plus} size={15} />
          </button>
          <button
            type="button"
            onClick={closeAssistant}
            title="Close"
            aria-label="Close the assistant"
            className="grid h-7 w-7 place-items-center rounded-md text-text3 transition hover:bg-panel2 hover:text-text"
          >
            <Icon d={ICONS.close} size={15} />
          </button>
        </div>

        <div className="mt-2.5 flex w-fit max-w-full items-center gap-1.5 rounded-md bg-panel2 px-2 py-1 font-mono text-[11px] text-text3">
          <Icon d="M12 2 3 7l9 5 9-5-9-5Z" size={11} className="flex-none text-amber" />
          <span className="truncate">{contextLabel}</span>
        </div>
      </div>

      {historyOpen && (
        <div className="flex-none border-b border-line bg-panel2 px-2 py-2">
          <p className="px-1.5 pb-1 font-mono text-[10.5px] uppercase tracking-[0.04em] text-text3">
            Chats in this project
          </p>
          {history.isLoading && <p className="px-1.5 py-1 font-sans text-xs text-text3">Loading…</p>}
          {history.isError && (
            <p role="alert" className="px-1.5 py-1 font-sans text-xs text-red">
              {messageFor(history.error)}
            </p>
          )}
          {history.data?.length === 0 && (
            <p className="px-1.5 py-1 font-sans text-xs text-text3">No chats yet.</p>
          )}
          <ul className="max-h-[220px] overflow-y-auto">
            {history.data?.map((summary) => (
              <li key={summary.id}>
                <button
                  type="button"
                  onClick={() => handleOpenThread(summary.id)}
                  className={cn(
                    "block w-full truncate rounded-md px-1.5 py-1.5 text-start font-sans text-xs text-text2 transition hover:bg-panel hover:text-text",
                    summary.id === threadId && "bg-panel font-medium text-text",
                  )}
                >
                  {summary.title}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div ref={scroller} className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-3 py-4">
        {thread.isLoading && <p className="my-auto text-center font-mono text-[11px] text-text3">Opening the chat…</p>}
        {thread.isError && (
          <p role="alert" className="font-sans text-[11.5px] text-red">
            {messageFor(thread.error)}
          </p>
        )}

        {turns.map((turn) => (
          <div key={turn.id}>
            <AssistantTurnView turn={turn} projectId={projectId} />
          </div>
        ))}

        {pendingQuestion && (
          <div>
            <QuestionBubble question={pendingQuestion} />
            <AssistantSteps steps={liveSteps.length > 0 ? liveSteps : [READING_STEP]} />
          </div>
        )}

        {asking.isError && (
          <p role="alert" className="font-sans text-[11.5px] text-red">
            {messageFor(asking.error)}
          </p>
        )}

        {empty && !threadId && (
          <div className="my-auto">
            <div className="mb-4 text-center">
              <p className="font-sans text-[13px] text-text2">Find companies for this mandate.</p>
              <p className="mt-1 font-mono text-[11px] text-text3">
                It searches the company universe and lets you add what it finds.
              </p>
            </div>
            {STARTERS.map((starter) => (
              <button
                key={starter}
                type="button"
                onClick={() => setDraft(starter)}
                className="mb-1.5 block w-full rounded-lg border border-dashed border-line px-2.5 py-2 text-start font-sans text-xs text-text2 transition hover:border-solid hover:border-ai hover:bg-ai-soft hover:text-text"
              >
                {starter}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="flex-none border-t border-line px-3 pb-3 pt-2.5">
        <div className="rounded-[10px] border border-line bg-panel2 px-2.5 py-2">
          <textarea
            ref={composer}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                handleSend();
              }
            }}
            placeholder={asking.isPending ? "Answering…" : "Ask for companies…"}
            rows={2}
            aria-label="Ask the assistant"
            className="w-full resize-none border-none bg-transparent font-sans text-[13px] leading-[1.5] text-text outline-none"
          />
          <div className="mt-1 flex items-center gap-2">
            <span className="font-mono text-[10px] text-text3">
              {asking.isPending ? "Answering…" : "Enter to send"}
            </span>
            <button
              type="button"
              onClick={handleSend}
              disabled={!draft.trim() || asking.isPending}
              aria-label="Send"
              title="Send"
              className="ms-auto grid h-[26px] w-[26px] place-items-center rounded-md border-none bg-[linear-gradient(135deg,var(--color-ai),var(--color-ai2))] transition disabled:opacity-40"
            >
              <Icon d={ICONS.arrowUp} size={13} className="text-white" />
            </button>
          </div>
        </div>
      </div>
    </aside>
  );
}
