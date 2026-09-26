import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../features/auth/AuthProvider";
import * as authApi from "../../features/auth/api/authApi";
import { aUser, aWorkspace } from "../../test/fixtures/user";
import { ToastProvider } from "../ui";
import { Topbar } from "./Topbar";

vi.mock("../../features/auth/api/authApi");
vi.mock("../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
  switchWorkspaceSession: vi.fn(),
}));

const { restoreSession, switchWorkspaceSession } = await import("../../lib/apiClient");

function Pathname() {
  return <span data-testid="pathname">{useLocation().pathname}</span>;
}

/** The workspace menu under the mark: the switcher, and the door to managing workspaces. */
describe("Topbar — workspace menu", () => {
  const home = aWorkspace();
  const second = aWorkspace({ id: "w2", name: "Meridian Search Partners", logoMark: "M", roles: ["MEMBER"] });

  const renderAt = () =>
    render(
      <MemoryRouter initialEntries={["/projects/p1"]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <Topbar />
              <Routes>
                <Route path="*" element={<Pathname />} />
              </Routes>
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
  });

  it("offers no switcher to a member of one workspace, only the way to manage them", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home }));
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /uncava/i }));

    expect(screen.getByText("Manage workspaces")).toBeInTheDocument();
    expect(screen.queryByText("Workspaces")).not.toBeInTheDocument();
    expect(screen.queryByText("Current workspace")).not.toBeInTheDocument();
  });

  it("lists the other workspaces and switches the session into the one picked", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w2",
      expiresIn: 900,
      user: aUser({ workspace: second, workspaces: [home, second] }),
    });
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /uncava/i }));
    expect(screen.getByText("Current workspace")).toBeInTheDocument();
    // The current one heads the menu; only the others are offered as a move.
    expect(screen.queryByRole("button", { name: /NextWebSpark Search/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Meridian Search Partners/ }));

    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalledWith("w2"));
    // A project route of the old workspace means nothing in the new one: the switch lands on home.
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
  });

  it("offers a pending invitation, and accepting joins then switches", async () => {
    vi.mocked(authApi.me).mockResolvedValue(
      aUser({
        workspace: home,
        pendingInvitations: [{ id: "inv-1", workspaceName: "Northgate Search", role: "MEMBER", inviterName: "Omar" }],
      }),
    );
    const joined = aWorkspace({ id: "w3", name: "Northgate Search", roles: ["MEMBER"] });
    vi.mocked(authApi.acceptInvitationById).mockResolvedValue(
      aUser({ workspace: joined, workspaces: [home, joined] }),
    );
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w3",
      expiresIn: 900,
      user: aUser({ workspace: joined, workspaces: [home, joined] }),
    });
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /uncava/i }));
    await userEvent.click(screen.getByRole("button", { name: /Invited to Northgate Search/ }));

    await waitFor(() => expect(authApi.acceptInvitationById).toHaveBeenCalledWith("inv-1"));
    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalledWith("w3"));
  });

  it("leaves the session where it was when the switch is refused", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    const { ApiRequestError } = await import("../../lib/apiClient");
    vi.mocked(switchWorkspaceSession).mockRejectedValue(
      new ApiRequestError({ code: "NOT_A_MEMBER", detail: "Workspace not found", status: 404, correlationId: "c1" }),
    );
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /uncava/i }));
    await userEvent.click(screen.getByRole("button", { name: /Meridian Search Partners/ }));

    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalled());
    expect(screen.getByTestId("pathname").textContent).toBe("/projects/p1");
    expect(await screen.findByText(/Workspace not found/)).toBeInTheDocument();
  });
});
