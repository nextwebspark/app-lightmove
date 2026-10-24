import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as availabilityApi from "../api/availabilityApi";
import type { MyAvailability } from "../api/availabilityApi";
import { SettingsAvailabilityPage } from "./SettingsAvailabilityPage";

vi.mock("../api/availabilityApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/availabilityApi")>()),
  getMyAvailability: vi.fn(),
  putMyAvailability: vi.fn(),
}));

const INHERITED: MyAvailability = {
  weeklyHours: null,
  workspaceWeeklyHours: (["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"] as const).map((day) => ({
    day,
    start: "09:00:00",
    end: "18:00:00",
  })),
  dailyEmailCap: null,
  workspaceDailyEmailCap: 50,
  timeZone: "Asia/Dubai",
  calendarStatesHours: false,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsAvailabilityPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SettingsAvailabilityPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(availabilityApi.getMyAvailability).mockResolvedValue(INHERITED);
  });

  it("follows the workspace until the consultant sets their own", async () => {
    renderPage();

    expect(await screen.findByText("Workspace default: Sun–Thu, 09:00–18:00")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Use workspace hours" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("button", { name: "Work on Mon" })).toBeDisabled();
    expect(screen.queryByText(/Outlook working hours/)).not.toBeInTheDocument();
  });

  it("saves their own week and a lower limit, and clears both back to the workspace's", async () => {
    vi.mocked(availabilityApi.putMyAvailability).mockResolvedValue(INHERITED);
    renderPage();

    await userEvent.click(await screen.findByRole("switch", { name: "Use workspace hours" }));
    await userEvent.click(screen.getByRole("button", { name: "Work on Sat" }));
    await userEvent.click(screen.getByRole("switch", { name: "Use workspace limit" }));
    const cap = screen.getByRole("spinbutton", { name: "My daily email limit" });
    await userEvent.clear(cap);
    await userEvent.type(cap, "60");
    expect(screen.getByRole("alert")).toHaveTextContent("Choose a limit from 1 to your workspace's 50.");
    await userEvent.clear(cap);
    await userEvent.type(cap, "20");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(availabilityApi.putMyAvailability).toHaveBeenCalledWith({
        weeklyHours: expect.arrayContaining([{ day: "SATURDAY", start: "08:00", end: "18:00" }]),
        dailyEmailCap: 20,
      }),
    );
  });

  it("says Outlook's hours apply until the consultant sets their own, and not once they have", async () => {
    vi.mocked(availabilityApi.getMyAvailability).mockResolvedValue({ ...INHERITED, calendarStatesHours: true });
    renderPage();

    expect(await screen.findByText(/follows your Outlook working hours where Outlook states them/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("switch", { name: "Use workspace hours" }));
    expect(screen.queryByText(/follows your Outlook working hours/)).not.toBeInTheDocument();
  });
});
