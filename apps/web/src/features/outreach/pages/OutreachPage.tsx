import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { buttonClassName, EmptyState } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { useProjectStream } from "../../../lib/useProjectStream";
import { hasRoomForRails } from "../../../lib/viewport";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { rememberDrawerTab } from "../../candidates/lib/drawerTabs";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as conversationApi from "../api/conversationApi";
import type { ConversationList, ConversationScope, ConversationView } from "../api/conversationApi";
import { ConversationListPane } from "../components/ConversationListPane";
import { CONVERSATION_REFRESH_MS, ConversationPane } from "../components/ConversationPane";
import { MailboxBanner } from "../components/MailboxBanner";
import { MailboxChip } from "../components/MailboxChip";
import { MailboxSetupCard } from "../components/MailboxSetupCard";
import { NewEmailButton } from "../components/NewEmailButton";
import { OutreachHeader } from "../components/OutreachHeader";
import { mailboxStateOf, type MailboxState } from "../lib/mailboxState";
import { refreshOutreachActivity } from "../lib/outreachRefresh";
import { useMailbox } from "../lib/useMailbox";
import { useMailboxConnect, type MailboxConnect } from "../lib/useMailboxConnect";
import { positionPipelinePath } from "../../pipeline/lib/pipelinePath";

const VIEWS: readonly ConversationView[] = ["all", "needs", "scheduled", "sent"];
const SEARCH_DELAY_MS = 250;
/** While the stream is live: a read every five minutes catches what it does not announce. */
export const LIVE_SAFETY_REFRESH_MS = 5 * 60_000;

/**
 * A position's Outreach page (`claude-design/Outreach.dc.html`): everyone the team is in conversation with on the
 * left, and the one picked — the top of the list, where there is room beside it — with their history, the composer
 * and their details. Staff only; the sequences are the header's other view.
 */
export function OutreachPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const view = viewOf(params.get("view"));
  const who: ConversationScope = params.get("who") === "mine" ? "mine" : "team";
  const query = params.get("q") ?? "";
  const candidateId = params.get("candidate");

  const mailbox = useMailbox();
  const mailboxState = mailboxStateOf(mailbox);
  const connector = useMailboxConnect();
  const [searchText, setSearchText] = useState(query);
  const [recordId, setRecordId] = useState<string | null>(null);
  // Whom New email opened, so their composer takes the caret; a row click opens someone to read.
  const [writingTo, setWritingTo] = useState<string | null>(null);

  // Live, the server says when an email, a reply, a run or a task moved; the slow read is the safety net for what
  // it does not announce (a note, a meeting, a status), and the minute's read stands in whenever the stream is down.
  const streamIsLive = useProjectStream(project.id, (kinds) => {
    if (kinds.includes("outreach") || kinds.includes("task")) refreshOutreachActivity(queryClient, project.id);
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

  // Where the list and the conversation sit side by side, the top one opens; on a phone the list comes first. Never off a
  // read still in flight: that is the previous filter's list, or one still holding a row just handled.
  const firstCandidateId = list.isFetching ? null : (rows[0]?.candidateId ?? null);
  useEffect(() => {
    if (candidateId || !firstCandidateId || !hasRoomForRails()) return;
    changeParams((next) => next.set("candidate", firstCandidateId), true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidateId, firstCandidateId]);

  const select = (next: string | null) =>
    changeParams((params) => (next ? params.set("candidate", next) : params.delete("candidate")));

  const openToRead = (next: string) => {
    setWritingTo(null);
    select(next);
  };

  const openToWrite = (next: string) => {
    setWritingTo(next);
    select(next);
  };

  const showView = (next: ConversationView) =>
    changeParams((params) => {
      if (next === "all") params.delete("view");
      else params.set("view", next);
      params.delete("candidate");
    });

  const showEveryone = () =>
    changeParams((params) => {
      for (const key of ["view", "who", "q", "candidate"]) params.delete(key);
    });

  const showScope = (next: ConversationScope) =>
    changeParams((params) => {
      if (next === "team") params.delete("who");
      else params.set("who", next);
      params.delete("candidate");
    });

  // Only To do loses a handled row, so only there does the next one come up; anywhere else they stay open.
  const selectNextAfterHandled = () => {
    if (view !== "needs") return;
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
      <OutreachHeader projectId={project.id} section="conversations">
        <MailboxChip state={mailboxState} connector={connector} onAsked={() => void mailbox.refetch()} />
        <NewEmailButton projectId={project.id} mailbox={mailboxState} connector={connector} onPick={openToWrite} />
      </OutreachHeader>
      <MailboxBanner state={mailboxState} connector={connector} />

      <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)] lg:grid-cols-[clamp(260px,24vw,336px)_minmax(0,1fr)] xl:grid-cols-[clamp(260px,24vw,336px)_minmax(420px,1fr)_clamp(260px,22vw,320px)]">
        <div className={cn("min-h-0 flex-col border-e border-u-border", candidateId ? "hidden lg:flex" : "flex")}>
          <ConversationListPane
            view={view}
            who={who}
            query={searchText}
            list={list}
            selectedCandidateId={candidateId}
            onViewChange={showView}
            onQueryChange={setSearchText}
            onWhoChange={showScope}
            onSelect={openToRead}
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
            focusComposer={writingTo === candidateId}
            refreshMs={refreshMs}
          />
        ) : (
          <div className="hidden min-h-0 overflow-y-auto bg-u-surface lg:flex xl:col-span-2">
            <div className="m-auto">
              <NobodyOpen
                projectId={project.id}
                mailbox={mailboxState}
                connector={connector}
                list={list.data}
                view={view}
                who={who}
                query={query}
                onShowEveryone={showEveryone}
                onShowTeam={() => showScope("team")}
                onClearSearch={() => setSearchText("")}
              />
            </div>
          </div>
        )}
      </div>

      <CandidateDrawerById
        project={project}
        candidateId={recordId}
        onClose={() => setRecordId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: ["outreach", project.id] })}
      />
    </div>
  );
}

/**
 * The conversation's place while there is nobody to open: the way to a mailbox first, then what this list would hold
 * and the way back to everyone. With rows, the top one is opening.
 */
function NobodyOpen({
  projectId,
  mailbox,
  connector,
  list,
  view,
  who,
  query,
  onShowEveryone,
  onShowTeam,
  onClearSearch,
}: {
  projectId: string;
  mailbox: MailboxState;
  connector: MailboxConnect;
  list: ConversationList | undefined;
  view: ConversationView;
  who: ConversationScope;
  query: string;
  onShowEveryone: () => void;
  onShowTeam: () => void;
  onClearSearch: () => void;
}): ReactNode {
  if (mailbox.kind === "loading" || !list || list.rows.length > 0) return null;
  if (mailbox.kind === "none") {
    return <MailboxSetupCard providers={mailbox.providers} connector={connector} />;
  }
  if (mailbox.kind === "unavailable") {
    return (
      <EmptyState
        icon={<Icon d={ICONS.mail} size={22} />}
        title="Outreach email is off for your workspace"
        body="Ask Uncava support to turn it on."
      />
    );
  }
  if (query.trim()) {
    return (
      <EmptyState icon={<Icon d={ICONS.searchX} size={22} />} title={`No one matches “${query.trim()}”`} body="Try a name or an email.">
        <button type="button" onClick={onClearSearch} className={buttonClassName("secondary")}>
          Clear search
        </button>
      </EmptyState>
    );
  }
  if (view === "all" && who === "mine") {
    return (
      <EmptyState icon={<Icon d={ICONS.outreach} size={22} />} title="Nothing of yours yet" body="Conversations you start show up here.">
        <button type="button" onClick={onShowTeam} className={buttonClassName("secondary")}>
          Show the team’s
        </button>
      </EmptyState>
    );
  }
  if (view === "all") {
    return (
      <EmptyState
        icon={<Icon d={ICONS.outreach} size={22} />}
        title="No conversations yet"
        body="Write to someone with New email, or add people to a sequence from In universe or the Pipeline."
      >
        <div className="flex gap-2">
          <Link to={`/projects/${projectId}/companies/universe`} className={buttonClassName("secondary")}>
            In universe
          </Link>
          <Link to={positionPipelinePath(projectId)} className={buttonClassName("secondary")}>
            Pipeline
          </Link>
        </div>
      </EmptyState>
    );
  }
  const empty = EMPTY_FILTERS[view];
  return (
    <EmptyState icon={<Icon d={empty.icon} size={22} />} title={empty.title} body={empty.body}>
      <button type="button" onClick={onShowEveryone} className={buttonClassName("secondary")}>
        Show all conversations
      </button>
    </EmptyState>
  );
}

const EMPTY_FILTERS: Record<Exclude<ConversationView, "all">, { icon: string; title: string; body: string }> = {
  needs: { icon: ICONS.checkCircle, title: "All caught up", body: "Replies, follow-ups and bounced emails come up here." },
  scheduled: { icon: ICONS.clock, title: "Nothing scheduled", body: "Emails you schedule wait here until they go." },
  sent: { icon: ICONS.mail, title: "Nothing sent yet", body: "Emails show up here once they go." },
};

function viewOf(value: string | null): ConversationView {
  return VIEWS.find((view) => view === value) ?? "all";
}
