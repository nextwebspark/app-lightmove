import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { WorkspaceDetail } from "../../workspace/api/types";
import { SettingsGeneralPage } from "./SettingsGeneralPage";
import { WORKSPACE_DATE_TIME } from "../../../test/fixtures/user";

vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  workspace: vi.fn(),
  updateWorkspace: vi.fn(),
  changeMode: vi.fn(),
  updateDateTime: vi.fn(),
}));

vi.mock("../../strategy/api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../strategy/api/companiesApi")>()),
  searchCompanies: vi.fn(),
}));

const reload = vi.fn();

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ reload }),
}));

const alFuttaim: CompanySuggestion = {
  apolloAccountId: "apollo-af",
  companyName: "Al-Futtaim",
  industry: "retail",
  companyCity: "Dubai",
  companyCountry: "United Arab Emirates",
  website: "https://alfuttaim.com",
  logoUrl: "https://logos.example/af.png",
  numEmployees: 42000,
};

const typedWorkspace = {
  id: "ws-1",
  name: "Typed Firm",
  slug: "typed-firm",
  logoMark: "T",
  mode: "COMPANY",
  calendarSync: "RECALL",
  emailDomain: "typed.example",
  defaultRegion: "GCC",
  defaultCurrency: "USD",
  plan: "TRIAL",
  memberCount: 1,
  createdAt: "2026-09-01T00:00:00Z",
  persona: { summary: null, sectors: [], competitors: [], geographies: [], notes: null },
  company: null,
  dateTime: WORKSPACE_DATE_TIME,
} satisfies WorkspaceDetail;

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <SettingsGeneralPage />
      </ToastProvider>
    </QueryClientProvider>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(workspaceApi.workspace).mockResolvedValue(typedWorkspace);
  vi.mocked(workspaceApi.updateWorkspace).mockResolvedValue(typedWorkspace);
  vi.mocked(workspaceApi.changeMode).mockResolvedValue({ ...typedWorkspace, mode: "AGENCY" });
  vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [alFuttaim] });
  vi.mocked(workspaceApi.updateDateTime).mockResolvedValue(typedWorkspace);
});

describe("SettingsGeneralPage — the workspace's date and time", () => {
  it("opens on the country's zone and the standard formats, already chosen", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Date & time" })).toBeInTheDocument();
    expect(screen.getByLabelText("Time zone")).toHaveValue("Asia/Dubai");
    expect(screen.getByLabelText("Date format")).toHaveValue("DD_MMM_YYYY");
    expect(screen.getByRole("radio", { name: "14:30 · 24-hour" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("Applies to everyone in the workspace")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset to standard" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save date & time" })).toBeDisabled();
  });

  it("saves the admin's choice from the fixed lists, previewed before it is saved", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByLabelText("Time zone"), "Europe/London");
    await user.selectOptions(screen.getByLabelText("Date format"), "MMM_D_YYYY");
    await user.click(screen.getByRole("radio", { name: "2:30 PM · 12-hour" }));
    expect(screen.getByText(/^\w{3}, \w{3} \d{1,2}, \d{4}, \d{1,2}:\d{2} [AP]M GMT(\+1)? · London$/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save date & time" }));

    await waitFor(() =>
      expect(workspaceApi.updateDateTime).toHaveBeenCalledWith({
        timeZone: "Europe/London",
        dateFormat: "MMM_D_YYYY",
        timeFormat: "H12",
      }),
    );
    expect(await screen.findByText("Date and time saved")).toBeInTheDocument();
  });

  it("puts the standard back in one click, saved as not chosen so the country's still reaches it", async () => {
    vi.mocked(workspaceApi.workspace).mockResolvedValue({
      ...typedWorkspace,
      memberCount: 4,
      dateTime: { ...WORKSPACE_DATE_TIME, timeZone: "Asia/Riyadh", dateFormat: "YYYY_MM_DD", membersFollowing: 3 },
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Applies to 3 of 4 members · 1 chose their own")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Reset to standard" }));
    expect(screen.getByLabelText("Time zone")).toHaveValue("Asia/Dubai");
    await user.click(screen.getByRole("button", { name: "Save date & time" }));

    await waitFor(() =>
      expect(workspaceApi.updateDateTime).toHaveBeenCalledWith({ timeZone: null, dateFormat: null, timeFormat: null }),
    );
  });
});

describe("SettingsGeneralPage — the workspace's firm", () => {
  it("re-picks the firm from the company database and files it by its universe id", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Change" }));
    await user.type(screen.getByPlaceholderText("Search company database…"), "Al-Fut");
    await user.click(await screen.findByRole("button", { name: /Al-Futtaim/ }));
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(workspaceApi.updateWorkspace).toHaveBeenCalled());
    expect(workspaceApi.updateWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Al-Futtaim", apolloAccountId: "apollo-af" }),
    );
  });

  it("keeps a typed firm typed, with no universe id", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Change" });
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(workspaceApi.updateWorkspace).toHaveBeenCalled());
    expect(workspaceApi.updateWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Typed Firm", apolloAccountId: "" }),
    );
  });

  it("will not save with no firm chosen", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Change" }));

    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
  });
});

describe("SettingsGeneralPage — who the workspace hires for", () => {
  it("shows the mode in force", async () => {
    renderPage();

    expect(await screen.findByRole("radio", { name: /In-house talent team/ })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: /Search firm/ })).toHaveAttribute("aria-checked", "false");
  });

  it("asks before switching, and a cancel switches nothing", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("radio", { name: /Search firm/ }));
    const warning = screen.getByRole("dialog", { name: "Switch to Search firm?" });
    expect(warning).toHaveTextContent("This changes the workspace for everyone in it");
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(workspaceApi.changeMode).not.toHaveBeenCalled();
  });

  it("switches once confirmed, and re-reads the session so every screen relabels", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("radio", { name: /Search firm/ }));
    await user.click(screen.getByRole("button", { name: "Switch to Search firm" }));

    await waitFor(() => expect(workspaceApi.changeMode).toHaveBeenCalledWith("AGENCY"));
    await waitFor(() => expect(reload).toHaveBeenCalled());
    expect(await screen.findByRole("radio", { name: /Search firm/ })).toHaveAttribute("aria-checked", "true");
  });
});
