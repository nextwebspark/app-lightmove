import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as tasksApi from "../api/tasksApi";
import type { TaskResponse, TasksResponse } from "../api/types";
import { NextActionBar } from "./NextActionBar";

vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: { id: "u1", fullName: "Yara Haddad" } }) }));
vi.mock("../api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/tasksApi")>()),
  getCandidateTasks: vi.fn(),
  updateTask: vi.fn(),
}));

const yara = { userId: "u1", fullName: "Yara Haddad", avatarUrl: null };

const aTask = (id: string, overrides: Partial<TaskResponse> = {}): TaskResponse => ({
  id,
  projectId: "p1",
  projectTitle: "Chief Financial Officer",
  candidateId: "c1",
  personId: "person-1",
  personName: "Jawaher Al Suwaidi",
  title: `Task ${id}`,
  kind: "call",
  detail: null,
  presetId: null,
  note: null,
  assignee: yara,
  createdBy: yara,
  dueAt: null,
  status: "open",
  source: "manual",
  rule: null,
  completedAt: null,
  completedBy: null,
  createdAt: new Date(2026, 9, 1).toISOString(),
  editable: true,
  ...overrides,
});

const aDoneTask = (index: number) =>
  aTask(`done-${index}`, {
    title: `Finished task ${index}`,
    status: "done",
    completedAt: new Date(2026, 9, 14 - index, 12).toISOString(),
    completedBy: yara,
  });

const EVERY_DONE = Array.from({ length: 5 }, (_, index) => aDoneTask(index + 1));

function answer(tasks: Partial<TasksResponse>) {
  vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [], done: [], doneCount: 0, ...tasks });
}

function renderBar({
  onOpen = vi.fn(),
  onShowNote,
  justCompleted = false,
}: { onOpen?: (taskId: string | null) => void; onShowNote?: (noteId: string) => void; justCompleted?: boolean } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <NextActionBar
          projectId="p1"
          candidateId="c1"
          firstName="Jawaher"
          justCompleted={justCompleted}
          onCompleted={() => {}}
          onPromptSettled={() => {}}
          onOpen={onOpen}
          onShowNote={onShowNote}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("the Next action bar", () => {
  beforeEach(() => {
    vi.mocked(tasksApi.getCandidateTasks).mockReset();
    vi.mocked(tasksApi.updateTask).mockReset();
  });

  it("shows the earliest open task alone, and counts the rest", async () => {
    answer({ open: [aTask("t1", { title: "Call back" }), aTask("t2"), aTask("t3")] });
    renderBar();

    const bar = await screen.findByRole("group", { name: "Next action" });
    expect(within(bar).getByText("Call back")).toBeInTheDocument();
    expect(within(bar).queryByText("Task t2")).not.toBeInTheDocument();
    expect(within(bar).getByText("+2")).toBeInTheDocument();
  });

  it("opens every open task in a popover, grouped by when it is due", async () => {
    const yesterday = new Date(Date.now() - 86_400_000).toISOString();
    const nextWeek = new Date(Date.now() + 7 * 86_400_000).toISOString();
    answer({
      open: [aTask("t1", { title: "Chase the reference", dueAt: yesterday }), aTask("t2", { title: "Send the brief", dueAt: nextWeek })],
    });
    renderBar();

    await userEvent.click(await screen.findByRole("button", { name: /Next action: Chase the reference/ }));

    const panel = await screen.findByRole("dialog", { name: "Next actions" });
    expect(within(within(panel).getByRole("region", { name: "Overdue" })).getByText("Chase the reference")).toBeInTheDocument();
    expect(within(within(panel).getByRole("region", { name: "Upcoming" })).getByText("Send the brief")).toBeInTheDocument();
  });

  it("ticks the earliest task done from the bar itself", async () => {
    answer({ open: [aTask("t1", { title: "Call back" })] });
    vi.mocked(tasksApi.updateTask).mockResolvedValue(aTask("t1", { title: "Call back", status: "done" }));
    renderBar();

    await userEvent.click(await screen.findByRole("checkbox", { name: "Mark Call back done" }));

    expect(tasksApi.updateTask).toHaveBeenCalledWith("t1", { status: "done" });
    expect(await screen.findByRole("button", { name: "Undo" })).toBeInTheDocument();
  });

  it("offers to set one when there is none, without alarm", async () => {
    const onOpen = vi.fn();
    answer({});
    renderBar({ onOpen });

    expect(await screen.findByText("No next action for Jawaher")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Set next action" }));
    expect(onOpen).toHaveBeenCalledWith(null);
  });

  it("asks for the next one right after the last was done", async () => {
    answer({});
    renderBar({ justCompleted: true });

    expect(await screen.findByText("Done. Set the next one?")).toBeInTheDocument();
  });
});

describe("the popover's done tasks", () => {
  beforeEach(() => {
    vi.mocked(tasksApi.getCandidateTasks).mockImplementation(async (_project, _candidate, _signal, allDone) => ({
      open: [aTask("t1", { title: "Call back" })],
      done: allDone ? EVERY_DONE : EVERY_DONE.slice(0, 3),
      doneCount: EVERY_DONE.length,
    }));
  });

  it("folds them away, shows the last three, and every one when asked", async () => {
    renderBar();
    await userEvent.click(await screen.findByRole("button", { name: /Next action: Call back/ }));
    const panel = await screen.findByRole("dialog", { name: "Next actions" });
    expect(within(panel).queryByText("Finished task 1")).not.toBeInTheDocument();

    await userEvent.click(within(panel).getByRole("button", { name: /Done/ }));
    expect(within(panel).getAllByText(/^Finished task/)).toHaveLength(3);

    await userEvent.click(within(panel).getByRole("button", { name: "Show all 5" }));
    expect(await within(panel).findByText("Finished task 5")).toBeInTheDocument();
    expect(tasksApi.getCandidateTasks).toHaveBeenLastCalledWith("p1", "c1", expect.anything(), true);
  });
});

describe("a next action set from a note", () => {
  it("says which note, and brings it into view", async () => {
    const onShowNote = vi.fn();
    answer({
      open: [
        aTask("t1", {
          title: "Call back",
          note: { id: "n1", kind: "call", writtenAt: new Date(2026, 9, 3, 8, 0).toISOString() },
        }),
      ],
    });
    renderBar({ onShowNote });

    await userEvent.click(await screen.findByRole("button", { name: /Next action: Call back/ }));
    await userEvent.click(await screen.findByRole("button", { name: "From a call note · 03 Oct 2026" }));
    expect(onShowNote).toHaveBeenCalledWith("n1");
  });
});
