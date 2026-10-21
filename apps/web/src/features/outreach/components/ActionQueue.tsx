import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, LinesSkeleton, SegmentedControl } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { ConversationList, ConversationRow, ConversationScope, ConversationView } from "../api/conversationApi";
import { QUEUE_GROUPS, queueTimeOf, TONE_DOT, toneOfGroup } from "../lib/conversationVocabulary";
import { sendTimeOf } from "../lib/runVocabulary";

const SCOPES = [
  { value: "mine", label: "Mine" },
  { value: "team", label: "Team" },
] as const;

/**
 * The Outreach page's left pane (`Outreach.dc.html`): who needs something, grouped on Needs action — a reply to
 * write, a follow-up, preparation, a delivery problem — and listed by time on the other tabs, Mine or Team, with a
 * search over names and what was sent.
 */
export function ActionQueue({
  view,
  who,
  query,
  list,
  selectedCandidateId,
  onQueryChange,
  onWhoChange,
  onSelect,
  onShowScheduled,
}: {
  view: ConversationView;
  who: ConversationScope;
  query: string;
  list: { data?: ConversationList; isError: boolean; isPending: boolean; error: unknown };
  selectedCandidateId: string | null;
  onQueryChange: (query: string) => void;
  onWhoChange: (who: ConversationScope) => void;
  onSelect: (candidateId: string) => void;
  onShowScheduled: () => void;
}) {
  const rows = list.data?.rows ?? [];
  return (
    <section aria-label="Action queue" className="flex min-h-0 flex-col bg-u-bg">
      <div className="flex items-baseline px-4 pt-3.5">
        <h2 className="text-body font-semibold text-u-text">Action queue</h2>
        {list.data && view === "needs" && (
          <span className="ms-auto font-mono text-meta text-u-text3">{list.data.counts.needs} open</span>
        )}
      </div>
      <div className="flex flex-col gap-2 px-4 pb-3 pt-2.5">
        <label className="flex items-center gap-2 rounded-u-control border border-u-border bg-u-surface px-2.5 py-1.5">
          <Icon d={ICONS.search} size={13} className="flex-none text-u-text3" />
          <input
            type="search"
            aria-label="Search people or messages"
            placeholder="Search people or messages…"
            value={query}
            maxLength={100}
            onChange={(event) => onQueryChange(event.target.value)}
            className="min-w-0 flex-1 bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
          />
        </label>
        <div className="flex items-center gap-2">
          <SegmentedControl label="Whose" options={SCOPES} value={who} onChange={onWhoChange} />
          {view === "needs" && (
            <span className="ms-auto inline-flex items-center gap-1 text-meta text-u-text3">
              <Icon d={ICONS.filter} size={12} />
              Priority
            </span>
          )}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto border-t border-u-border">
        {list.isError ? (
          <p role="alert" className="px-4 py-4 text-note text-u-text3">
            {messageFor(list.error)}
          </p>
        ) : list.isPending ? (
          <div className="px-4 py-4">
            <LinesSkeleton />
          </div>
        ) : rows.length === 0 ? (
          <p className="px-4 py-4 text-note text-u-text3">{emptyLineOf(view, query)}</p>
        ) : view === "needs" ? (
          QUEUE_GROUPS.map(({ group, label }) => {
            const inGroup = rows.filter((row) => row.group === group);
            if (inGroup.length === 0) return null;
            return (
              <div key={group}>
                <div className="flex items-center gap-2 px-4 pb-1 pt-3">
                  <h3 className="type-micro-label text-u-text3">{label}</h3>
                  <span className="rounded-u-control bg-u-raised px-1.5 font-mono text-eyebrow text-u-text2">
                    {inGroup.length}
                  </span>
                </div>
                <QueueRows rows={inGroup} selectedCandidateId={selectedCandidateId} onSelect={onSelect} />
              </div>
            );
          })
        ) : (
          <QueueRows rows={rows} selectedCandidateId={selectedCandidateId} onSelect={onSelect} />
        )}
        {list.data?.truncated && (
          <p className="px-4 py-3 text-meta text-u-text3">Only the first 500 are listed. Search to narrow them.</p>
        )}
      </div>

      {list.data && list.data.counts.scheduled > 0 && (
        <button
          type="button"
          onClick={onShowScheduled}
          className="flex items-center gap-2 border-t border-u-border px-4 py-2.5 text-start text-meta text-u-text3 hover:text-u-text2"
        >
          <Icon d={ICONS.clock} size={12} />
          <span className="min-w-0 flex-1 truncate">
            {list.data.counts.scheduled} scheduled
            {list.data.nextScheduledAt && <> · {sendTimeOf(list.data.nextScheduledAt)}</>}
          </span>
          <Icon d={ICONS.arrowRight} size={12} />
        </button>
      )}
    </section>
  );
}

function QueueRows({
  rows,
  selectedCandidateId,
  onSelect,
}: {
  rows: readonly ConversationRow[];
  selectedCandidateId: string | null;
  onSelect: (candidateId: string) => void;
}) {
  return (
    <ul>
      {rows.map((row) => (
        <li key={row.candidateId}>
          <QueueRow row={row} isSelected={row.candidateId === selectedCandidateId} onSelect={onSelect} />
        </li>
      ))}
    </ul>
  );
}

function QueueRow({
  row,
  isSelected,
  onSelect,
}: {
  row: ConversationRow;
  isSelected: boolean;
  onSelect: (candidateId: string) => void;
}) {
  const time = queueTimeOf(row);
  const role = [row.title, row.companyName].filter(Boolean).join(" · ");
  return (
    <button
      type="button"
      aria-current={isSelected ? "true" : undefined}
      onClick={() => onSelect(row.candidateId)}
      className={cn(
        "flex w-full gap-2.5 px-4 py-2.5 text-start hover:bg-u-raised",
        isSelected && "bg-u-raised",
      )}
    >
      <Avatar id={row.personId} name={row.fullName} size="md" className="mt-0.5 flex-none" />
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline gap-2">
          <span className="min-w-0 flex-1 truncate text-note font-semibold text-u-text">{row.fullName}</span>
          {time.text && (
            <span className={cn("flex-none font-mono text-eyebrow", time.isLate ? "text-u-offlimits" : "text-u-text3")}>
              {time.text}
            </span>
          )}
        </span>
        {role && <span className="block truncate text-meta text-u-text3">{role}</span>}
        {row.action && (
          <span className="mt-0.5 flex items-center gap-1.5 text-meta text-u-text2">
            <span aria-hidden="true" className={cn("size-1 flex-none rounded-full", TONE_DOT[toneOfGroup(row.group)])} />
            <span className="min-w-0 truncate">{row.action.title}</span>
          </span>
        )}
        {row.latest?.preview && <span className="block truncate text-meta text-u-text3">{row.latest.preview}</span>}
      </span>
    </button>
  );
}

function emptyLineOf(view: ConversationView, query: string): string {
  if (query.trim()) return "Nothing matches that search.";
  switch (view) {
    case "needs":
      return "Nothing needs you right now.";
    case "all":
      return "No conversations on this position yet. Start one from New outreach.";
    case "scheduled":
      return "Nothing is scheduled.";
    case "sent":
      return "Nothing has been sent from this position yet.";
  }
}
