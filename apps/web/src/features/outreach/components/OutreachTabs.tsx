import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { TabList } from "../../../components/ui/TabList";
import type { ConversationList, ConversationView } from "../api/conversationApi";

/**
 * The Outreach page's tab row (`Outreach.dc.html`): Needs action, All conversations, Scheduled and Sent with their
 * counts, and on the right whatever the page puts there — the mailbox chip — and New outreach.
 */
export function OutreachTabs({
  projectId,
  view,
  counts,
  selectedName,
  onViewChange,
  onAddToSequence,
  children,
}: {
  projectId: string;
  view: ConversationView;
  counts: ConversationList["counts"] | null;
  /** The executive open in the conversation, whom New outreach can add to a sequence. */
  selectedName: string | null;
  onViewChange: (view: ConversationView) => void;
  onAddToSequence: () => void;
  children?: React.ReactNode;
}) {
  const tabs = [
    { value: "needs" as const, label: "Needs action", count: counts?.needs },
    { value: "all" as const, label: "All conversations" },
    { value: "scheduled" as const, label: "Scheduled", count: counts?.scheduled },
    { value: "sent" as const, label: "Sent" },
  ];
  return (
    <div className="flex flex-wrap items-end gap-x-4 gap-y-2 border-b border-u-border px-4 pt-1 md:px-6">
      <TabList label="Conversations" idPrefix="outreach-view" tabs={tabs} value={view} onChange={onViewChange} />
      <div className="ms-auto flex min-w-0 items-center gap-2 pb-2">
        {children}
        <Popover
          align="right"
          width={240}
          hasPopup="menu"
          triggerClassName="inline-flex items-center gap-1.5 rounded-u-control border border-u-accent-solid bg-u-accent-solid px-3 py-1.5 text-note font-medium text-white hover:bg-u-accent-solid-hover"
          trigger={() => (
            <>
              <Icon d={ICONS.plus} size={13} />
              New outreach
            </>
          )}
        >
          {(close) => (
            <div role="menu" className="flex flex-col p-1.5">
              {selectedName && (
                <button
                  type="button"
                  role="menuitem"
                  className="rounded-u-control px-2.5 py-2 text-start text-body text-u-text2 hover:bg-u-raised hover:text-u-text"
                  onClick={() => {
                    close();
                    onAddToSequence();
                  }}
                >
                  Add {selectedName} to a sequence
                </button>
              )}
              <Link
                role="menuitem"
                to={`/projects/${projectId}/outreach/sequences/new`}
                className="rounded-u-control px-2.5 py-2 text-body text-u-text2 hover:bg-u-raised hover:text-u-text"
              >
                New sequence
              </Link>
              <Link
                role="menuitem"
                to={`/projects/${projectId}/outreach/sequences`}
                className="rounded-u-control px-2.5 py-2 text-body text-u-text2 hover:bg-u-raised hover:text-u-text"
              >
                Sequences and everyone in them
              </Link>
            </div>
          )}
        </Popover>
      </div>
    </div>
  );
}
