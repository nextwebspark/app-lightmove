import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project, TeamMember } from "../../projects/api/types";
import * as tasksApi from "../api/tasksApi";
import type { TaskPreset, TaskResponse } from "../api/types";
import { NextActionModal, type NextActionTarget } from "./NextActionModal";

const viewer = {
  id: "u1",
  fullName: "Yara Haddad",
  avatarUrl: null,
  workspace: { roles: ["MEMBER"], mode: "COMPANY" },
};
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: viewer }) }));
vi.mock("../api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/tasksApi")>()),
  getTaskPresets: vi.fn(),
  getCandidateTasks: vi.fn(),
  createTask: vi.fn(),
  updateTask: vi.fn(),
  cancelTask: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));

const seat = (userId: string, fullName: string, projectRoles: TeamMember["projectRoles"]): TeamMember => ({
  memberId: `m-${userId}`,
  userId,
  fullName,
  avatarUrl: null,
  workspaceRoles: ["MEMBER"],
  projectRoles,
});

const position = {
  id: "p1",
  positionTitle: "Chief Financial Officer",
  team: [
    seat("u3", "Sara Al-Mansour", ["RESEARCHER"]),
    seat("u9", "Khalid Al Marzooqi", ["CLIENT"]),
    seat("u1", "Yara Haddad", ["LEAD"]),
    seat("u4", "Omar Khalil", ["LEAD"]),
    seat("u2", "Dana Aboud", ["RESEARCHER"]),
  ],
} as Project;

const preset = (id: string, label: string, kind: TaskPreset["kind"], dueOffsetDays: number | null, retired = false): TaskPreset => ({
  id,
  label,
  kind,
  dueOffsetDays,
  position: Number(id.slice(2)),
  retired,
  retiredAt: retired ? "2026-09-01T00:00:00Z" : null,
  usage: 0,
});

const PRESETS = [
  preset("pr1", "Call to qualify interest", "call", 1),
  preset("pr2", "Reference check", "todo", 5),
  preset("pr3", "Debrief after meeting", "follow_up", 0),
  preset("pr4", "Old habit", "todo", 2, true),
];

const newTarget: NextActionTarget = {
  projectId: "p1",
  candidateId: "c1",
  personId: "person-1",
  executiveName: "Fatima Al Mazrouei",
};

const local = (day: number, hour: number) => new Date(2026, 9, day, hour, 0).toISOString();

const openTask: TaskResponse = {
  id: "t1",
  projectId: "p1",
  projectTitle: "Chief Financial Officer",
  candidateId: "c1",
  personId: "person-1",
  personName: "Fatima Al Mazrouei",
  title: "Share profile with hiring manager",
  kind: "todo",
  detail: "Before Thursday",
  presetId: null,
  note: null,
  assignee: { userId: "u3", fullName: "Sara Al-Mansour", avatarUrl: null },
  createdBy: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
  dueAt: local(3, 16),
  status: "open",
  source: "manual",
  rule: null,
  completedAt: null,
  completedBy: null,
  createdAt: local(1, 9),
  editable: true,
};

function renderModal(target: NextActionTarget = newTarget) {
  const onClose = vi.fn();
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <NextActionModal target={target} onClose={onClose} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
  return { onClose };
}

const whatField = () => screen.getByRole("combobox", { name: "What needs doing" });
const dueLine = () => screen.getByText(/^Due \d/);

describe("NextActionModal", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
    vi.mocked(tasksApi.getTaskPresets).mockResolvedValue(PRESETS);
    vi.mocked(projectsApi.projects).mockResolvedValue([position]);
    vi.mocked(tasksApi.createTask).mockImplementation(async (_p, _c, task) => ({ ...openTask, ...task }) as TaskResponse);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("offers me first and chosen, then the leads, then the researchers A–Z, and never a client seat", async () => {
    renderModal();

    const assign = await screen.findByRole("button", { name: /^Assign to/ });
    expect(assign).toHaveAccessibleName("Assign to Me · Yara Haddad");
    await userEvent.click(assign);

    const options = within(screen.getByRole("listbox", { name: "Assign to" })).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "YHMe · Yara HaddadLead",
      "OKOmar KhalilLead",
      "DADana AboudResearcher",
      "SASara Al-MansourResearcher",
    ]);
    expect(options[0]).toHaveAttribute("aria-selected", "true");
    expect(within(options[2]).getByTitle("Dana Aboud")).toBeInTheDocument();
    expect(screen.queryByText("Khalid Al Marzooqi")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Not here? Add to team →" })).toHaveAttribute("href", "/projects/p1/team");
  });

  it("hides Add to team from a researcher", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([
      { ...position, team: [seat("u1", "Yara Haddad", ["RESEARCHER"]), seat("u4", "Omar Khalil", ["LEAD"])] } as Project,
    ]);
    renderModal();

    await userEvent.click(await screen.findByRole("button", { name: /^Assign to/ }));

    expect(screen.getByText("This position's team only")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Add to team/ })).not.toBeInTheDocument();
  });

  it("takes a preset's title, kind and due, and saves it as that preset", async () => {
    renderModal();
    await screen.findByRole("option", { name: /Call to qualify interest/ });
    expect(screen.queryByRole("option", { name: /Old habit/ })).not.toBeInTheDocument();

    await userEvent.type(whatField(), "ref");
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Use “ref”Your own",
      "Reference checkTo-do · +5 days",
    ]);
    await userEvent.click(screen.getByRole("option", { name: /Reference check/ }));

    expect(whatField()).toHaveValue("Reference check");
    expect(screen.getByRole("radio", { name: "To-do" })).toBeChecked();
    expect(dueLine()).toHaveTextContent(/^Due 08 Oct 2026, 09:00 · /);

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith("p1", "c1", {
        title: "Reference check",
        kind: "todo",
        assigneeUserId: "u1",
        dueAt: local(8, 9),
        detail: null,
        presetId: "pr2",
        noteId: null,
      }),
    );
    expect(await screen.findByText("Next action set · due 08 Oct 2026, 09:00")).toBeInTheDocument();
  });

  it("puts a same-day preset at the end of today", async () => {
    renderModal();

    await userEvent.click(await screen.findByRole("option", { name: /Debrief after meeting/ }));

    expect(dueLine()).toHaveTextContent(/^Due 03 Oct 2026, 17:00 · /);
    expect(screen.getByRole("radio", { name: "Follow-up" })).toBeChecked();
  });

  it("keeps free text as written, with no preset behind it", async () => {
    renderModal();
    await screen.findByRole("option", { name: /Call to qualify interest/ });

    await userEvent.type(whatField(), "Ask about the Riyadh move");
    expect(screen.getByText("No preset matches. Your text is saved as written.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("option", { name: /Use “Ask about the Riyadh move”/ }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith("p1", "c1", {
        title: "Ask about the Riyadh move",
        kind: "call",
        assigneeUserId: "u1",
        dueAt: local(4, 9),
        detail: null,
        presetId: null,
        noteId: null,
      }),
    );
  });

  it("files the next action against the note it was set from, and says so", async () => {
    renderModal({
      ...newTarget,
      note: { id: "n1", kind: "call", writtenAt: new Date(2026, 9, 3, 8, 0).toISOString() },
    });
    await screen.findByRole("option", { name: /Call to qualify interest/ });

    expect(screen.getByText("Linked to a call note · 03 Oct 2026")).toBeInTheDocument();
    await userEvent.type(whatField(), "Call back after the family talk");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith("p1", "c1", expect.objectContaining({ noteId: "n1" })),
    );
  });

  it("sets the due from the quick chips and says it back", async () => {
    renderModal();
    await screen.findByRole("option", { name: /Call to qualify interest/ });
    expect(screen.getByRole("button", { name: "Tomorrow 09:00" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(screen.getByRole("button", { name: "Next Monday" }));
    expect(screen.getByRole("button", { name: "Next Monday" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Tomorrow 09:00" })).toHaveAttribute("aria-pressed", "false");
    expect(dueLine()).toHaveTextContent(/^Due 05 Oct 2026, 09:00 · /);

    await userEvent.click(screen.getByRole("button", { name: "Today 17:00" }));
    expect(dueLine()).toHaveTextContent(/^Due 03 Oct 2026, 17:00 · /);

    await userEvent.click(screen.getByRole("button", { name: "In 3 days" }));
    expect(dueLine()).toHaveTextContent(/^Due 06 Oct 2026, 09:00 · /);
  });

  it("tells a colleague they have it, and saves and notifies them", async () => {
    renderModal();
    await screen.findByRole("option", { name: /Call to qualify interest/ });
    await userEvent.type(whatField(), "Call him back");

    await userEvent.click(screen.getByRole("button", { name: /^Assign to/ }));
    await userEvent.click(screen.getByRole("option", { name: /Sara Al-Mansour/ }));

    expect(screen.getByText(/is told in the app, and by email if it's still unread after 10 minutes/)).toHaveTextContent(
      "Sara Al-Mansour is told in the app, and by email if it's still unread after 10 minutes.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Save & notify Sara" }));

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith("p1", "c1", expect.objectContaining({ assigneeUserId: "u3" })),
    );
    expect(await screen.findByText("Assigned to Sara Al-Mansour · they've been notified")).toBeInTheDocument();
  });

  it("says why a save was refused", async () => {
    vi.mocked(tasksApi.createTask).mockRejectedValue(
      new ApiRequestError({ code: "TASK_ASSIGNEE_NOT_ON_TEAM", detail: "no", status: 400, correlationId: "c1" }),
    );
    renderModal();
    await screen.findByRole("option", { name: /Call to qualify interest/ });
    await userEvent.type(whatField(), "Call him back");

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("They aren't on this position's team.");
  });

  it("edits a task it is given, and offers Mark done and Delete", async () => {
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [openTask], done: [], doneCount: 0 });
    vi.mocked(tasksApi.updateTask).mockResolvedValue({ ...openTask, status: "done" });
    const { onClose } = renderModal({ ...newTarget, taskId: "t1" });

    expect(await screen.findByRole("combobox", { name: "What needs doing" })).toHaveValue(
      "Share profile with hiring manager",
    );
    expect(screen.getByRole("dialog", { name: "Edit next action" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Detail" })).toHaveValue("Before Thursday");
    expect(screen.getByRole("button", { name: /^Assign to/ })).toHaveAccessibleName("Assign to Sara Al-Mansour");
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete task" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Mark done" }));

    await waitFor(() => expect(tasksApi.updateTask).toHaveBeenCalledWith("t1", { status: "done" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("cancels the task it edits on Delete", async () => {
    vi.mocked(tasksApi.getCandidateTasks).mockResolvedValue({ open: [openTask], done: [], doneCount: 0 });
    vi.mocked(tasksApi.cancelTask).mockResolvedValue(undefined);
    renderModal({ ...newTarget, taskId: "t1" });

    await userEvent.click(await screen.findByRole("button", { name: "Delete task" }));

    await waitFor(() => expect(tasksApi.cancelTask).toHaveBeenCalledWith("t1"));
    expect(await screen.findByText("Task deleted")).toBeInTheDocument();
  });
});
