import { LinesSkeleton, SegmentedControl } from "../../../components/ui";
import { PersonAvatar } from "../../candidates/components/pool/PersonAvatar";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { ConversationList, ConversationRow, ConversationScope, ConversationView } from "../api/conversationApi";
import { QUEUE_GROUPS, queueTimeOf, TONE_DOT, toneOfGroup } from "../lib/conversationVocabulary";

const SCOPES = [
  { value: "team", label: "Team" },
  { value: "mine", label: "Mine" },
] as const;

const FILTERS: readonly { view: ConversationView; label: string }[] = [
  { view: "all", label: "All" },
  { view: "needs", label: "To do" },
  { view: "scheduled", label: "Scheduled" },
  { view: "sent", label: "Sent" },
];

/**
 * The Outreach page's left pane: everyone on the position the team is in conversation with, newest first, narrowed
 * to what is to do — grouped by what it is — scheduled or sent, Team or Mine, narrowed by the header's search over names and emails.
 */
export function ConversationListPane({
  view,
  who,
  query,
  list,
  selectedCandidateId,
  onViewChange,
  onWhoChange,
  onSelect,
}: {
  view: ConversationView;
  who: ConversationScope;
  /** The header's search, which the empty line answers to. */
  query: string;
  list: { data?: ConversationList; isError: boolean; isPending: boolean; error: unknown };
  selectedCandidateId: string | null;
  onViewChange: (view: ConversationView) => void;
  onWhoChange: (who: ConversationScope) => void;
  onSelect: (candidateId: string) => void;
}) {
  const rows = list.data?.rows ?? [];
  return (
    <section aria-label="Conversations" className="flex min-h-0 flex-1 flex-col bg-u-bg">
      <div className="flex items-center gap-2 px-4 pt-3">
        <h2 className="text-body font-semibold text-u-text">Conversations</h2>
        <SegmentedControl label="Whose" options={SCOPES} value={who} onChange={onWhoChange} className="ms-auto" />
      </div>
      <div className="flex flex-col gap-2.5 px-4 pb-3 pt-2.5">
        <div role="group" aria-label="Show" className="flex flex-wrap gap-1.5">
          {FILTERS.map((filter) => {
            const isOn = filter.view === view;
            const count = countOf(filter.view, list.data);
            return (
              <button
                key={filter.view}
                type="button"
                aria-pressed={isOn}
                onClick={() => onViewChange(filter.view)}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-note font-medium transition",
                  isOn ? "border-u-accent bg-u-accent-tint text-u-text" : "border-u-border text-u-text2 hover:text-u-text",
                )}
              >
                {filter.label}
                {count > 0 && <span className="font-mono text-meta text-u-text3">{count}</span>}
              </button>
            );
          })}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto border-t border-u-border">
        {list.isError && !list.data ? (
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
      <PersonAvatar person={row} size="md" className="mt-0.5 flex-none" />
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

function countOf(view: ConversationView, data: ConversationList | undefined): number {
  if (!data) return 0;
  if (view === "needs") return data.counts.needs;
  if (view === "scheduled") return data.counts.scheduled;
  return 0;
}

function emptyLineOf(view: ConversationView, query: string): string {
  if (query.trim()) return "No matches.";
  switch (view) {
    case "all":
      return "No conversations yet.";
    case "needs":
      return "Nothing to do.";
    case "scheduled":
      return "Nothing scheduled.";
    case "sent":
      return "Nothing sent yet.";
  }
}
