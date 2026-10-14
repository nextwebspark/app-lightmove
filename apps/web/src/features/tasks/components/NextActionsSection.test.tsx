import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as tasksApi from "../api/tasksApi";
import type { TaskResponse } from "../api/types";
import { NextActionsSection } from "./NextActionsSection";

vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: { id: "u1", fullName: "Yara Haddad" } }) }));
vi.mock("../api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/tasksApi")>()),
  getCandidateTasks: vi.fn(),
}));

const yara = { userId: "u1", fullName: "Yara Haddad", avatarUrl: null };

const aDoneTask = (index: number): TaskResponse => ({
  id: `done-${index}`,
  projectId: "p1",
  projectTitle: "Chief Financial Officer",
  candidateId: "c1",
  personId: "person-1",
  personName: "Jawaher Al Suwaidi",
  title: `Finished task ${index}`,
  kind: "todo",
  detail: null,
  presetId: null,
  note: null,
  assignee: yara,
  createdBy: yara,
  dueAt: null,
  status: "done",
  source: "manual",
  rule: null,
  completedAt: new Date(2026, 9, 14 - index, 12).toISOString(),
  completedBy: yara,
  createdAt: new Date(2026, 9, 1).toISOString(),
  editable: true,
});

const EVERY_DONE = Array.from({ length: 5 }, (_, index) => aDoneTask(index + 1));

function renderSection(onShowNote?: (noteId: string) => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <NextActionsSection
          projectId="p1"
          candidateId="c1"
          firstName="Jawaher"
          justCompleted={false}
          onCompleted={() => {}}
          onOpen={() => {}}
          onShowNote={onShowNote}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("the drawer's done tasks", () => {
  beforeEach(() => {
    vi.mocked(tasksApi.getCandidateTasks).mockImplementation(async (_project, _candidate, _signal, allDone) => ({
      open: [],
      done: allDone ? EVERY_DONE : EVERY_DONE.slice(0, 3),
      doneCount: EVERY_DONE.length,
    }));
  });

  it("shows the last three, and every one after See more", async () => {
    renderSection();

    const done = await screen.findByRole("group", { name: "Done" });
    expect(within(done).getAllByText(/^Finished task/)).toHaveLength(3);

    await userEvent.click(within(done).getByRole("button", { name: "See more · 2 more" }));

    expect(await within(done).findByText("Finished task 5")).toBeInTheDocument();
    expect(within(done).getAllByText(/^Finished task/)).toHaveLength(5);
    expect(tasksApi.getCandidateTasks).toHaveBeenLastCalledWith("p1", "c1", expect.anything(), true);

    await userEvent.click(within(done).getByRole("button", { name: "See less" }));
    expect(await within(done).findAllByText(/^Finished task/)).toHaveLength(3);
  });

  it("offers no See more when three or fewer are done", async () => {
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({
      open: [],
      done: EVERY_DONE.slice(0, 2),
      doneCount: 2,
    });
    renderSection();

    const done = await screen.findByRole("group", { name: "Done" });
    expect(within(done).queryByRole("button", { name: /See more/ })).not.toBeInTheDocument();
  });
});

describe("a next action set from a note", () => {
  it("says which note, and brings it into view", async () => {
    const onShowNote = vi.fn();
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({
      open: [
        {
          ...aDoneTask(1),
          id: "t1",
          title: "Call back",
          status: "open",
          completedAt: null,
          completedBy: null,
          note: { id: "n1", kind: "call", writtenAt: new Date(2026, 9, 3, 8, 0).toISOString() },
        },
      ],
      done: [],
      doneCount: 0,
    });
    renderSection(onShowNote);

    await userEvent.click(await screen.findByRole("button", { name: "From a call note · 03 Oct 2026" }));
    expect(onShowNote).toHaveBeenCalledWith("n1");
  });
});
