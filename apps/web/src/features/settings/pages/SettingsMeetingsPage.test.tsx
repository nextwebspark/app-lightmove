import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as availabilityApi from "../api/availabilityApi";
import type { WorkspaceAvailability } from "../api/availabilityApi";
import { SettingsMeetingsPage } from "./SettingsMeetingsPage";

vi.mock("../api/availabilityApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/availabilityApi")>()),
  getWorkspaceAvailability: vi.fn(),
  putWorkspaceAvailability: vi.fn(),
}));

const SUNDAY_TO_THURSDAY = (["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"] as const).map((day) => ({
  day,
  start: "08:00:00",
  end: "18:00:00",
}));

const SAUDI: WorkspaceAvailability = {
  weeklyHours: SUNDAY_TO_THURSDAY,
  weeklyHoursChosen: false,
  suggestedFromCountry: "Saudi Arabia",
  dailyEmailCap: 50,
  dailyEmailCapChosen: false,
  maxDailyEmailCap: 200,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsMeetingsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SettingsMeetingsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(availabilityApi.getWorkspaceAvailability).mockResolvedValue(SAUDI);
  });

  it("shows the week the firm's country keeps until an admin chooses one", async () => {
    renderPage();

    expect(await screen.findByText("Suggested from Saudi Arabia")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Work on Sun" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Work on Fri" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("combobox", { name: "Thu ends" })).toHaveValue("18:00");
  });

  it("saves a short Thursday and a lower cap", async () => {
    vi.mocked(availabilityApi.putWorkspaceAvailability).mockResolvedValue({ ...SAUDI, weeklyHoursChosen: true });
    renderPage();

    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Thu ends" }), "13:00");
    const cap = screen.getByRole("spinbutton", { name: "Daily email limit" });
    await userEvent.clear(cap);
    await userEvent.type(cap, "30");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(availabilityApi.putWorkspaceAvailability).toHaveBeenCalledWith({
        weeklyHours: [
          { day: "MONDAY", start: "08:00", end: "18:00" },
          { day: "TUESDAY", start: "08:00", end: "18:00" },
          { day: "WEDNESDAY", start: "08:00", end: "18:00" },
          { day: "THURSDAY", start: "08:00", end: "13:00" },
          { day: "SUNDAY", start: "08:00", end: "18:00" },
        ],
        dailyEmailCap: 30,
      }),
    );
    expect(await screen.findByText("Meetings & email saved")).toBeInTheDocument();
  });

  it("will not save a week nobody works or a cap past the deployment's", async () => {
    renderPage();

    for (const day of ["Sun", "Mon", "Tue", "Wed", "Thu"]) {
      await userEvent.click(await screen.findByRole("button", { name: `Work on ${day}` }));
    }
    expect(screen.getByRole("alert")).toHaveTextContent("Choose at least one working day.");
    await userEvent.click(screen.getByRole("button", { name: "Mon–Fri" }));
    expect(screen.getByRole("button", { name: "Work on Fri" })).toHaveAttribute("aria-pressed", "true");

    const cap = screen.getByRole("spinbutton", { name: "Daily email limit" });
    await userEvent.clear(cap);
    await userEvent.type(cap, "500");
    expect(screen.getByRole("alert")).toHaveTextContent("Choose a limit from 1 to 200.");
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
  });
});
