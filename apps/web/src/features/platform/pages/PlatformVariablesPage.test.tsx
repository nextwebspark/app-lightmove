import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import * as platformConfigApi from "../api/platformConfigApi";
import type { PlatformSetting } from "../api/types";
import { PlatformVariablesPage } from "./PlatformVariablesPage";

vi.mock("../api/platformConfigApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/platformConfigApi")>()),
  listSettings: vi.fn(),
  changeSetting: vi.fn(),
  resetSetting: vi.fn(),
  refreshPlatformConfig: vi.fn(),
}));

const picks: PlatformSetting = {
  key: "enrichment.sourcing.picks-per-company",
  group: "SOURCING",
  type: "INT",
  label: "People filed per company",
  description: "The best-ranked hits filed at each company.",
  min: 1,
  max: 10,
  defaultValue: "3",
  value: "4",
  overridden: true,
  updatedByName: "Lina Haddad",
  updatedAt: "2026-10-28T09:00:00Z",
};

const ceiling: PlatformSetting = {
  key: "billing.fair-use.assistant-ask-per-seat",
  group: "FAIR_USE",
  type: "LONG",
  label: "Assistant questions",
  description: "Questions the assistant may answer each month per staff seat. 0 is no ceiling.",
  min: 0,
  max: 1_000_000,
  defaultValue: "1500",
  value: "1500",
  overridden: false,
  updatedByName: null,
  updatedAt: null,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <PlatformVariablesPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const rowOf = (label: string) => screen.getByText(label).closest("li") as HTMLElement;

describe("PlatformVariablesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(platformConfigApi.listSettings).mockResolvedValue({
      settings: [picks, ceiling],
      config: { currentVersion: 14, loadedVersion: 14, loadedAt: "2026-11-02T09:00:00Z" },
    });
  });

  it("groups each value by area with its default, and who changed an overridden one", async () => {
    renderPage();

    expect(await screen.findByRole("region", { name: "Find executives" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Fair use per seat, each month" })).toBeInTheDocument();
    const row = rowOf("People filed per company");
    expect(within(row).getByText("Default 3")).toBeInTheDocument();
    expect(within(row).getByText("Changed by Lina Haddad · 28 Oct 2026")).toBeInTheDocument();
    expect(within(rowOf("Assistant questions")).getByText("Default 1,500")).toBeInTheDocument();
    expect(within(rowOf("Assistant questions")).queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
    expect(screen.getByText("Version 14")).toBeInTheDocument();
  });

  it("opens the editor without sending anything, and keeps it open", async () => {
    vi.mocked(platformConfigApi.changeSetting).mockResolvedValue(picks);
    renderPage();
    await screen.findByText("Default 3");
    const row = rowOf("People filed per company");

    await userEvent.click(within(row).getByRole("button", { name: "Edit" }));
    await new Promise((settled) => setTimeout(settled, 50));

    expect(platformConfigApi.changeSetting).not.toHaveBeenCalled();
    expect(within(row).getByLabelText("People filed per company")).toHaveValue("4");
  });

  it("saves an edit, and shows the server's refusal under the field rather than as a toast", async () => {
    vi.mocked(platformConfigApi.changeSetting).mockRejectedValueOnce(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "One or more fields are invalid",
        status: 400,
        correlationId: "ref",
        fieldErrors: { [picks.key]: "Must be between 1 and 10" },
      }),
    );
    vi.mocked(platformConfigApi.changeSetting).mockResolvedValueOnce({ ...picks, value: "5" });
    renderPage();
    await screen.findByText("Default 3");
    const row = rowOf("People filed per company");

    await userEvent.click(within(row).getByRole("button", { name: "Edit" }));
    const field = within(row).getByLabelText("People filed per company");
    await userEvent.clear(field);
    await userEvent.type(field, "11");
    await userEvent.click(within(row).getByRole("button", { name: "Save" }));
    expect(await within(row).findByText("Must be between 1 and 10")).toBeInTheDocument();

    await userEvent.clear(field);
    await userEvent.type(field, "5");
    await userEvent.click(within(row).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(platformConfigApi.changeSetting).toHaveBeenLastCalledWith(picks.key, "5"));
    expect(await screen.findByText("People filed per company saved")).toBeInTheDocument();
  });

  it("resets an overridden value to its default", async () => {
    vi.mocked(platformConfigApi.resetSetting).mockResolvedValue({ ...picks, value: "3", overridden: false });
    renderPage();
    await screen.findByText("Default 3");

    await userEvent.click(within(rowOf("People filed per company")).getByRole("button", { name: "Reset" }));

    await waitFor(() => expect(platformConfigApi.resetSetting).toHaveBeenCalledWith(picks.key));
    expect(await screen.findByText("People filed per company is back to its default")).toBeInTheDocument();
  });

  it("refreshes every server through the variables page's own route", async () => {
    vi.mocked(platformConfigApi.refreshPlatformConfig).mockResolvedValue({
      currentVersion: 15,
      loadedVersion: 15,
      loadedAt: null,
    });
    renderPage();
    await screen.findByText("Version 14");

    await userEvent.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() => expect(platformConfigApi.refreshPlatformConfig).toHaveBeenCalledWith("settings"));
    expect(await screen.findByText("Every server now runs version 15")).toBeInTheDocument();
  });

  it("says why when the list is refused, rather than drawing an empty page", async () => {
    const refused = new ApiRequestError({ code: "NOT_FOUND", detail: "Not found", status: 404, correlationId: "ref" });
    vi.mocked(platformConfigApi.listSettings).mockRejectedValue(refused);
    renderPage();

    expect(await screen.findByText(messageFor(refused))).toBeInTheDocument();
    expect(screen.queryByRole("region")).not.toBeInTheDocument();
  });
});
