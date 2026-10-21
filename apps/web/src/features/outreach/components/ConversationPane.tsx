import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, LinesSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import type { PersonNoteKind } from "../../candidates/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskResponse } from "../../tasks/api/types";
import { useCandidateTasks, useTaskCompletion } from "../../tasks/components/NextActionBar";
import * as conversationApi from "../api/conversationApi";
import type { Conversation } from "../api/conversationApi";
import { historyRangeOf } from "../lib/conversationVocabulary";
import type { MailboxState } from "../lib/mailboxState";
import { firstNameOf } from "../lib/sequenceTokens";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { CandidateDetailsPane, StatusPill } from "./CandidateDetailsPane";
import { ConversationEventCard } from "./ConversationEventCard";
import { NextActionStrip } from "./NextActionStrip";
import { NoteComposerModal } from "./NoteComposerModal";
import { ReplyComposer } from "./ReplyComposer";

/** While the page is open, how often an executive's history is read again: replies arrive without a push yet. */
export const CONVERSATION_REFRESH_MS = 60_000;

/**
 * The middle pane of the Outreach page (`Outreach.dc.html`): one executive's conversation on the position — who
 * they are, the task the queue put them in for, everything that happened newest first, and the composer.
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
}: {
  projectId: string;
  candidateId: string;
  isArchived: boolean;
  mailbox: MailboxState;
  connector: MailboxConnect;
  /** After Mark handled: the queue moves on to the next person. */
  onHandled: () => void;
  /** Opens the person's full record in place; `activity` lands on its Activity tab. */
  onOpenRecord: (tab: "activity" | null) => void;
  /** Back to the queue, where only one pane fits. */
  onBack: () => void;
}) {
  const conversation = useQuery({
    queryKey: conversationApi.CONVERSATION_KEY(projectId, candidateId),
    queryFn: ({ signal }) => conversationApi.getConversation(projectId, candidateId, signal),
    refetchInterval: CONVERSATION_REFRESH_MS,
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
}) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { executive } = conversation;
  const candidateId = executive.candidateId;
  const firstName = firstNameOf(executive.fullName) ?? executive.fullName;
  const [noteKind, setNoteKind] = useState<PersonNoteKind | null>(null);
  const tasks = useCandidateTasks(projectId, candidateId);
  // A send closes their reply task on the server, after the dispatch, and nothing pushes that yet: the task read follows
  // every read of the conversation so the strip and Mark handled never act on a task already closed.
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
  const range = historyRangeOf(conversation.events);
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] });
  const details = <CandidateDetailsPane projectId={projectId} conversation={conversation} onOpenRecord={onOpenRecord} />;

  return (
    <>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-u-surface">
        <BackToQueue onBack={onBack} />
        <header className="flex flex-wrap items-start gap-3 border-b border-u-border px-5 py-3.5">
          <div className="min-w-0 flex-1 basis-56">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-subhead font-semibold text-u-text">{executive.fullName}</h2>
              <StatusPill label={executive.statusLabel} colour={executive.statusColour} />
            </div>
            <div className="mt-0.5 truncate text-note text-u-text3">
              {[executive.title, executive.companyName].filter(Boolean).join(" · ")}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-1.5">
            <Button variant="ghost" size="xs" onClick={() => setNoteKind("call")}>
              Log call
            </Button>
            <Button variant="ghost" size="xs" onClick={() => setNoteKind("general")}>
              Add note
            </Button>
            <Button
              variant="secondary"
              size="xs"
              disabled={!nextAction}
              title={nextAction ? undefined : "Nothing is open for them"}
              loading={nextAction !== null && completion.completingId === nextAction.id}
              onClick={() => nextAction && completion.complete(nextAction)}
            >
              Mark handled
            </Button>
          </div>
        </header>
        {nextAction && (
          <NextActionStrip task={nextAction} otherOpenCount={openTasks.length - 1} onOpenTasks={() => onOpenRecord(null)} />
        )}

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <div className="mb-3 flex items-baseline">
            <h3 className="type-micro-label text-u-text3">Communication history</h3>
            {range && <span className="ms-auto font-mono text-eyebrow text-u-text3">{range}</span>}
          </div>
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

        <ReplyComposer
          key={candidateId}
          projectId={projectId}
          conversation={conversation}
          mailbox={mailbox}
          connector={connector}
          isArchived={isArchived}
        />
        <NoteComposerModal
          projectId={projectId}
          candidateId={candidateId}
          fullName={executive.fullName}
          kind={noteKind}
          onClose={() => setNoteKind(null)}
          onSaved={refresh}
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
      Action queue
    </button>
  );
}

/** The task the queue put them in for: a reply to write first, then a bounce to fix, then the soonest. */
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
