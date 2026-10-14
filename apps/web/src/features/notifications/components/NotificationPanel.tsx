import { useInfiniteQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, LinesSkeleton } from "../../../components/ui";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as notificationsApi from "../api/notificationsApi";
import type { NotificationFilter, NotificationResponse } from "../api/types";
import { groupByDay, notificationHref } from "../lib/notificationView";
import { useNotificationActions } from "../lib/useNotificationActions";
import { NotificationRow } from "./NotificationRow";

const FILTERS: readonly NotificationFilter[] = ["unread", "all"];

/** The bell's panel (`Notifications.dc.html`): Unread or All, by day, a page at a time. */
export function NotificationPanel({ unreadCount, onClose }: { unreadCount: number; onClose: () => void }) {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<NotificationFilter>("unread");
  const tabKeys = useRadioGroupKeys(FILTERS, filter, setFilter);
  const actions = useNotificationActions();
  const list = useInfiniteQuery({
    queryKey: notificationsApi.NOTIFICATIONS_KEY(filter),
    queryFn: ({ pageParam, signal }) => notificationsApi.getNotifications(filter, pageParam, signal),
    initialPageParam: null as string | null,
    getNextPageParam: (page) => page.nextCursor,
  });

  const now = new Date();
  const rows = list.data?.pages.flatMap((page) => page.items) ?? [];
  // A row read here leaves Unread at once, as the server's next page would have it.
  const shown = filter === "unread" ? rows.filter((row) => !row.read) : rows;
  const days = groupByDay(shown, now);

  const handleOpen = (row: NotificationResponse) => {
    if (!row.read) actions.setRead(row, true);
    onClose();
    navigate(notificationHref(row));
  };

  return (
    <div
      role="dialog"
      aria-label="Notifications"
      className={cn(
        "fixed inset-x-3 top-[50px] z-[80] flex max-h-[70vh] flex-col overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface shadow-u-e3",
        "sm:absolute sm:inset-x-auto sm:-right-10 sm:top-10 sm:w-[400px]",
      )}
    >
      <div className="flex-none border-b border-u-border px-4 pb-2.5 pt-3.5">
        <div className="flex items-center gap-2">
          <span className="text-lead font-semibold text-u-text">Notifications</span>
          <button
            type="button"
            onClick={actions.markAllRead}
            disabled={unreadCount === 0 || actions.isMarkingAllRead}
            className={cn(
              "ms-auto rounded-u-control px-2 py-1 text-note font-medium text-u-accent transition hover:bg-u-raised",
              "disabled:cursor-default disabled:text-u-text3 disabled:opacity-60 disabled:hover:bg-transparent",
            )}
          >
            Mark all read
          </button>
        </div>
        <div
          ref={tabKeys.ref}
          role="tablist"
          aria-label="Show"
          onKeyDown={tabKeys.onKeyDown}
          className="mt-2.5 inline-flex items-center rounded-lg border border-u-border-strong bg-u-raised p-0.5"
        >
          {FILTERS.map((value) => {
            const selected = value === filter;
            return (
              <button
                key={value}
                type="button"
                role="tab"
                aria-selected={selected}
                tabIndex={selected ? 0 : -1}
                onClick={() => setFilter(value)}
                className={cn(
                  "rounded-u-control px-3 py-1 text-note font-medium transition",
                  selected ? "bg-u-surface text-u-text shadow-u-e1" : "text-u-text3 hover:text-u-text2",
                )}
              >
                {value === "unread" ? `Unread · ${unreadCount}` : "All"}
              </button>
            );
          })}
        </div>
      </div>

      <div role="tabpanel" aria-label={filter === "unread" ? "Unread" : "All"} className="min-h-0 flex-1 overflow-y-auto pb-1.5 pt-1">
        {list.isError ? (
          <p className="px-4 py-6 text-note text-u-text3">{messageFor(list.error)}</p>
        ) : list.isPending ? (
          <LinesSkeleton lines={4} className="px-4 py-3" />
        ) : days.length === 0 ? (
          <EmptyPanel filter={filter} onShowAll={() => setFilter("all")} />
        ) : (
          <>
            {days.map((day) => (
              <section key={day.label} aria-label={day.label}>
                <div className="sticky top-0 z-[2] bg-u-surface px-4 pb-1.5 pt-2.5 font-mono type-micro-label tracking-[0.14em] text-u-text3">
                  {day.label}
                </div>
                <ul>
                  {day.items.map((row) => (
                    <NotificationRow
                      key={row.id}
                      row={row}
                      now={now}
                      onOpen={handleOpen}
                      onSetRead={actions.setRead}
                      onSnooze={actions.snooze}
                      onArchive={actions.archive}
                    />
                  ))}
                </ul>
              </section>
            ))}
            {list.hasNextPage && (
              <div className="flex justify-center px-4 py-2">
                <Button variant="secondary" size="xs" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
                  Load more
                </Button>
              </div>
            )}
          </>
        )}
      </div>

      <div className="flex flex-none items-center justify-between gap-2.5 border-t border-u-border bg-u-raised px-3 py-2">
        <Link
          to="/settings/notifications"
          onClick={onClose}
          className="inline-flex items-center gap-1.5 rounded-u-control px-1.5 py-1 text-note font-medium text-u-text2 transition hover:bg-u-surface hover:text-u-text"
        >
          <Icon d={ICONS.settings} size={14} />
          Notification settings
        </Link>
        <Link
          to="/"
          onClick={onClose}
          className="inline-flex items-center gap-1.5 rounded-u-control px-1.5 py-1 text-note font-medium text-u-accent transition hover:bg-u-surface"
        >
          View all tasks
          <Icon d={ICONS.arrowRight} size={13} />
        </Link>
      </div>
    </div>
  );
}

function EmptyPanel({ filter, onShowAll }: { filter: NotificationFilter; onShowAll: () => void }) {
  const unread = filter === "unread";
  return (
    <div className="px-7 pb-10 pt-11 text-center">
      <span className="inline-grid size-11 place-items-center rounded-full bg-u-direct-tint text-u-direct">
        <Icon d={ICONS.check} size={20} />
      </span>
      <div className="mt-3 text-body font-semibold text-u-text">{unread ? "You're all caught up" : "Nothing here yet"}</div>
      <div className="mx-auto mt-1 max-w-[260px] text-note text-u-text3">
        {unread
          ? "New tasks, mentions and replies show up here."
          : "Tasks, mentions and replies on your positions show up here."}
      </div>
      {unread && (
        <Button variant="secondary" size="xs" className="mx-auto mt-3.5" onClick={onShowAll}>
          See all notifications
        </Button>
      )}
    </div>
  );
}
