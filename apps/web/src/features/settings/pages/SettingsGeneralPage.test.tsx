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

vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  workspace: vi.fn(),
  updateWorkspace: vi.fn(),
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
  emailDomain: "typed.example",
  defaultRegion: "GCC",
  defaultCurrency: "USD",
  plan: "TRIAL",
  memberCount: 1,
  createdAt: "2026-09-01T00:00:00Z",
  persona: { summary: null, sectors: [], competitors: [], geographies: [], notes: null },
  company: null,
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
  vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [alFuttaim] });
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
