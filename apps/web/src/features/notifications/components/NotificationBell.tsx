import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useLocation } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import * as notificationsApi from "../api/notificationsApi";
import { unreadBadgeOf } from "../lib/notificationView";
import { useNotificationStream } from "../lib/useNotificationStream";
import { NotificationPanel } from "./NotificationPanel";

/** While the stream is down the count is asked for on a timer instead, so the bell is never stale for long. */
const POLL_WITHOUT_STREAM_MS = 60_000;

/**
 * The topbar's bell (`Notifications.dc.html`): the unread count on it, hidden at none, and the panel under
 * it. Staff only — the caller decides — and it holds the caller's notification stream open while it is up.
 */
export function NotificationBell() {
  const isLive = useNotificationStream();
  const unread = useQuery({
    queryKey: notificationsApi.UNREAD_COUNT_KEY,
    queryFn: ({ signal }) => notificationsApi.getUnreadCount(signal),
    refetchInterval: isLive ? false : POLL_WITHOUT_STREAM_MS,
  });
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const { pathname } = useLocation();

  const unreadCount = unread.data?.unreadCount ?? 0;
  const badge = unreadBadgeOf(unreadCount);

  useEscapeKey(open, () => {
    setOpen(false);
    buttonRef.current?.focus();
  });

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    if (!open) return;
    const closeOnOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutside);
    return () => document.removeEventListener("mousedown", closeOnOutside);
  }, [open]);

  return (
    <div ref={rootRef} className="relative flex-none">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((wasOpen) => !wasOpen)}
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : "Notifications"}
        aria-haspopup="dialog"
        aria-expanded={open}
        title="Notifications"
        className={cn(
          "relative grid size-8 place-items-center rounded-lg transition hover:bg-u-raised hover:text-u-text",
          "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent",
          open ? "bg-u-raised text-u-text" : "text-u-text2",
        )}
      >
        <Icon d={ICONS.bell} size={17} />
        {badge && (
          <span
            data-testid="notification-badge"
            aria-hidden="true"
            className="absolute -right-0.5 top-px h-4 min-w-4 rounded-full border-2 border-u-bg bg-u-offlimits px-1 text-center font-u-num text-eyebrow font-bold leading-3 text-white"
          >
            {badge}
          </span>
        )}
      </button>

      {open && (
        <>
          <NotificationPanel unreadCount={unreadCount} onClose={() => setOpen(false)} />
          {createPortal(<div aria-hidden="true" className="fixed inset-0 z-[55] bg-u-scrim opacity-45" />, document.body)}
        </>
      )}
    </div>
  );
}
