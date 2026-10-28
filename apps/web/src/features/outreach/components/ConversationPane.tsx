import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LinesSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskResponse } from "../../tasks/api/types";
import { useCandidateTasks, useTaskCompletion } from "../../tasks/components/NextActionBar";
import * as conversationApi from "../api/conversationApi";
import type { Conversation } from "../api/conversationApi";
import type { MailboxState } from "../lib/mailboxState";
import { firstNameOf } from "../lib/sequenceTokens";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { CandidateDetailsPane, StatusPill } from "./CandidateDetailsPane";
import { ConversationDock } from "./ConversationDock";
import { ConversationEventCard } from "./ConversationEventCard";
import { ConversationGlance } from "./ConversationGlance";
import { NextActionStrip } from "./NextActionStrip";

/** While the page's stream is down, how often an executive's history is read again. */
export const CONVERSATION_REFRESH_MS = 60_000;

/**
 * The middle pane of the Outreach page (`Outreach.dc.html`): one executive's conversation on the position — the task
 * they are waiting on, everything that happened newest first, and the dock that writes to them or about them.
 */
export function ConversationPane({
  projectId,
  candidateId,
  isArchived,
  mailbox,
  connector,
  onHandled,
  onOpenRecord,
  onBack,
  focusComposer = false,
  refreshMs = CONVERSATION_REFRESH_MS,
}: {
  projectId: string;
  candidateId: string;
  isArchived: boolean;
  mailbox: MailboxState;
  connector: MailboxConnect;
  /** Opened to write to them: the caret starts in the composer. */
  focusComposer?: boolean;
  /** How often their history is read again: slowly while the page's stream says what moved. */
  refreshMs?: number;
  /** After Mark handled: To do moves on to the next person. */
  onHandled: () => void;
  /** Opens the person's full record in place; `activity` lands on its Activity tab. */
  onOpenRecord: (tab: "activity" | null) => void;
  /** Back to the list, where only one pane fits. */
  onBack: () => void;
}) {
  const conversation = useQuery({
    queryKey: conversationApi.CONVERSATION_KEY(projectId, candidateId),
    queryFn: ({ signal }) => conversationApi.getConversation(projectId, candidateId, signal),
    refetchInterval: refreshMs,
  });

  if (conversation.isError) {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <BackToQueue onBack={onBack} />
        <p role="alert" className="px-5 py-6 text-body text-u-text3">
          {messageFor(conversation.error)}
        </p>
      </div>
    );
  }
  if (conversation.isPending) {
    return (
      <div className="flex-1 px-5 py-6">
        <LinesSkeleton />
      </div>
    );
  }
  return (
    <LoadedConversation
      projectId={projectId}
      conversation={conversation.data}
      refreshedAt={conversation.dataUpdatedAt}
      isArchived={isArchived}
      mailbox={mailbox}
      connector={connector}
      onHandled={onHandled}
      onOpenRecord={onOpenRecord}
      onBack={onBack}
      focusComposer={focusComposer}
    />
  );
}

function LoadedConversation({
  projectId,
  conversation,
  refreshedAt,
  isArchived,
  mailbox,
  connector,
  onHandled,
  onOpenRecord,
  onBack,
  focusComposer,
}: {
  projectId: string;
  conversation: Conversation;
  /** When the conversation was last read: their tasks are read again with it. */
  refreshedAt: number;
  isArchived: boolean;
  mailbox: MailboxState;
  connector: MailboxConnect;
  onHandled: () => void;
  onOpenRecord: (tab: "activity" | null) => void;
  onBack: () => void;
  focusComposer: boolean;
}) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { executive } = conversation;
  const candidateId = executive.candidateId;
  const firstName = firstNameOf(executive.fullName) ?? executive.fullName;
  const tasks = useCandidateTasks(projectId, candidateId);
  // A send closes their reply task on the server, after the dispatch; while the stream is down nothing says so, so the
  // task read follows every read of the conversation and the strip and Mark handled never act on a task already closed.
  const firstRead = useRef(refreshedAt);
  useEffect(() => {
    if (refreshedAt === firstRead.current) return;
    void queryClient.invalidateQueries({ queryKey: tasksApi.CANDIDATE_TASKS_KEY(projectId, candidateId) });
  }, [refreshedAt, projectId, candidateId, queryClient]);
  const completion = useTaskCompletion(projectId, () => {
    void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
    onHandled();
  });
  const openTasks = tasks.data?.open ?? [];
  const nextAction = nextActionOf(openTasks);
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
  const details = <CandidateDetailsPane projectId={projectId} conversation={conversation} onOpenRecord={onOpenRecord} />;

  return (
    <>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-u-surface">
        <BackToQueue onBack={onBack} />
        <div className="flex items-center gap-2 border-b border-u-border px-5 py-2.5 xl:hidden">
          <h2 className="min-w-0 truncate text-body font-semibold text-u-text">{executive.fullName}</h2>
          <StatusPill label={executive.statusLabel} colour={executive.statusColour} />
        </div>
        {nextAction && (
          <NextActionStrip
            task={nextAction}
            otherOpenCount={openTasks.length - 1}
            isCompleting={completion.completingId === nextAction.id}
            onComplete={() => completion.complete(nextAction)}
            onOpenTasks={() => onOpenRecord(null)}
          />
        )}

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <ConversationGlance conversation={conversation} />
          {conversation.events.length === 0 ? (
            <p className="text-body text-u-text3">
              Nothing yet with {firstName} on this position. Write the first email below, or add them to a sequence.
            </p>
          ) : (
            <ol className="flex flex-col gap-2.5">
              {conversation.events.map((event) => (
                <ConversationEventCard
                  key={`${event.type}:${keyOf(event)}`}
                  event={event}
                  projectId={projectId}
                  firstName={firstName}
                  viewerUserId={user?.id ?? null}
                />
              ))}
            </ol>
          )}
          {conversation.truncated && (
            <p className="mt-3 text-note text-u-text3">
              Older history isn&rsquo;t shown here.{" "}
              <button type="button" className="text-u-accent hover:underline" onClick={() => onOpenRecord("activity")}>
                Open their record
              </button>
            </p>
          )}
          <div className="mt-5 xl:hidden">{details}</div>
        </div>

        <ConversationDock
          key={candidateId}
          projectId={projectId}
          conversation={conversation}
          mailbox={mailbox}
          connector={connector}
          isArchived={isArchived}
          focusComposer={focusComposer}
          onNoteSaved={refresh}
        />
      </div>
      <div className="hidden min-h-0 overflow-y-auto border-s border-u-border bg-u-bg xl:block">{details}</div>
    </>
  );
}

function BackToQueue({ onBack }: { onBack: () => void }) {
  return (
    <button
      type="button"
      onClick={onBack}
      className="inline-flex items-center gap-1 self-start px-5 pt-3 text-note text-u-text3 hover:text-u-text2 lg:hidden"
    >
      <Icon d={ICONS.arrowLeft} size={13} />
      Conversations
    </button>
  );
}

/** The task they are waiting on: a reply to write first, then a bounce to fix, then the soonest. */
function nextActionOf(openTasks: readonly TaskResponse[]): TaskResponse | null {
  return (
    openTasks.find((task) => task.rule === "reply") ??
    openTasks.find((task) => task.rule === "bounce") ??
    openTasks[0] ??
    null
  );
}

function keyOf(event: conversationApi.ConversationEvent): string {
  switch (event.type) {
    case "MESSAGE":
      return event.message.id;
    case "ACTIVITY":
      return String(event.activity.id);
    case "NOTE":
      return event.note.id;
    case "MEETING":
      return event.meeting.id;
  }
}
