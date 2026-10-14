import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as notificationsApi from "../api/notificationsApi";
import type { NotificationResponse, NotificationsResponse } from "../api/types";
import { NotificationBell } from "./NotificationBell";

vi.mock("../api/notificationsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/notificationsApi")>()),
  getUnreadCount: vi.fn(),
  getNotifications: vi.fn(),
  markRead: vi.fn(),
  changeNotification: vi.fn(),
}));
vi.mock("../lib/useNotificationStream", () => ({ useNotificationStream: () => true }));

function Location() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname + location.search}</span>;
}

function renderBell() {
  return render(
    <MemoryRouter initialEntries={["/all"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <NotificationBell />
          <Routes>
            <Route path="*" element={<Location />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

function aNotification(overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: "n1",
    kind: "task_assigned",
    count: 1,
    actor: { userId: "u2", fullName: "Yara Haddad", avatarUrl: null },
    projectId: "p1",
    projectTitle: "CFO Search",
    personId: "pe1",
    candidateId: "c1",
    personName: "Ahmed Saleh",
    taskId: "t1",
    taskTitle: "Compensation check",
    noteId: null,
    names: [],
    dueAt: null,
    createdAt: new Date().toISOString(),
    read: false,
    snoozedUntil: null,
    ...overrides,
  };
}

const page = (items: NotificationResponse[], nextCursor: string | null = null): NotificationsResponse => ({
  items,
  nextCursor,
  unreadCount: items.filter((item) => !item.read).length,
});

const assigned = aNotification();
const mention = aNotification({
  id: "n2",
  kind: "mentioned",
  actor: { userId: "u3", fullName: "Omar Khalil", avatarUrl: null },
  taskId: null,
  taskTitle: null,
  noteId: "note-1",
});

const openPanel = async () => userEvent.click(await screen.findByRole("button", { name: /^Notifications/ }));

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue({ unreadCount: 2 });
    vi.mocked(notificationsApi.getNotifications).mockImplementation(async (filter) =>
      filter === "unread" ? page([assigned, mention]) : page([assigned, mention, aNotification({ id: "n3", read: true })]),
    );
  });

  it("carries the unread count on the bell", async () => {
    renderBell();

    expect(await screen.findByRole("button", { name: "Notifications, 2 unread" })).toBeInTheDocument();
    expect(screen.getByTestId("notification-badge")).toHaveTextContent("2");
  });

  it("hides the badge when nothing is unread", async () => {
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue({ unreadCount: 0 });
    renderBell();

    await waitFor(() => expect(notificationsApi.getUnreadCount).toHaveBeenCalled());
    expect(await screen.findByRole("button", { name: "Notifications" })).toBeInTheDocument();
    expect(screen.queryByTestId("notification-badge")).not.toBeInTheDocument();
  });

  it("says 9+ past nine", async () => {
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue({ unreadCount: 14 });
    renderBell();

    await screen.findByRole("button", { name: "Notifications, 14 unread" });
    expect(screen.getByTestId("notification-badge")).toHaveTextContent("9+");
  });

  it("lists the unread rows under their day, with whoever did each", async () => {
    renderBell();
    await openPanel();

    const panel = await screen.findByRole("dialog", { name: "Notifications" });
    expect(within(panel).getByRole("tab", { name: "Unread · 2" })).toHaveAttribute("aria-selected", "true");
    const today = within(panel).getByRole("region", { name: "Today" });
    expect(within(today).getByRole("button", { name: /Yara Haddad assigned you Compensation check on Ahmed Saleh/ })).toBeInTheDocument();
    expect(within(today).getByRole("button", { name: /Omar Khalil mentioned you in a note on Ahmed Saleh/ })).toBeInTheDocument();
    expect(within(today).getByTitle("Omar Khalil")).toBeInTheDocument();
  });

  it("marks everything read, and says you're all caught up", async () => {
    vi.mocked(notificationsApi.markRead).mockResolvedValue({ unreadCount: 0 });
    renderBell();
    await openPanel();
    await screen.findByRole("button", { name: /assigned you/ });
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue({ unreadCount: 0 });
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([]));

    await userEvent.click(screen.getByRole("button", { name: "Mark all read" }));

    await waitFor(() => expect(notificationsApi.markRead).toHaveBeenCalledWith({ all: true }));
    expect(await screen.findByText("You're all caught up")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Unread · 0" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mark all read" })).toBeDisabled();
    expect(screen.queryByTestId("notification-badge")).not.toBeInTheDocument();
  });

  it("shows read rows under All", async () => {
    renderBell();
    await openPanel();
    await userEvent.click(await screen.findByRole("tab", { name: "All" }));

    expect(await screen.findAllByRole("button", { name: /assigned you/ })).toHaveLength(2);
    expect(notificationsApi.getNotifications).toHaveBeenCalledWith("all", null, expect.anything());
  });

  it("says nothing is here yet when All is empty", async () => {
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue({ unreadCount: 0 });
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([]));
    renderBell();
    await openPanel();

    expect(await screen.findByText("You're all caught up")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "See all notifications" }));

    expect(await screen.findByText("Nothing here yet")).toBeInTheDocument();
  });

  it("opens a row on the executive's drawer and marks it read", async () => {
    vi.mocked(notificationsApi.changeNotification).mockResolvedValue({ ...assigned, read: true });
    renderBell();
    await openPanel();

    await userEvent.click(await screen.findByRole("button", { name: /assigned you/ }));

    await waitFor(() => expect(notificationsApi.changeNotification).toHaveBeenCalledWith("n1", { read: true }));
    expect(screen.getByTestId("location")).toHaveTextContent("/projects/p1/pipeline?candidate=c1");
    expect(screen.queryByRole("dialog", { name: "Notifications" })).not.toBeInTheDocument();
  });

  it("snoozes a row until the time picked, and archives another", async () => {
    vi.mocked(notificationsApi.changeNotification).mockResolvedValue(assigned);
    renderBell();
    await openPanel();
    const row = (await screen.findByRole("button", { name: /assigned you/ })).closest("li") as HTMLElement;

    await userEvent.click(within(row).getByRole("button", { name: "Snooze" }));
    await userEvent.click(within(row).getByRole("menuitem", { name: /^Tomorrow/ }));

    await waitFor(() =>
      expect(notificationsApi.changeNotification).toHaveBeenCalledWith("n1", { snoozedUntil: expect.any(String) }),
    );
    expect(await screen.findByText(/^Snoozed until \d{2} [A-Z][a-z]{2} \d{4}, 09:00$/)).toBeInTheDocument();

    const other = screen.getByRole("button", { name: /mentioned you/ }).closest("li") as HTMLElement;
    await userEvent.click(within(other).getByRole("button", { name: "Archive" }));
    await waitFor(() => expect(notificationsApi.changeNotification).toHaveBeenCalledWith("n2", { archived: true }));
  });

  it("pages on with the cursor it was given", async () => {
    vi.mocked(notificationsApi.getNotifications).mockImplementation(async (_filter, cursor) =>
      cursor === "next" ? page([aNotification({ id: "n9", taskTitle: "Reference check" })]) : page([assigned], "next"),
    );
    renderBell();
    await openPanel();

    await userEvent.click(await screen.findByRole("button", { name: "Load more" }));

    expect(await screen.findByRole("button", { name: /Reference check/ })).toBeInTheDocument();
    expect(notificationsApi.getNotifications).toHaveBeenCalledWith("unread", "next", expect.anything());
  });
});
