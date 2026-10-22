import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { AppIcon, Avatar } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import type { NotificationKind, NotificationResponse } from "../api/types";
import { sentenceOf, sentenceTextOf } from "../lib/notificationSentence";
import { snoozeOptionsOf, timeLabelOf, type SnoozeOption } from "../lib/notificationView";

const GLYPHS = {
  task: "M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11",
  mention: "M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  reply: "M9 17l-5-5 5-5M4 12h11a5 5 0 0 1 5 5v2",
  bounce: "M12 8v4M12 16h.01M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z",
  clock: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20ZM12 6v6l4 2",
  unread: "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z",
} as const;

/** The kind at the avatar's corner (`Notifications.dc.html`'s KINDS). */
const KIND_BADGES: Record<NotificationKind, { label: string; glyph: string; className: string }> = {
  task_assigned: { label: "Task", glyph: GLYPHS.task, className: "bg-u-accent-solid" },
  task_due_soon: { label: "Task due soon", glyph: GLYPHS.clock, className: "bg-u-signal" },
  task_overdue: { label: "Task overdue", glyph: GLYPHS.clock, className: "bg-u-offlimits" },
  mentioned: { label: "Mention", glyph: GLYPHS.mention, className: "bg-u-inferred" },
  outreach_replied: { label: "Reply", glyph: GLYPHS.reply, className: "bg-u-direct" },
  outreach_bounced: { label: "Bounce", glyph: GLYPHS.bounce, className: "bg-u-offlimits" },
  call_booked: { label: "Call booked", glyph: ICONS.calendar, className: "bg-u-adjacent" },
  added_to_team: { label: "Team", glyph: ICONS.team, className: "bg-u-text2" },
  tasks_moved: { label: "Tasks moved", glyph: GLYPHS.task, className: "bg-u-accent-solid" },
  meeting_notes_ready: { label: "Meeting notes", glyph: ICONS.calendar, className: "bg-u-direct" },
};

/** One bell row: who, the sentence, where and when; on hover or focus, read, snooze and archive. */
export function NotificationRow({
  row,
  now,
  onOpen,
  onSetRead,
  onSnooze,
  onArchive,
}: {
  row: NotificationResponse;
  now: Date;
  onOpen: (row: NotificationResponse) => void;
  onSetRead: (row: NotificationResponse, read: boolean) => void;
  onSnooze: (row: NotificationResponse, option: SnoozeOption) => void;
  onArchive: (row: NotificationResponse) => void;
}) {
  const sentence = sentenceOf(row, now);
  const time = timeLabelOf(row.createdAt, now);
  const spoken = [sentenceTextOf(sentence), sentence.sub?.text, time, row.read ? null : "Unread"]
    .filter(Boolean)
    .join(". ");
  const [snoozeOpen, setSnoozeOpen] = useState(false);
  const actionsRef = useRef<HTMLDivElement>(null);

  useEscapeKey(snoozeOpen, () => setSnoozeOpen(false));

  useEffect(() => {
    if (!snoozeOpen) return;
    const closeOnOutside = (event: MouseEvent) => {
      if (!actionsRef.current?.contains(event.target as Node)) setSnoozeOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutside);
    return () => document.removeEventListener("mousedown", closeOnOutside);
  }, [snoozeOpen]);

  return (
    <li className="group relative">
      <button
        type="button"
        onClick={() => onOpen(row)}
        aria-label={spoken}
        className={cn(
          "flex w-full items-start gap-3 py-2.5 pe-3.5 ps-4 text-left outline-none transition",
          "group-hover:bg-u-raised focus-visible:bg-u-raised",
          row.read ? "bg-transparent" : "bg-u-accent/5",
          snoozeOpen && "bg-u-raised",
        )}
      >
        <ActorMark row={row} />
        <span className="min-w-0 flex-1">
          <span className="block text-body text-u-text2">
            {sentence.parts.map((part, index) => (
              <span
                key={index}
                className={cn(part.strong && "font-semibold text-u-text", part.muted && "text-u-text3")}
              >
                {part.text}
              </span>
            ))}
            {sentence.where && <span className="text-u-text3"> · {sentence.where}</span>}
          </span>
          {sentence.sub && (
            <span
              className={cn(
                "mt-0.5 flex items-start gap-1.5 text-note",
                sentence.sub.tone === "task" ? "text-u-accent" : "text-u-text3",
              )}
            >
              <Icon d={sentence.sub.tone === "task" ? GLYPHS.task : GLYPHS.clock} size={12} className="mt-0.5 flex-none" />
              <span>{sentence.sub.text}</span>
            </span>
          )}
        </span>
        <span className="flex min-w-11 flex-none flex-col items-end gap-2 pt-0.5">
          <span className={cn("whitespace-nowrap font-u-num text-meta", row.read ? "text-u-text3" : "text-u-text2")}>
            {time}
          </span>
          {!row.read && <span className="size-[7px] rounded-full bg-u-accent" />}
        </span>
      </button>

      <div
        ref={actionsRef}
        className={cn(
          "absolute right-3 top-2 items-center gap-0.5 rounded-u-control bg-u-raised",
          snoozeOpen ? "flex" : "hidden group-focus-within:flex group-hover:flex",
        )}
      >
        <RowAction
          label={row.read ? "Mark unread" : "Mark read"}
          glyph={row.read ? GLYPHS.unread : ICONS.check}
          onClick={() => onSetRead(row, !row.read)}
        />
        <RowAction
          label="Snooze"
          glyph={GLYPHS.clock}
          active={snoozeOpen}
          expanded={snoozeOpen}
          onClick={() => setSnoozeOpen((open) => !open)}
        />
        <RowAction label="Archive" glyph={ICONS.archive} onClick={() => onArchive(row)} />
        {snoozeOpen && (
          <div
            role="menu"
            aria-label="Snooze until"
            className="absolute right-7 top-8 z-10 w-[184px] rounded-u-card border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
          >
            <div className="px-2 pb-1 pt-1.5 font-mono type-micro-label text-u-text3">Snooze until</div>
            {snoozeOptionsOf(now).map((option) => (
              <button
                key={option.key}
                type="button"
                role="menuitem"
                onClick={() => {
                  setSnoozeOpen(false);
                  onSnooze(row, option);
                }}
                className="flex w-full items-center justify-between gap-2.5 rounded-u-control px-2 py-1.5 text-left hover:bg-u-raised focus-visible:bg-u-raised focus-visible:outline-none"
              >
                <span className="text-body text-u-text">{option.label}</span>
                <span className="font-u-num text-meta text-u-text3">{option.hint}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </li>
  );
}

/** The person who did it, always — or Uncava's own mark when nobody did. */
function ActorMark({ row }: { row: NotificationResponse }) {
  const badge = KIND_BADGES[row.kind];
  return (
    <span className="relative mt-px size-7 flex-none">
      {row.actor ? (
        <Avatar id={row.actor.userId} name={row.actor.fullName ?? "Teammate"} src={row.actor.avatarUrl} className="size-7" />
      ) : (
        <span title="Uncava" className="grid size-7 place-items-center rounded-full bg-u-raised">
          <AppIcon className="h-4" />
        </span>
      )}
      <span
        title={badge.label}
        className={cn(
          "absolute -bottom-1 -right-1 grid size-4 place-items-center rounded-full text-white ring-2 ring-u-surface group-hover:ring-u-raised",
          badge.className,
        )}
      >
        <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d={badge.glyph} />
        </svg>
      </span>
    </span>
  );
}

function RowAction({
  label,
  glyph,
  active = false,
  expanded,
  onClick,
}: {
  label: string;
  glyph: string;
  active?: boolean;
  expanded?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      aria-haspopup={expanded === undefined ? undefined : "menu"}
      aria-expanded={expanded}
      onClick={onClick}
      className={cn(
        "grid size-[26px] place-items-center rounded-u-control text-u-text3 transition hover:bg-u-surface hover:text-u-text",
        active && "bg-u-surface text-u-text",
      )}
    >
      <Icon d={glyph} size={14} />
    </button>
  );
}
