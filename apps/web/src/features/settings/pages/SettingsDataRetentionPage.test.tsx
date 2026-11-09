import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as dataProtectionApi from "../../candidates/api/dataProtectionApi";
import * as dataRetentionApi from "../api/dataRetentionApi";
import type { CandidateRetention, RetentionDue } from "../api/dataRetentionApi";
import { SettingsDataRetentionPage } from "./SettingsDataRetentionPage";

vi.mock("../../candidates/api/dataProtectionApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/dataProtectionApi")>()),
  getDataProtectionConfig: vi.fn(),
}));
vi.mock("../api/dataRetentionApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/dataRetentionApi")>()),
  getCandidateRetention: vi.fn(),
  putCandidateRetention: vi.fn(),
  getRetentionDue: vi.fn(),
  keepDue: vi.fn(),
  eraseDue: vi.fn(),
  getSuppressions: vi.fn(),
  addSuppression: vi.fn(),
  removeSuppression: vi.fn(),
}));

const TWO_YEARS: CandidateRetention = { months: 24, chosen: false, defaultMonths: 24, options: [12, 24, 36, 60] };

const DUE: RetentionDue = {
  months: 24,
  cutoff: "2024-11-02T00:00:00Z",
  total: 2,
  people: [
    {
      personId: "p1",
      fullName: "Tariq Mahmoud",
      title: "Finance Director",
      employer: "Emaar Industries",
      lastActivityAt: "2024-08-14T10:00:00Z",
      lastActivityKind: "STATUS_CHANGED",
      positions: 1,
      ownerName: "Omar Khalil",
    },
    {
      personId: "p2",
      fullName: "Lina Farouk",
      title: null,
      employer: null,
      lastActivityAt: "2024-06-01T10:00:00Z",
      lastActivityKind: "RETENTION_KEPT",
      positions: 2,
      ownerName: null,
    },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsDataRetentionPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SettingsDataRetentionPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(dataProtectionApi.getDataProtectionConfig).mockResolvedValue({ enabled: true });
    vi.mocked(dataRetentionApi.getCandidateRetention).mockResolvedValue(TWO_YEARS);
    vi.mocked(dataRetentionApi.getRetentionDue).mockResolvedValue(DUE);
    vi.mocked(dataRetentionApi.getSuppressions).mockResolvedValue([]);
  });

  it("reads nothing and draws nothing where the deployment holds the controls back", async () => {
    vi.mocked(dataProtectionApi.getDataProtectionConfig).mockResolvedValue({ enabled: false });
    renderPage();

    await waitFor(() => expect(dataProtectionApi.getDataProtectionConfig).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByText("Data & retention")).not.toBeInTheDocument());
    expect(dataRetentionApi.getCandidateRetention).not.toHaveBeenCalled();
    expect(dataRetentionApi.getSuppressions).not.toHaveBeenCalled();
  });

  it("lists who is due with when they were last touched, and saves a new period", async () => {
    vi.mocked(dataRetentionApi.putCandidateRetention).mockResolvedValue({ ...TWO_YEARS, months: null, chosen: true });
    renderPage();

    const due = await screen.findByRole("table", { name: "Due for deletion" });
    expect(within(due).getByText("Tariq Mahmoud")).toBeInTheDocument();
    expect(within(due).getByText("Finance Director · Emaar Industries")).toBeInTheDocument();
    expect(within(due).getAllByText("Kept by an admin").length).toBeGreaterThan(0);
    expect(within(due).getByText("Nobody")).toBeInTheDocument();
    expect(screen.getByText("2 people")).toBeInTheDocument();

    const save = screen.getByRole("button", { name: "Save changes" });
    expect(save).toBeDisabled();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Retention period" }), "never");
    expect(screen.getByText(/Retention is off: candidates are kept until someone deletes them/)).toBeInTheDocument();
    await userEvent.click(save);

    await waitFor(() => expect(dataRetentionApi.putCandidateRetention).toHaveBeenCalledWith(null));
  });

  it("keeps the ticked people, and deletes others only after asking", async () => {
    vi.mocked(dataRetentionApi.keepDue).mockResolvedValue({ kept: 1 });
    vi.mocked(dataRetentionApi.eraseDue).mockResolvedValue({ erased: 1, noLongerDue: 1, sendInFlight: 0 });
    renderPage();

    await userEvent.click(await screen.findByRole("checkbox", { name: "Select Tariq Mahmoud" }));
    await userEvent.click(screen.getByRole("button", { name: "Keep" }));
    await waitFor(() => expect(dataRetentionApi.keepDue).toHaveBeenCalledWith(["p1"]));

    await userEvent.click(await screen.findByRole("checkbox", { name: "Select everyone due" }));
    await userEvent.click(screen.getByRole("button", { name: "Delete selected" }));
    const dialog = await screen.findByRole("dialog", { name: "Delete 2 candidates permanently?" });
    expect(within(dialog).getByText("Tariq Mahmoud and Lina Farouk")).toBeInTheDocument();
    expect(dataRetentionApi.eraseDue).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Delete permanently" }));

    await waitFor(() => expect(dataRetentionApi.eraseDue).toHaveBeenCalledWith(["p1", "p2"]));
    expect(await screen.findByText(/1 candidate deleted permanently · 1 person no longer due kept/)).toBeInTheDocument();
  });

  it("says nobody becomes due while retention is off", async () => {
    vi.mocked(dataRetentionApi.getCandidateRetention).mockResolvedValue({ ...TWO_YEARS, months: null, chosen: true });
    vi.mocked(dataRetentionApi.getRetentionDue).mockResolvedValue({ months: null, cutoff: null, total: 0, people: [] });
    renderPage();

    expect(await screen.findByText("Nobody is due for deletion")).toBeInTheDocument();
    expect(screen.getByText(/Choose a period above to start reviewing/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete selected" })).not.toBeInTheDocument();
  });

  it("adds a profile to the suppression list, refusing a link that names no profile", async () => {
    vi.mocked(dataRetentionApi.addSuppression).mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText(/Nobody is on the list/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Add to suppression list" }));
    const dialog = await screen.findByRole("dialog", { name: "Add to suppression list" });
    const profile = within(dialog).getByPlaceholderText("linkedin.com/in/…");
    await userEvent.type(profile, "linkedin.com/company/acme");
    await userEvent.click(within(dialog).getByRole("button", { name: "Add to list" }));
    expect(within(dialog).getByText("Paste a LinkedIn profile link, such as linkedin.com/in/name")).toBeInTheDocument();
    expect(dataRetentionApi.addSuppression).not.toHaveBeenCalled();

    await userEvent.clear(profile);
    await userEvent.type(profile, "linkedin.com/in/rami-saleh");
    await userEvent.click(within(dialog).getByRole("radio", { name: /Objected/ }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Add to list" }));
    await waitFor(() =>
      expect(dataRetentionApi.addSuppression).toHaveBeenCalledWith({
        linkedinUrl: "linkedin.com/in/rami-saleh",
        email: null,
        reason: "objected",
        note: null,
      }),
    );
  });

  it("asks before lifting an entry an erasure left", async () => {
    vi.mocked(dataRetentionApi.getSuppressions).mockResolvedValue([
      { id: "s1", kind: "linkedin", reason: "erased", note: null, addedBy: null, createdAt: "2026-10-05T10:00:00Z" },
    ]);
    vi.mocked(dataRetentionApi.removeSuppression).mockResolvedValue(undefined);
    renderPage();

    const list = await screen.findByRole("table", { name: "Suppression list" });
    await userEvent.click(within(list).getByRole("button", { name: "Remove" }));
    const dialog = await screen.findByRole("dialog", { name: "Remove from the suppression list?" });
    expect(within(dialog).getByText(/came from a permanent deletion/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Remove" }));
    await waitFor(() => expect(dataRetentionApi.removeSuppression).toHaveBeenCalledWith("s1"));
  });
});
