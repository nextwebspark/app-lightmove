import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskPreset } from "../../tasks/api/types";
import { SettingsTaskPresetsPage } from "./SettingsTaskPresetsPage";

vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getTaskPresets: vi.fn(),
  getTaskPresetSuggestions: vi.fn(),
  createTaskPreset: vi.fn(),
  updateTaskPreset: vi.fn(),
  reorderTaskPresets: vi.fn(),
}));

const qualify: TaskPreset = {
  id: "p1",
  label: "Call to qualify interest",
  kind: "call",
  dueOffsetDays: 1,
  position: 0,
  retired: false,
  retiredAt: null,
  usage: 42,
};
const requestCv: TaskPreset = {
  id: "p2",
  label: "Request CV",
  kind: "email",
  dueOffsetDays: 2,
  position: 1,
  retired: false,
  retiredAt: null,
  usage: 1,
};
const salarySurvey: TaskPreset = {
  id: "p3",
  label: "Send salary survey",
  kind: "email",
  dueOffsetDays: null,
  position: 2,
  retired: true,
  retiredAt: new Date(2026, 9, 3, 11, 0).toISOString(),
  usage: 4,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsTaskPresetsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const presetRow = (label: string) => screen.getByRole("listitem", { name: label });

describe("SettingsTaskPresetsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(tasksApi.getTaskPresets).mockResolvedValue([qualify, requestCv, salarySurvey]);
    vi.mocked(tasksApi.getTaskPresetSuggestions).mockResolvedValue([
      { label: "Check notice period", count: 9 },
      { label: "request cv", count: 3 },
    ]);
  });

  it("lists each live preset with its type, default due and use, and keeps the retired ones folded", async () => {
    renderPage();

    const row = await screen.findByRole("listitem", { name: "Call to qualify interest" });
    expect(within(row).getByText("Call")).toBeInTheDocument();
    expect(within(row).getByText("+1 day")).toBeInTheDocument();
    expect(within(row).getByText("Used 42 times")).toBeInTheDocument();
    expect(within(presetRow("Request CV")).getByText("Used once")).toBeInTheDocument();
    expect(screen.queryByRole("listitem", { name: "Send salary survey" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retired/ })).toHaveAttribute("aria-expanded", "false");
  });

  it("adds a preset for the whole team", async () => {
    vi.mocked(tasksApi.createTaskPreset).mockResolvedValue({ ...qualify, id: "p9", label: "Reference check" });
    renderPage();
    await screen.findByRole("listitem", { name: "Request CV" });

    await userEvent.click(screen.getByRole("button", { name: "Add preset" }));
    await userEvent.type(screen.getByLabelText("Preset name"), "Reference check");
    await userEvent.selectOptions(screen.getByLabelText("Type"), "To-do");
    await userEvent.selectOptions(screen.getByLabelText("Default due"), "+5 days");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(tasksApi.createTaskPreset).toHaveBeenCalledWith({ label: "Reference check", kind: "todo", dueOffsetDays: 5 }),
    );
    expect(await screen.findByText("Preset added for the whole team")).toBeInTheDocument();
    expect(screen.queryByLabelText("Preset name")).not.toBeInTheDocument();
  });

  it("says when the team already has a preset by that name", async () => {
    vi.mocked(tasksApi.createTaskPreset).mockRejectedValue(
      new ApiRequestError({ code: "TASK_PRESET_EXISTS", detail: "exists", status: 409, correlationId: "c1" }),
    );
    renderPage();
    await screen.findByRole("listitem", { name: "Request CV" });

    await userEvent.click(screen.getByRole("button", { name: "Add preset" }));
    await userEvent.type(screen.getByLabelText("Preset name"), "Send salary survey");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Your team already has that preset.")).toBeInTheDocument();
    expect(screen.getByLabelText("Preset name")).toHaveValue("Send salary survey");
  });

  it("changes a preset's default due from its menu, clearing it as the server asks", async () => {
    vi.mocked(tasksApi.updateTaskPreset).mockResolvedValue({ ...requestCv, dueOffsetDays: null });
    renderPage();
    await screen.findByRole("listitem", { name: "Request CV" });

    await userEvent.click(screen.getByRole("button", { name: "Actions for Request CV" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Change default due" }));
    await userEvent.selectOptions(screen.getByLabelText("Default due"), "None");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(tasksApi.updateTaskPreset).toHaveBeenCalledWith("p2", { clearDueOffset: true }));
    expect(await screen.findByText("Preset saved")).toBeInTheDocument();
  });

  it("retires a preset, and restores a retired one", async () => {
    vi.mocked(tasksApi.updateTaskPreset).mockResolvedValue(qualify);
    renderPage();
    await screen.findByRole("listitem", { name: "Call to qualify interest" });

    await userEvent.click(screen.getByRole("button", { name: "Actions for Call to qualify interest" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Retire" }));

    await waitFor(() => expect(tasksApi.updateTaskPreset).toHaveBeenCalledWith("p1", { retired: true }));
    expect(await screen.findByText("Retired — tasks that use it keep it")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Retired/ }));
    const retired = presetRow("Send salary survey");
    expect(within(retired).getByText("Retired 03 Oct 2026 · used 4 times")).toBeInTheDocument();
    await userEvent.click(within(retired).getByRole("button", { name: "Restore" }));

    await waitFor(() => expect(tasksApi.updateTaskPreset).toHaveBeenCalledWith("p3", { retired: false }));
    expect(await screen.findByText("Preset offered again")).toBeInTheDocument();
  });

  it("offers the titles the team keeps typing that no preset spells, and makes one", async () => {
    vi.mocked(tasksApi.createTaskPreset).mockResolvedValue({ ...qualify, id: "p9", label: "Check notice period" });
    renderPage();

    const card = await screen.findByRole("region", { name: "Most typed, not a preset" });
    expect(within(card).getByText("typed 9 times")).toBeInTheDocument();
    expect(within(card).queryByText("request cv")).not.toBeInTheDocument();

    await userEvent.click(within(card).getByRole("button", { name: "Make a preset of Check notice period" }));
    expect(screen.getByLabelText("Preset name")).toHaveValue("Check notice period");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(tasksApi.createTaskPreset).toHaveBeenCalledWith({ label: "Check notice period", kind: "todo", dueOffsetDays: 1 }),
    );
  });

  it("reorders from the keyboard and saves the new order of what is offered", async () => {
    vi.mocked(tasksApi.reorderTaskPresets).mockResolvedValue([
      { ...requestCv, position: 0 },
      { ...qualify, position: 1 },
      salarySurvey,
    ]);
    renderPage();
    await screen.findByRole("listitem", { name: "Request CV" });

    screen.getByRole("button", { name: "Reorder Request CV" }).focus();
    await userEvent.keyboard("{ArrowUp}");

    await waitFor(() => expect(tasksApi.reorderTaskPresets).toHaveBeenCalledWith(["p2", "p1"]));
    const rows = within(screen.getByRole("list", { name: "Presets" })).getAllByRole("listitem");
    expect(rows.map((row) => row.getAttribute("aria-label"))).toEqual(["Request CV", "Call to qualify interest"]);
  });
});
