import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CompanySuggestion } from "../../strategy/api/types";
import * as authApi from "../api/authApi";
import type { User } from "../api/types";
import { WorkspaceStepPage } from "./WorkspaceStepPage";

vi.mock("../api/authApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/authApi")>()),
  createWorkspace: vi.fn(),
  updateWorkspace: vi.fn(),
  searchOnboardingCompanies: vi.fn(),
}));

const reload = vi.fn();
let currentUser: Partial<User> | null = null;

vi.mock("../AuthProvider", () => ({
  useAuth: () => ({ user: currentUser, reload }),
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

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <WorkspaceStepPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  currentUser = { workspace: null };
  vi.mocked(authApi.searchOnboardingCompanies).mockResolvedValue([alFuttaim]);
  vi.mocked(authApi.createWorkspace).mockResolvedValue({} as User);
});

describe("WorkspaceStepPage — the organization is picked from the company database", () => {
  it("files a picked company by its universe id and sizes the firm from its headcount", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Al-Fut");
    await user.click(await screen.findByRole("button", { name: /Al-Futtaim/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Al-Futtaim",
        apolloAccountId: "apollo-af",
        companySize: "200+ people",
      }),
    );
  });

  it("takes a firm the database does not carry as a typed name, with no universe id", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.searchOnboardingCompanies).mockResolvedValue([]);
    renderPage();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Nimbus Partners");
    await user.click(await screen.findByRole("button", { name: /None of these/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Nimbus Partners", apolloAccountId: null }),
    );
  });

  it("refuses to continue on typed text that was never picked", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Al-Fut");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(
      await screen.findByText("Choose your organization from the list, or add it as new"),
    ).toBeInTheDocument();
    expect(authApi.createWorkspace).not.toHaveBeenCalled();
  });

  it("takes the prefilled size back out when the picked company is swapped for a typed one", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Al-Fut");
    await user.click(await screen.findByRole("button", { name: /Al-Futtaim/ }));
    await user.click(screen.getByRole("button", { name: "Change" }));
    vi.mocked(authApi.searchOnboardingCompanies).mockResolvedValue([]);
    await user.clear(screen.getByPlaceholderText("Search company database…"));
    await user.type(screen.getByPlaceholderText("Search company database…"), "Nimbus Partners");
    await user.click(await screen.findByRole("button", { name: /None of these/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Nimbus Partners", apolloAccountId: null, companySize: "1–10 people" }),
    );
  });

  it("reopens a saved workspace with what it was described as", async () => {
    const user = userEvent.setup();
    currentUser = {
      workspace: {
        id: "w1",
        name: "Nimbus Partners",
        slug: "nimbus",
        logoMark: "N",
        emailDomain: "nimbus.example",
        roles: ["ADMIN"],
        joinedAt: null,
        company: null,
        companySize: "51–200 people",
        primaryRegion: "Europe",
        teamFocus: "Board advisory",
      },
    };
    vi.mocked(authApi.updateWorkspace).mockResolvedValue({} as User);
    renderPage();

    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.updateWorkspace).toHaveBeenCalled());
    expect(authApi.updateWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Nimbus Partners",
        companySize: "51–200 people",
        primaryRegion: "Europe",
        teamFocus: "Board advisory",
      }),
    );
  });
});
