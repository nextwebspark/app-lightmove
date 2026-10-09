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

/** The workspace menu on the workspace's name: the switcher, the workspace's own pages, and managing workspaces. */
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

    await userEvent.click(await screen.findByRole("button", { name: /^Workspace:/ }));

    expect(screen.getByRole("menuitem", { name: "Manage workspaces" })).toBeInTheDocument();
    expect(screen.queryByText("Switch to")).not.toBeInTheDocument();
    // Personal items live on the avatar now.
    expect(screen.queryByRole("menuitem", { name: "Sign out" })).not.toBeInTheDocument();
  });

  it("lists the other workspaces and switches the session into the one picked", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w2",
      expiresIn: 900,
      user: aUser({ workspace: second, workspaces: [home, second] }),
    });
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /^Workspace:/ }));
    expect(screen.getByText("Current workspace")).toBeInTheDocument();
    // The current one heads the menu; only the others are offered as a move.
    expect(screen.queryByRole("menuitem", { name: /NextWebSpark Search/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("menuitem", { name: /Meridian Search Partners/ }));

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

    await userEvent.click(await screen.findByRole("button", { name: /^Workspace:/ }));
    await userEvent.click(screen.getByRole("menuitem", { name: /Invited to Northgate Search/ }));

    await waitFor(() => expect(authApi.acceptInvitationById).toHaveBeenCalledWith("inv-1"));
    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalledWith("w3"));
  });

  it("names the current workspace on its trigger, and follows a switch", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w2",
      expiresIn: 900,
      user: aUser({ workspace: second, workspaces: [home, second] }),
    });
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: `Workspace: ${home.name}` }));
    await userEvent.click(screen.getByRole("menuitem", { name: /Meridian Search Partners/ }));

    expect(await screen.findByRole("button", { name: "Workspace: Meridian Search Partners" })).toBeInTheDocument();
  });

  it("shows a pending invitation on the closed trigger", async () => {
    vi.mocked(authApi.me).mockResolvedValue(
      aUser({
        workspace: home,
        pendingInvitations: [{ id: "inv-1", workspaceName: "Northgate Search", role: "MEMBER", inviterName: "Omar" }],
      }),
    );
    renderAt();

    expect(
      await screen.findByRole("button", { name: `Workspace: ${home.name} · 1 invitation to another workspace` }),
    ).toBeInTheDocument();
  });

  it("is a keyboard menu: opening focuses the first item, arrows move, Escape returns to the trigger", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    const user = userEvent.setup();
    renderAt();

    const trigger = await screen.findByRole("button", { name: /^Workspace:/ });
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");
    await user.click(trigger);
    const items = screen.getAllByRole("menuitem");
    expect(items[0]).toHaveFocus();
    await user.keyboard("{ArrowDown}");
    expect(items[1]).toHaveFocus();
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("offers Team, workspace settings and Create workspace to an admin", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home }));
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /^Workspace:/ }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Create workspace" }));

    expect(screen.getByTestId("pathname").textContent).toBe("/settings/workspaces");
  });

  it("leaves the session where it was when the switch is refused", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    const { ApiRequestError } = await import("../../lib/apiClient");
    vi.mocked(switchWorkspaceSession).mockRejectedValue(
      new ApiRequestError({ code: "NOT_A_MEMBER", detail: "Workspace not found", status: 404, correlationId: "c1" }),
    );
    renderAt();

    await userEvent.click(await screen.findByRole("button", { name: /^Workspace:/ }));
    await userEvent.click(screen.getByRole("menuitem", { name: /Meridian Search Partners/ }));

    await waitFor(() => expect(switchWorkspaceSession).toHaveBeenCalled());
    expect(screen.getByTestId("pathname").textContent).toBe("/projects/p1");
    expect(await screen.findByText(/That workspace isn't available to you any more/)).toBeInTheDocument();
  });
});

/** The avatar opens the person's own menu, on every screen — a project's people are drawn beside it. */
describe("Topbar — account menu", () => {
  const renderWith = (actions?: React.ReactNode) =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <Topbar actions={actions} />
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
    vi.mocked(authApi.me).mockResolvedValue(aUser({ fullName: "Alok Kumar" }));
  });

  it("opens the person's menu from the avatar, with Sign out", async () => {
    vi.mocked(authApi.logout).mockResolvedValue(undefined);
    renderWith();

    await userEvent.click(await screen.findByRole("button", { name: "Account: Alok Kumar" }));

    expect(screen.getByRole("menuitem", { name: "Your profile" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Security" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /mode$/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("menuitem", { name: "Sign out" }));
    await waitFor(() => expect(authApi.logout).toHaveBeenCalled());
  });

  it("keeps the avatar beside a project's people", async () => {
    renderWith(<span>People bar</span>);

    expect(await screen.findByText("People bar")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Account: Alok Kumar" })).toBeInTheDocument();
  });
});
