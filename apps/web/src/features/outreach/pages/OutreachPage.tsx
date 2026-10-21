import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useOutletContext, useSearchParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { EmptyState } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { tabPanelProps } from "../../../components/ui/tabPanelProps";
import { useProjectStream } from "../../../lib/useProjectStream";
import { hasRoomForRails } from "../../../lib/viewport";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { rememberDrawerTab } from "../../candidates/lib/drawerTabs";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as conversationApi from "../api/conversationApi";
import type { ConversationScope, ConversationView } from "../api/conversationApi";
import { ActionQueue } from "../components/ActionQueue";
import { CONVERSATION_REFRESH_MS, ConversationPane } from "../components/ConversationPane";
import { EnrolDialog } from "../components/EnrolDialog";
import { MailboxBanner } from "../components/MailboxBanner";
import { MailboxChip } from "../components/MailboxChip";
import { OutreachTabs } from "../components/OutreachTabs";
import { mailboxStateOf } from "../lib/mailboxState";
import { useMailbox } from "../lib/useMailbox";
import { useMailboxConnect } from "../lib/useMailboxConnect";

const VIEWS: readonly ConversationView[] = ["needs", "all", "scheduled", "sent"];
const SEARCH_DELAY_MS = 250;
/** While the stream is live: a read every five minutes catches what it does not announce. */
export const LIVE_SAFETY_REFRESH_MS = 5 * 60_000;

/**
 * A position's Outreach page (`claude-design/Outreach.dc.html`): the conversation workspace — the action queue, one
 * executive's history with the composer, and their details — with the consultant's mailbox in the tab row. Staff
 * only; the sequences and everyone in them are a page beneath it.
 */
export function OutreachPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const view = viewOf(params.get("view"));
  const who: ConversationScope = params.get("who") === "team" ? "team" : "mine";
  const query = params.get("q") ?? "";
  const candidateId = params.get("candidate");

  const mailbox = useMailbox();
  const mailboxState = mailboxStateOf(mailbox);
  const connector = useMailboxConnect();
  const [searchText, setSearchText] = useState(query);
  const [isEnrolling, setIsEnrolling] = useState(false);
  const [recordId, setRecordId] = useState<string | null>(null);

  // Live, the server says when an email, a reply, a run or a task moved; the slow read is the safety net for what
  // it does not announce (a note, a meeting, a status), and the minute's read stands in whenever the stream is down.
  const streamIsLive = useProjectStream(project.id, (kinds) => {
    void queryClient.invalidateQueries({ queryKey: ["outreach", project.id] });
    if (kinds.includes("task")) void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
  });
  const refreshMs = streamIsLive ? LIVE_SAFETY_REFRESH_MS : CONVERSATION_REFRESH_MS;

  const list = useQuery({
    queryKey: conversationApi.CONVERSATIONS_KEY(project.id, view, who, query),
    queryFn: ({ signal }) => conversationApi.listConversations(project.id, view, who, query, signal),
    placeholderData: keepPreviousData,
    refetchInterval: refreshMs,
  });
  const rows = list.data?.rows ?? [];
  // The conversation pane's own read, shared through the cache: the open person may be in no row of this tab.
  const openConversation = useQuery({
    queryKey: conversationApi.CONVERSATION_KEY(project.id, candidateId ?? ""),
    queryFn: ({ signal }) => conversationApi.getConversation(project.id, candidateId!, signal),
    enabled: candidateId !== null,
    refetchInterval: refreshMs,
  });
  const selectedName =
    rows.find((row) => row.candidateId === candidateId)?.fullName ?? openConversation.data?.executive.fullName ?? null;

  const changeParams = (change: (next: URLSearchParams) => void, replace = false) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        change(next);
        return next;
      },
      { replace },
    );

  // The address can move without the box — Back, Forward, the nav's link — and the box follows it. A timer the old text
  // had set is cleared by the re-render this causes, before it can write the old text back.
  useEffect(() => {
    setSearchText(query);
  }, [query]);

  useEffect(() => {
    if (searchText === query) return;
    const timer = window.setTimeout(
      () => changeParams((next) => (searchText.trim() ? next.set("q", searchText) : next.delete("q")), true),
      SEARCH_DELAY_MS,
    );
    return () => window.clearTimeout(timer);
    // changeParams is rebuilt every render; the search text is what the timer follows.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchText, query]);

  // Where the queue and the conversation sit side by side, the first person is opened; on a phone the queue comes first.
  const firstCandidateId = rows[0]?.candidateId ?? null;
  useEffect(() => {
    if (candidateId || !firstCandidateId || !hasRoomForRails()) return;
    changeParams((next) => next.set("candidate", firstCandidateId), true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidateId, firstCandidateId]);

  const select = (next: string | null) =>
    changeParams((params) => (next ? params.set("candidate", next) : params.delete("candidate")));

  const selectNextAfterHandled = () => {
    const index = rows.findIndex((row) => row.candidateId === candidateId);
    const following = rows[index + 1] ?? rows[index - 1] ?? null;
    select(following && following.candidateId !== candidateId ? following.candidateId : null);
  };

  const openRecord = (tab: "activity" | null) => {
    if (tab) rememberDrawerTab("position", tab);
    setRecordId(candidateId);
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <h1 className="sr-only">Outreach</h1>
      <OutreachTabs
        projectId={project.id}
        view={view}
        counts={list.data?.counts ?? null}
        selectedName={selectedName}
        onViewChange={(next) => changeParams((params) => (next === "needs" ? params.delete("view") : params.set("view", next)))}
        onAddToSequence={() => setIsEnrolling(true)}
      >
        <MailboxChip state={mailboxState} connector={connector} onAsked={() => void mailbox.refetch()} />
      </OutreachTabs>
      {mailboxState.kind !== "ready" && mailboxState.kind !== "loading" && mailboxState.kind !== "refused" && (
        <div className="px-4 pt-3 md:px-6">
          <MailboxBanner state={mailboxState} connector={connector} />
        </div>
      )}

      <div className="grid min-h-0 flex-1 lg:grid-cols-[clamp(260px,24vw,336px)_minmax(0,1fr)] xl:grid-cols-[clamp(260px,24vw,336px)_minmax(420px,1fr)_clamp(260px,22vw,320px)]">
        <div
          className={cn("min-h-0 flex-col border-e border-u-border", candidateId ? "hidden lg:flex" : "flex")}
          {...tabPanelProps("outreach-view", view)}
        >
          <ActionQueue
            view={view}
            who={who}
            query={searchText}
            list={list}
            selectedCandidateId={candidateId}
            onQueryChange={setSearchText}
            onWhoChange={(next) => changeParams((params) => (next === "mine" ? params.delete("who") : params.set("who", next)))}
            onSelect={select}
            onShowScheduled={() => changeParams((params) => params.set("view", "scheduled"))}
          />
        </div>
        {candidateId ? (
          <ConversationPane
            key={candidateId}
            projectId={project.id}
            candidateId={candidateId}
            isArchived={Boolean(project.archivedAt)}
            mailbox={mailboxState}
            connector={connector}
            onHandled={selectNextAfterHandled}
            onOpenRecord={openRecord}
            onBack={() => select(null)}
            refreshMs={refreshMs}
          />
        ) : (
          <div className="hidden min-h-0 items-center justify-center bg-u-surface lg:flex xl:col-span-2">
            <EmptyState
              icon={<Icon d={ICONS.outreach} size={22} />}
              title={rows.length === 0 ? "No conversations here yet" : "Pick someone from the queue"}
              body={
                rows.length === 0
                  ? "Add people to a sequence from New outreach, In universe or the Pipeline, or open someone and write the first email."
                  : "Their emails, replies, notes and meetings on this position show here, newest first."
              }
            />
          </div>
        )}
      </div>

      {isEnrolling && candidateId && selectedName && (
        <EnrolDialog
          projectId={project.id}
          scope={{ candidateIds: [candidateId] }}
          source={selectedName}
          onClose={() => setIsEnrolling(false)}
        />
      )}
      <CandidateDrawerById
        project={project}
        candidateId={recordId}
        onClose={() => setRecordId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: ["outreach", project.id] })}
      />
    </div>
  );
}

function viewOf(value: string | null): ConversationView {
  return VIEWS.find((view) => view === value) ?? "needs";
}
