import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as notificationsApi from "../../notifications/api/notificationsApi";
import type { NotificationPreferences } from "../../notifications/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import { SettingsNotificationsPage } from "./SettingsNotificationsPage";

vi.mock("../../notifications/api/notificationsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../notifications/api/notificationsApi")>()),
  getNotificationPreferences: vi.fn(),
  putNotificationPreferences: vi.fn(),
  getUnreadCount: vi.fn(),
}));
vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getTaskRules: vi.fn(),
  putTaskRules: vi.fn(),
  getMyTasks: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

const preferences: NotificationPreferences = {
  kinds: [
    { kind: "task_assigned", inApp: true, email: true },
    { kind: "task_due", inApp: true, email: false },
    { kind: "mentioned", inApp: true, email: true },
    { kind: "outreach_replied", inApp: true, email: false },
    { kind: "outreach_bounced", inApp: true, email: false },
    { kind: "call_booked", inApp: true, email: false },
    { kind: "added_to_team", inApp: true, email: false },
    { kind: "tasks_moved", inApp: true, email: false },
  ],
  digest: true,
  timeZone: "Asia/Dubai",
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsNotificationsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SettingsNotificationsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(notificationsApi.getNotificationPreferences).mockResolvedValue(preferences);
    vi.mocked(notificationsApi.putNotificationPreferences).mockImplementation(async (next) => next);
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue({ unreadCount: 2 });
    vi.mocked(tasksApi.getTaskRules).mockResolvedValue({ reply: true, bounce: true, bookedCall: false });
    vi.mocked(tasksApi.putTaskRules).mockImplementation(async (next) => next);
    vi.mocked(tasksApi.getMyTasks).mockResolvedValue({
      tasks: [],
      counts: { today: 3, overdue: 1, upcoming: 4, assignedByMe: 0 },
      hasMore: false,
    });
  });

  it("offers an email switch only for a task assigned and a mention, and the digest for the rest", async () => {
    renderPage();
    const events = await screen.findByRole("region", { name: "Events" });

    expect(within(events).getAllByRole("switch", { name: /, in app$/ })).toHaveLength(9);
    expect(within(events).getAllByRole("switch", { name: /, by email$/ }).map((toggle) => toggle.getAttribute("aria-label"))).toEqual([
      "A task is assigned to me, by email",
      "Someone @mentions me, by email",
    ]);
    expect(within(events).getAllByText("Daily digest")).toHaveLength(7);
    expect(screen.getByText("Emailed only if still unread after 10 minutes.")).toBeInTheDocument();
  });

  it("saves a switch the moment it is flipped", async () => {
    renderPage();
    const mentionByEmail = await screen.findByRole("switch", { name: "Someone @mentions me, by email" });

    await userEvent.click(mentionByEmail);

    expect(mentionByEmail).toHaveAttribute("aria-checked", "false");
    await waitFor(() => expect(notificationsApi.putNotificationPreferences).toHaveBeenCalledTimes(1));
    const saved = vi.mocked(notificationsApi.putNotificationPreferences).mock.calls[0][0];
    expect(saved.kinds.find((entry) => entry.kind === "mentioned")).toEqual({ kind: "mentioned", inApp: true, email: false });
    expect(saved.kinds.find((entry) => entry.kind === "task_assigned")).toEqual({ kind: "task_assigned", inApp: true, email: true });
    expect(await screen.findByText("Saved")).toBeInTheDocument();
  });

  it("puts a refused switch back as the server has it", async () => {
    vi.mocked(notificationsApi.putNotificationPreferences).mockRejectedValue(
      new ApiRequestError({ code: "RATE_LIMITED", detail: "slow down", status: 429, correlationId: "c1" }),
    );
    renderPage();
    const inApp = await screen.findByRole("switch", { name: "Tasks are moved to me, in app" });

    await userEvent.click(inApp);

    expect(await screen.findByText("Too many requests in a short time. Wait a minute and try again.")).toBeInTheDocument();
    await waitFor(() => expect(inApp).toHaveAttribute("aria-checked", "true"));
  });

  it("switches the daily digest, which goes out in the person's own zone", async () => {
    renderPage();

    expect(await screen.findByText("Every weekday at 08:00 (Asia/Dubai)")).toBeInTheDocument();
    expect(await screen.findByText(/· 3 due today · 1 overdue · 2 unread$/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "Daily digest" }));

    await waitFor(() =>
      expect(notificationsApi.putNotificationPreferences).toHaveBeenCalledWith({ ...preferences, digest: false }),
    );
    expect(screen.queryByText(/due today/)).not.toBeInTheDocument();
  });

  it("lets an admin switch the workspace's automatic tasks", async () => {
    renderPage();
    const card = await screen.findByRole("region", { name: "Automatic tasks for the workspace" });
    expect(within(card).getByText("When outreach needs a person, Uncava files a task for whoever started the sequence.")).toBeInTheDocument();
    expect(within(card).getByText("Find a working email for {name}")).toBeInTheDocument();
    const booked = await within(card).findByRole("switch", { name: "Automatic task on Booked call" });
    await waitFor(() => expect(booked).toBeEnabled());
    expect(booked).toHaveAttribute("aria-checked", "false");

    await userEvent.click(booked);

    await waitFor(() => expect(tasksApi.putTaskRules).toHaveBeenCalledWith({ reply: true, bounce: true, bookedCall: true }));
    expect(await screen.findByText("Saved for the whole workspace")).toBeInTheDocument();
  });

  it("shows a member no automatic tasks, and never asks for them", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    renderPage();
    await screen.findByRole("region", { name: "Events" });

    expect(screen.queryByRole("region", { name: "Automatic tasks for the workspace" })).not.toBeInTheDocument();
    expect(tasksApi.getTaskRules).not.toHaveBeenCalled();
  });
});
