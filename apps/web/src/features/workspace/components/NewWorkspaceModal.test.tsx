import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import * as authApi from "../../auth/api/authApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import * as workspaceApi from "../api/workspaceApi";
import { NewWorkspaceModal } from "./NewWorkspaceModal";

vi.mock("../../auth/api/authApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../auth/api/authApi")>()),
  searchOnboardingCompanies: vi.fn(),
}));
vi.mock("../api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/workspaceApi")>()),
  createWorkspace: vi.fn(),
  invite: vi.fn(),
}));

const switchWorkspace = vi.fn();
vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: aUser(), switchWorkspace }),
}));

const meridian: CompanySuggestion = {
  apolloAccountId: "apollo-meridian",
  companyName: "Meridian Search Partners",
  industry: "staffing and recruiting",
  companyCity: "Dubai",
  companyCountry: "United Arab Emirates",
  website: "https://meridian.example",
  logoUrl: null,
  numEmployees: 30,
};

function Pathname() {
  return <span data-testid="pathname">{useLocation().pathname}</span>;
}

/** Details, then switch, then invites — in that order, because invitations address the session's workspace. */
describe("NewWorkspaceModal", () => {
  const onClose = vi.fn();

  const renderModal = () =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={["/settings/workspaces"]}>
          <NewWorkspaceModal open onClose={onClose} />
          <Routes>
            <Route path="*" element={<Pathname />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.searchOnboardingCompanies).mockResolvedValue([meridian]);
    const created = aWorkspace({ id: "w2", name: "Meridian Search Partners" });
    vi.mocked(workspaceApi.createWorkspace).mockResolvedValue(aUser({ workspace: created }));
    vi.mocked(workspaceApi.invite).mockResolvedValue({ sent: 1 });
    switchWorkspace.mockResolvedValue(aUser({ workspace: created }));
  });

  it("creates the workspace, switches into it, then invites the team from there", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Merid");
    await user.click(await screen.findByRole("button", { name: /Meridian Search Partners/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() =>
      expect(workspaceApi.createWorkspace).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Meridian Search Partners", apolloAccountId: "apollo-meridian" }),
      ),
    );
    // Switched before the invite stage is shown, so the invitations land on the new roster.
    await waitFor(() => expect(switchWorkspace).toHaveBeenCalledWith("w2"));
    expect(await screen.findByText("Invite your team")).toBeInTheDocument();

    await user.type(screen.getAllByLabelText("Colleague's email")[0], "sara@meridian.example");
    await user.click(screen.getByRole("button", { name: /send invites/i }));

    await waitFor(() =>
      expect(workspaceApi.invite).toHaveBeenCalledWith([{ email: "sara@meridian.example", role: "MEMBER" }]),
    );
    expect(onClose).toHaveBeenCalled();
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
  });

  it("skipping the invites still opens the new workspace", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Merid");
    await user.click(await screen.findByRole("button", { name: /Meridian Search Partners/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(await screen.findByRole("button", { name: /skip for now/i }));

    expect(workspaceApi.invite).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
  });
});
