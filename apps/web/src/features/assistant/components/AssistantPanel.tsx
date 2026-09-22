import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { messageFor } from "../../../lib/errorCodes";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import * as assistantApi from "../api/assistantApi";
import { useAssistant } from "../AssistantProvider";
import { progressOf, useAssistantTurn } from "../lib/useAssistantTurn";
import { AssistantTurnView } from "./AssistantTurnView";

const STARTERS = [
  "Top 10 IPP operators in Saudi Arabia",
  "Which companies in my universe have no executives mapped?",
  "Show the market by country for this filter",
];

/**
 * The assistant, docked beside the page rather than over it.
 *
 * <p><b>Not a {@link Drawer}.</b> That one is `role="dialog"` with `aria-modal` behind a scrim,
 * which is right for reading one record and wrong here: the point is to have the assistant open
 * *while* you tick rows in the grid next to it. A scrim would make the page unclickable and
 * `aria-modal` would tell a screen reader the rest of it does not exist. So this is
 * `role="complementary"`, with no scrim and no focus trap, and the page underneath stays live.
 *
 * <p>It holds no state worth keeping — {@link AssistantProvider} does — so remounting it as the
 * user crosses between layouts costs nothing.
 *
 * <p>It is also a fixed 400px and knows nothing about being open or shut: {@link AssistantDock} is
 * the slot that animates around it, and a panel whose own width moved would reflow its contents on
 * every frame of that.
 */
export function AssistantPanel({
  contextLabel,
  projectId,
}: {
  contextLabel: string;
  projectId: string | null;
}) {
  const { open, toggledByUser, closeAssistant, threadId, turnId, question, startedTurn, forgetThread } =
    useAssistant();
  const [draft, setDraft] = useState("");
  const [failure, setFailure] = useState<string | null>(null);
  const composer = useRef<HTMLTextAreaElement>(null);
  const transcript = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const progress = useAssistantTurn(turnId);
  const running = Boolean(turnId) && progress.status === "RUNNING";

  // The conversation already open, which is what stops a second question replacing the first. The
  // live turn is drawn from the stream instead, so it appears without waiting for this to refetch.
  const thread = useQuery({
    queryKey: assistantApi.ASSISTANT_THREAD_KEY(threadId ?? ""),
    queryFn: () => assistantApi.getThread(threadId!),
    enabled: Boolean(threadId),
  });
  const past = (thread.data?.turns ?? []).filter((turn) => turn.id !== turnId);

  const asking = useMutation({
    mutationFn: (asked: string) =>
      threadId ? assistantApi.askIn(threadId, asked) : assistantApi.ask(asked, projectId),
    onSuccess: (turn) => startedTurn({ id: turn.id, threadId: turn.threadId, question: turn.question }),
    onError: (error) => setFailure(messageFor(error)),
  });

  const handleSend = () => {
    const asked = draft.trim();
    if (!asked || running || asking.isPending) return;
    setFailure(null);
    setDraft("");
    asking.mutate(asked);
  };

  useEscapeKey(open, closeAssistant);

  // A remembered thread the server will not open is one this user can no longer reach — deleted,
  // or left behind in a workspace they have moved out of. Falling back to the starters is the whole
  // recovery; keeping a thread id that 404s would fail every question asked into it.
  useEffect(() => {
    if (thread.isError && !turnId) forgetThread();
  }, [thread.isError, turnId, forgetThread]);

  // A finished turn stops being the stream's and becomes the thread's, carrying its answer and its
  // proposal with it. Nothing on screen changes; the source underneath it does.
  useEffect(() => {
    if (!threadId || !turnId || progress.status === "RUNNING") return;
    void queryClient.invalidateQueries({ queryKey: assistantApi.ASSISTANT_THREAD_KEY(threadId) });
  }, [threadId, turnId, progress.status, queryClient]);

  // A new question that appears above the fold reads as nothing having happened.
  useEffect(() => {
    if (transcript.current) transcript.current.scrollTop = transcript.current.scrollHeight;
  }, [turnId]);

  // The composer rather than the close button: somebody who just pressed "AI Research" wants to
  // type. Guarded on the toggle so restoring a remembered panel never steals the caret.
  useEffect(() => {
    if (open && toggledByUser) composer.current?.focus();
  }, [open, toggledByUser]);

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
            onClick={closeAssistant}
            title="Close"
            aria-label="Close the assistant"
            className="ms-auto grid h-7 w-7 place-items-center rounded-md text-text3 transition hover:bg-panel2 hover:text-text"
          >
            <Icon d={ICONS.close} size={15} />
          </button>
        </div>

        {/* Which mandate the conversation is about. A thread keeps the context it was asked in, so
            this states the screen's, not wherever the reader has since navigated. */}
        <div className="mt-2.5 flex w-fit max-w-full items-center gap-1.5 rounded-md bg-panel2 px-2 py-1 font-mono text-[11px] text-text3">
          <Icon d="M12 2 3 7l9 5 9-5-9-5Z" size={11} className="flex-none text-amber" />
          <span className="truncate">{contextLabel}</span>
        </div>
      </div>

      <div ref={transcript} className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-3 py-4">
        {threadId ? (
          <>
            {thread.isLoading && !turnId && (
              <p className="my-auto text-center font-mono text-[11px] text-text3">
                Opening the conversation…
              </p>
            )}
            {past.map((turn) => (
              <AssistantTurnView
                key={turn.id}
                turnId={turn.id}
                threadId={turn.threadId}
                question={turn.question}
                progress={progressOf(turn)}
              />
            ))}
            {turnId && (
              <AssistantTurnView
                turnId={turnId}
                threadId={threadId}
                question={question}
                progress={progress}
              />
            )}
          </>
        ) : (
          <div className="my-auto">
            <div className="mb-4 text-center">
              <p className="font-sans text-[13px] text-text2">Ask about this market.</p>
              <p className="mt-1 font-mono text-[11px] text-text3">
                It reads the company universe and this mandate&apos;s own rows.
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
        {failure && (
          <p role="alert" className="font-sans text-[11.5px] text-red">
            {failure}
          </p>
        )}
      </div>

      <div className="flex-none border-t border-line px-3 pb-3 pt-2.5">
        <div className="rounded-[10px] border border-line bg-panel2 px-2.5 py-2">
          <textarea
            ref={composer}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              // Enter sends, Shift+Enter breaks the line — a question is usually one line, and a
              // composer that needs a mouse to send reads as a form rather than a conversation.
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                handleSend();
              }
            }}
            placeholder="Ask about this market..."
            rows={2}
            aria-label="Ask the assistant"
            className="w-full resize-none border-none bg-transparent font-sans text-[13px] leading-[1.5] text-text outline-none"
          />
          <div className="mt-1 flex items-center gap-2">
            <span className="font-mono text-[10px] text-text3">
              {running ? "Answering…" : "Enter to send"}
            </span>
            <button
              type="button"
              onClick={handleSend}
              disabled={!draft.trim() || running || asking.isPending}
              aria-label="Send"
              title={running ? "Wait for the current answer" : "Send"}
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
