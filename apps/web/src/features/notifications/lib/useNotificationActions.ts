import { useMutation, useQueryClient, type InfiniteData, type QueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as notificationsApi from "../api/notificationsApi";
import type { NotificationResponse, NotificationsResponse } from "../api/types";
import type { SnoozeOption } from "./notificationView";

type NotificationPages = InfiniteData<NotificationsResponse, string | null>;

const LISTS_KEY = [...notificationsApi.NOTIFICATIONS_KEY_PREFIX, "list"] as const;

/**
 * The bell's writes, each drawn at once: a read row goes quiet, a snoozed or archived one leaves, and the count
 * moves with it. Whatever the server answers, both lists and the count are read again after.
 */
export function useNotificationActions() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const settle = () => void queryClient.invalidateQueries({ queryKey: notificationsApi.NOTIFICATIONS_KEY_PREFIX });
  const refuse = (error: unknown) => toast.error(messageFor(error));

  const markAllRead = useMutation({
    mutationFn: () => notificationsApi.markRead({ all: true }),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: LISTS_KEY });
      patchRows(queryClient, (row) => ({ ...row, read: true }));
      setUnread(queryClient, () => 0);
    },
    onSuccess: ({ unreadCount }) => setUnread(queryClient, () => unreadCount),
    onError: refuse,
    onSettled: settle,
  });

  const setRead = useMutation({
    mutationFn: ({ row, read }: { row: NotificationResponse; read: boolean }) =>
      notificationsApi.changeNotification(row.id, { read }),
    onMutate: async ({ row, read }) => {
      if (row.read === read) return;
      await queryClient.cancelQueries({ queryKey: LISTS_KEY });
      patchRows(queryClient, (candidate) => (candidate.id === row.id ? { ...candidate, read } : candidate));
      setUnread(queryClient, (count) => count + (read ? -1 : 1));
    },
    onError: refuse,
    onSettled: settle,
  });

  const snooze = useMutation({
    mutationFn: ({ row, option }: { row: NotificationResponse; option: SnoozeOption }) =>
      notificationsApi.changeNotification(row.id, { snoozedUntil: option.until.toISOString() }),
    onMutate: ({ row }) => remove(queryClient, row),
    onSuccess: (_, { option }) => toast(`Snoozed until ${option.hint}`),
    onError: refuse,
    onSettled: settle,
  });

  const archive = useMutation({
    mutationFn: (row: NotificationResponse) => notificationsApi.changeNotification(row.id, { archived: true }),
    onMutate: (row) => remove(queryClient, row),
    onSuccess: () => toast("Archived"),
    onError: refuse,
    onSettled: settle,
  });

  return {
    markAllRead: () => markAllRead.mutate(),
    isMarkingAllRead: markAllRead.isPending,
    setRead: (row: NotificationResponse, read: boolean) => setRead.mutate({ row, read }),
    snooze: (row: NotificationResponse, option: SnoozeOption) => snooze.mutate({ row, option }),
    archive: (row: NotificationResponse) => archive.mutate(row),
  };
}

async function remove(queryClient: QueryClient, row: NotificationResponse) {
  await queryClient.cancelQueries({ queryKey: LISTS_KEY });
  patchRows(queryClient, (candidate) => (candidate.id === row.id ? null : candidate));
  if (!row.read) setUnread(queryClient, (count) => count - 1);
}

function patchRows(queryClient: QueryClient, patch: (row: NotificationResponse) => NotificationResponse | null) {
  queryClient.setQueriesData<NotificationPages>({ queryKey: LISTS_KEY }, (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.flatMap((row) => {
              const next = patch(row);
              return next ? [next] : [];
            }),
          })),
        }
      : data,
  );
}

function setUnread(queryClient: QueryClient, next: (count: number) => number) {
  queryClient.setQueryData<{ unreadCount: number }>(notificationsApi.UNREAD_COUNT_KEY, (current) =>
    current ? { unreadCount: Math.max(0, next(current.unreadCount)) } : current,
  );
}
