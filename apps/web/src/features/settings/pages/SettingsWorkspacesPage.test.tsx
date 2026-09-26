import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import { SettingsWorkspacesPage } from "./SettingsWorkspacesPage";

vi.mock("../../auth/api/authApi");
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
  switchWorkspaceSession: vi.fn(),
}));

const { restoreSession, switchWorkspaceSession } = await import("../../../lib/apiClient");

function Pathname() {
  return <span data-testid="pathname">{useLocation().pathname}</span>;
}

/** Settings → Workspaces: what you are in, what you are invited to, and the door to another. */
describe("SettingsWorkspacesPage", () => {
  const home = aWorkspace();
  const second = aWorkspace({ id: "w2", name: "Meridian Search Partners", logoMark: "M", roles: ["MEMBER"] });

  const renderPage = () =>
    render(
      <MemoryRouter initialEntries={["/settings/workspaces"]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <SettingsWorkspacesPage />
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

  it("marks the current workspace and opens another", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w2",
      expiresIn: 900,
      user: aUser({ workspace: second, workspaces: [home, second] }),
    });
    renderPage();

    expect(await screen.findByText("NextWebSpark Search")).toBeInTheDocument();
    expect(screen.getByText("Current")).toBeInTheDocument();
    expect(screen.getByText(/2 workspaces/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Open" }));

    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalledWith("w2"));
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
  });

  it("lets a staff member create a workspace, and hides the door from a pure client", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home }));
    const { unmount } = renderPage();
    expect(await screen.findByRole("button", { name: /create workspace/i })).toBeInTheDocument();
    unmount();

    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) }));
    renderPage();
    expect(await screen.findByText("NextWebSpark Search")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /create workspace/i })).not.toBeInTheDocument();
  });

  it("accepts an invitation and switches into the workspace joined", async () => {
    vi.mocked(authApi.me).mockResolvedValue(
      aUser({
        workspace: home,
        pendingInvitations: [{ id: "inv-1", workspaceName: "Northgate Search", role: "MEMBER", inviterName: "Omar Haddad" }],
      }),
    );
    const joined = aWorkspace({ id: "w3", name: "Northgate Search", roles: ["MEMBER"] });
    vi.mocked(authApi.acceptInvitationById).mockResolvedValue(aUser({ workspace: joined, workspaces: [home, joined] }));
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w3",
      expiresIn: 900,
      user: aUser({ workspace: joined, workspaces: [home, joined] }),
    });
    renderPage();

    expect(await screen.findByText("Northgate Search")).toBeInTheDocument();
    expect(screen.getByText(/Omar Haddad invited you as Member/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() => expect(authApi.acceptInvitationById).toHaveBeenCalledWith("inv-1"));
    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalledWith("w3"));
  });
});
