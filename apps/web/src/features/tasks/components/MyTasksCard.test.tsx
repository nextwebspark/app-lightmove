import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";
import * as tasksApi from "../api/tasksApi";
import type { MyTasksResponse, MyTasksView, TaskResponse } from "../api/types";
import { MyTasksCard } from "./MyTasksCard";

const viewer = { id: "u1", fullName: "Yara Haddad", avatarUrl: null, workspace: { roles: ["ADMIN"], mode: "COMPANY" } };
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: viewer }) }));
vi.mock("../api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/tasksApi")>()),
  getMyTasks: vi.fn(),
  getTaskPresets: vi.fn(),
  updateTask: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));

const at = (day: number, hour: number) => new Date(2026, 9, day, hour, 0).toISOString();

const aTask = (overrides: Partial<TaskResponse> & { id: string; title: string }): TaskResponse => ({
  projectId: "p1",
  projectTitle: "Chief Financial Officer",
  candidateId: "c1",
  personId: "person-1",
  personName: "Fatima Al Mazrouei",
  kind: "call",
  detail: null,
  presetId: null,
  noteId: null,
  assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
  createdBy: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
  dueAt: at(14, 16),
  status: "open",
  source: "manual",
  rule: null,
  completedAt: null,
  completedBy: null,
  createdAt: at(10, 9),
  editable: true,
  ...overrides,
});

const COUNTS = { today: 6, overdue: 1, upcoming: 0, assignedByMe: 1 };

const TODAY = Array.from({ length: 6 }, (_, index) =>
  aTask({ id: `t${index}`, title: `Today task ${index + 1}`, dueAt: at(14, 10 + index) }),
);

const BY_VIEW: Record<MyTasksView, TaskResponse[]> = {
  today: [
    aTask({ id: "reply", title: "Reply to Rania Khoury", source: "rule", rule: "reply", createdBy: null }),
    ...TODAY.slice(1),
  ],
  overdue: [aTask({ id: "late", title: "Reference check", dueAt: at(3, 9), kind: "todo" })],
  upcoming: [],
  assigned_by_me: [
    aTask({
      id: "given",
      title: "Confirm package range",
      assignee: { userId: "u3", fullName: "Sara Al-Mansour", avatarUrl: null },
    }),
  ],
};

function renderCard() {
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <MyTasksCard />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const tab = (name: RegExp) => screen.getByRole("tab", { name });

describe("MyTasksCard", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 14, 9, 0));
    vi.mocked(tasksApi.getMyTasks).mockImplementation(
      async (view): Promise<MyTasksResponse> => ({ tasks: BY_VIEW[view], counts: COUNTS }),
    );
    vi.mocked(tasksApi.getTaskPresets).mockResolvedValue([]);
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("counts every view on its tab and opens on Today, five rows at a time", async () => {
    renderCard();

    const list = await screen.findByRole("list", { name: "Today tasks" });
    expect(tab(/^Today/)).toHaveAccessibleName("Today · 6");
    expect(tab(/^Today/)).toHaveAttribute("aria-selected", "true");
    expect(tab(/^Overdue/)).toHaveAccessibleName("Overdue · 1");
    expect(tab(/^Upcoming/)).toHaveAccessibleName("Upcoming · 0");
    expect(tab(/^Assigned by me/)).toHaveAccessibleName("Assigned by me · 1");
    expect(within(list).getAllByRole("listitem")).toHaveLength(5);

    await userEvent.click(screen.getByRole("button", { name: "Show all 6" }));

    expect(within(list).getAllByRole("listitem")).toHaveLength(6);
    expect(screen.getByRole("button", { name: "Show fewer" })).toBeInTheDocument();
  });

  it("draws a row's kind, executive, position, due chip and an Auto pill for a rule's task", async () => {
    renderCard();

    const reply = within(await screen.findByRole("listitem", { name: "Reply to Rania Khoury" }));
    expect(reply.getByTitle("Call")).toBeInTheDocument();
    expect(reply.getByText("Auto")).toBeInTheDocument();
    expect(reply.getByTitle("Fatima Al Mazrouei")).toBeInTheDocument();
    expect(reply.getByRole("link", { name: "Fatima Al Mazrouei" })).toHaveAttribute(
      "href",
      "/projects/p1/pipeline?candidate=c1",
    );
    expect(reply.getByRole("link", { name: "Chief Financial Officer" })).toHaveAttribute("href", "/projects/p1/pipeline");
    expect(reply.getByText("Today · 16:00")).toHaveAttribute("data-tone", "today");
  });

  it("reads each tab's own tasks, the overdue in red", async () => {
    renderCard();
    await screen.findByRole("list", { name: "Today tasks" });

    await userEvent.click(tab(/^Overdue/));

    const late = within(await screen.findByRole("listitem", { name: "Reference check" }));
    expect(late.getByText("Overdue · 03 Oct 2026")).toHaveAttribute("data-tone", "overdue");
    expect(tasksApi.getMyTasks).toHaveBeenCalledWith("overdue", expect.anything());
  });

  it("shows whose a task is only on Assigned by me", async () => {
    renderCard();
    await screen.findByRole("list", { name: "Today tasks" });
    expect(screen.queryByTitle(/^Assigned to/)).not.toBeInTheDocument();

    await userEvent.click(tab(/^Assigned by me/));

    const given = within(await screen.findByRole("listitem", { name: "Confirm package range" }));
    expect(given.getByTitle("Assigned to Sara Al-Mansour")).toHaveTextContent("Sara");
  });

  it("says each tab's empty line", async () => {
    renderCard();
    await screen.findByRole("list", { name: "Today tasks" });

    await userEvent.click(tab(/^Upcoming/));

    expect(await screen.findByText("Nothing coming up.")).toBeInTheDocument();
  });

  it("strikes a completed task where it is and offers the next one", async () => {
    vi.mocked(tasksApi.updateTask).mockImplementation(async (taskId) => ({
      ...BY_VIEW.today.find((task) => task.id === taskId)!,
      status: "done",
    }));
    renderCard();
    await screen.findByRole("list", { name: "Today tasks" });

    await userEvent.click(screen.getByRole("checkbox", { name: "Complete Reply to Rania Khoury" }));

    await waitFor(() => expect(tasksApi.updateTask).toHaveBeenCalledWith("reply", { status: "done" }));
    const done = within(screen.getByRole("listitem", { name: "Reply to Rania Khoury" }));
    expect(await done.findByRole("checkbox", { name: "Reopen Reply to Rania Khoury" })).toBeChecked();
    expect(done.getByText("Reply to Rania Khoury")).toHaveClass("line-through");
    expect(tab(/^Today/)).toHaveAccessibleName("Today · 5");

    await userEvent.click(done.getByRole("button", { name: "Set the next one?" }));

    expect(await screen.findByRole("dialog", { name: "Set next action" })).toBeInTheDocument();
  });
});
