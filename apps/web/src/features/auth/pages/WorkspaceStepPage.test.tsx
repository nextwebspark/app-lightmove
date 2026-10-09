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

describe("WorkspaceStepPage — the firm types its own name, and a market match is optional", () => {
  const nameBox = () => screen.getByPlaceholderText("e.g. Meridian Search Partners");

  it("continues with a typed name, filing no universe id", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.searchOnboardingCompanies).mockResolvedValue([]);
    renderPage();

    await user.click(screen.getByRole("radio", { name: /In-house talent team/ }));
    await user.type(nameBox(), "Nimbus Partners");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ mode: "COMPANY", name: "Nimbus Partners", apolloAccountId: null }),
    );
  });

  it("continues with a typed name even while a match is on offer", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("radio", { name: /Search firm/ }));
    await user.type(nameBox(), "Al-Fut");
    expect(await screen.findByRole("button", { name: "Use Al-Futtaim" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ mode: "AGENCY", name: "Al-Fut", apolloAccountId: null }),
    );
  });

  it("files a taken match by its universe id and canonical name", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("radio", { name: /Search firm/ }));
    await user.type(nameBox(), "Al-Fut");
    await user.click(await screen.findByRole("button", { name: "Use Al-Futtaim" }));
    expect(screen.getByText(/Matched to/)).toHaveTextContent("Matched to Al-Futtaim · Dubai, United Arab Emirates");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ mode: "AGENCY", name: "Al-Futtaim", apolloAccountId: "apollo-af" }),
    );
  });

  it("drops a match once the name is edited, or declined", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("radio", { name: /Search firm/ }));
    await user.type(nameBox(), "Al-Fut");
    await user.click(await screen.findByRole("button", { name: "Use Al-Futtaim" }));
    await user.click(screen.getByRole("button", { name: "Not us" }));
    await user.type(nameBox(), " Group");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.createWorkspace).toHaveBeenCalled());
    expect(authApi.createWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Al-Futtaim Group", apolloAccountId: null }),
    );
  });

  it("names the field by who the firm hires for", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("Your firm or company's name")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /Search firm/ }));
    expect(screen.getByText("Your firm's name")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /In-house talent team/ }));
    expect(screen.getByText("Your company's name")).toBeInTheDocument();
  });

  it("refuses to continue until the firm says who it hires for, and has a name", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Choose who you hire for")).toBeInTheDocument();
    expect(screen.getByText("Enter your firm or company's name")).toBeInTheDocument();
    expect(authApi.createWorkspace).not.toHaveBeenCalled();
  });

  it("reopens a saved workspace with what it was described as", async () => {
    const user = userEvent.setup();
    currentUser = {
      workspace: {
        id: "w1",
        name: "Nimbus Partners",
        slug: "nimbus",
        logoMark: "N",
        mode: "AGENCY",
        emailDomain: "nimbus.example",
        roles: ["ADMIN"],
        joinedAt: null,
        company: null,
      },
    };
    vi.mocked(authApi.updateWorkspace).mockResolvedValue({} as User);
    renderPage();

    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(authApi.updateWorkspace).toHaveBeenCalled());
    expect(authApi.updateWorkspace).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: "AGENCY",
        name: "Nimbus Partners",
      }),
    );
  });
});
