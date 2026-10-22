import { useQuery } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, LinesSkeleton } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as pipelineApi from "../../pipeline/api/pipelineApi";
import type { PipelineRow } from "../../pipeline/api/types";
import type { MailboxState } from "../lib/mailboxState";
import type { MailboxConnect } from "../lib/useMailboxConnect";
import { ConnectFirstButton, mailboxBlockerOf } from "./ConnectFirstButton";
import { OUTREACH_PRIMARY_ACTION } from "./OutreachHeader";

/** Who is offered before anything is typed: the newest on the position, the likeliest first approach. */
const SUGGESTED = 5;
/** Matches drawn at once; a position of hundreds is narrowed by typing, never by scrolling. */
const SHOWN = 8;

/**
 * The Conversations view's action: type anyone on the position — however many it maps — and their conversation opens
 * ready to write. While no mailbox can send, it offers the connection instead.
 */
export function NewEmailButton({
  projectId,
  mailbox,
  connector,
  onPick,
}: {
  projectId: string;
  mailbox: MailboxState;
  connector: MailboxConnect;
  onPick: (candidateId: string) => void;
}) {
  const label = (
    <>
      <Icon d={ICONS.plus} size={13} />
      New email
    </>
  );
  if (mailbox.kind === "unavailable") return null;
  const blocker = mailboxBlockerOf(mailbox);
  if (blocker) {
    return (
      <ConnectFirstButton state={blocker} connector={connector}>
        {label}
      </ConnectFirstButton>
    );
  }
  return (
    <Popover
      align="right"
      width={340}
      hasPopup="dialog"
      disabled={mailbox.kind === "loading"}
      triggerClassName={cn(OUTREACH_PRIMARY_ACTION, "disabled:opacity-50")}
      trigger={() => label}
    >
      {(close) => (
        <PersonPicker
          projectId={projectId}
          onPick={(candidateId) => {
            close();
            onPick(candidateId);
          }}
        />
      )}
    </Popover>
  );
}

function PersonPicker({ projectId, onPick }: { projectId: string; onPick: (candidateId: string) => void }) {
  const listId = useId();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const people = useQuery({
    queryKey: pipelineApi.PIPELINE_KEY(projectId, pipelineApi.NO_PIPELINE_QUERY),
    queryFn: ({ signal }) => pipelineApi.getPipeline(projectId, pipelineApi.NO_PIPELINE_QUERY, signal),
  });
  const everyone = people.data?.rows ?? [];
  const typed = query.trim().toLowerCase();
  const matching = typed ? everyone.filter((row) => matches(row, typed)) : newestFirst(everyone);
  const shown = matching.slice(0, typed ? SHOWN : SUGGESTED);
  const highlighted = Math.min(active, shown.length - 1);

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive(Math.min(highlighted + 1, shown.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive(Math.max(highlighted - 1, 0));
    } else if (event.key === "Enter" && shown[highlighted]) {
      event.preventDefault();
      onPick(shown[highlighted].candidateId);
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label className="flex items-center gap-2 rounded-u-control border border-u-border bg-u-surface px-2.5 py-1.5">
        <span className="flex-none text-note text-u-text3">To</span>
        <input
          type="text"
          role="combobox"
          autoFocus
          aria-label="Who are you writing to?"
          aria-expanded={shown.length > 0}
          aria-controls={listId}
          aria-activedescendant={shown[highlighted] ? `${listId}-${shown[highlighted].candidateId}` : undefined}
          aria-autocomplete="list"
          placeholder="Name, title or company"
          value={query}
          maxLength={100}
          onChange={(event) => {
            setQuery(event.target.value);
            setActive(0);
          }}
          onKeyDown={handleKeyDown}
          className="min-w-0 flex-1 bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
        />
      </label>
      {people.isError ? (
        <p role="alert" className="px-1 py-2 text-note text-u-text3">
          {messageFor(people.error)}
        </p>
      ) : people.isPending ? (
        <LinesSkeleton className="px-1 py-2" />
      ) : shown.length === 0 ? (
        <p className="px-1 py-2 text-note text-u-text3">
          {typed ? "No one on this position matches." : "Nobody on this position yet."}
        </p>
      ) : (
        <>
          {!typed && <p className="px-2 pt-1 type-micro-label text-u-text3">Recently added</p>}
          <ul id={listId} role="listbox" aria-label="People on this position" className="flex flex-col">
            {shown.map((row, index) => (
              <li
                key={row.candidateId}
                id={`${listId}-${row.candidateId}`}
                role="option"
                aria-selected={index === highlighted}
                onMouseEnter={() => setActive(index)}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => onPick(row.candidateId)}
                className={cn(
                  "flex cursor-pointer items-center gap-2.5 rounded-u-control px-2 py-1.5",
                  index === highlighted && "bg-u-raised",
                )}
              >
                <Avatar id={row.personId} name={row.fullName} size="sm" className="flex-none" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-note font-semibold text-u-text">{row.fullName}</span>
                  <span className="block truncate text-meta text-u-text3">
                    {[row.title, row.companyName].filter(Boolean).join(" · ")}
                  </span>
                </span>
                <span className="flex-none text-meta text-u-text3">{row.statusLabel}</span>
              </li>
            ))}
          </ul>
        </>
      )}
      {!people.isPending && !people.isError && matching.length > shown.length && (
        <p className="border-t border-u-border px-2 pt-1.5 text-meta text-u-text3">
          {typed
            ? `${matching.length - shown.length} more. Keep typing to narrow.`
            : `Type to find anyone of the ${everyone.length} on this position.`}
        </p>
      )}
    </div>
  );
}

function matches(row: PipelineRow, typed: string): boolean {
  return [row.fullName, row.title, row.companyName].some((field) => field?.toLowerCase().includes(typed));
}

function newestFirst(rows: readonly PipelineRow[]): PipelineRow[] {
  return [...rows].sort((left, right) => right.addedAt.localeCompare(left.addedAt));
}
