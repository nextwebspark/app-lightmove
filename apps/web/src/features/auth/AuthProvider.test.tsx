import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { aUser, aWorkspace } from "../../test/fixtures/user";
import { AuthProvider, useAuth } from "./AuthProvider";
import * as authApi from "./api/authApi";

vi.mock("./api/authApi");
vi.mock("../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
  switchWorkspaceSession: vi.fn(),
  onWorkspaceMoved: vi.fn(),
}));

const { onWorkspaceMoved, restoreSession, switchWorkspaceSession } = await import("../../lib/apiClient");
const { takeWorkspaceMove } = await import("./workspaceMoveNotice");

function SwitchButton() {
  const { user, switchWorkspace, acceptAndSwitch } = useAuth();
  return (
    <>
      <span data-testid="current">{user?.workspace?.name}</span>
      <span data-testid="count">{user?.workspaces.length}</span>
      <button type="button" onClick={() => void switchWorkspace("w2")}>
        switch
      </button>
      <button
        type="button"
        onClick={() => void acceptAndSwitch(() => authApi.acceptInvitationById("inv-1")).catch(() => undefined)}
      >
        accept
      </button>
    </>
  );
}

/**
 * A switch moves every tab sharing the refresh cookie, so it is announced on a BroadcastChannel — and
 * only the <i>other</i> tabs may act on it. The switching tab reloading itself threw away the New
 * workspace modal between its two stages.
 */
describe("AuthProvider — workspace switch broadcast", () => {
  const home = aWorkspace();
  const second = aWorkspace({ id: "w2", name: "Meridian Search Partners" });
  const replace = vi.fn();
  const realLocation = window.location;

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));
    vi.mocked(switchWorkspaceSession).mockResolvedValue({
      accessToken: "in-w2",
      expiresIn: 900,
      user: aUser({ workspace: second, workspaces: [home, second] }),
    });
    Object.defineProperty(window, "location", { configurable: true, value: { ...realLocation, replace } });
  });

  afterEach(() => {
    Object.defineProperty(window, "location", { configurable: true, value: realLocation });
  });

  const renderProvider = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider>
          <SwitchButton />
        </AuthProvider>
      </QueryClientProvider>,
    );

  it("does not reload the tab that switched, and tells the others", async () => {
    const otherTab = new BroadcastChannel("lm-workspace");
    const heard = vi.fn();
    otherTab.onmessage = (event) => heard(event.data);
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("current").textContent).toBe("NextWebSpark Search"));

    await userEvent.click(screen.getByRole("button", { name: "switch" }));

    await waitFor(() => expect(screen.getByTestId("current").textContent).toBe("Meridian Search Partners"));
    await waitFor(() => expect(heard).toHaveBeenCalledWith({ workspaceId: "w2" }));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(replace).not.toHaveBeenCalled();
    otherTab.close();
  });

  it("reloads from the top when another tab switched", async () => {
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("current").textContent).toBe("NextWebSpark Search"));

    const otherTab = new BroadcastChannel("lm-workspace");
    otherTab.postMessage({ workspaceId: "w2" });
    otherTab.close();

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
  });

  it("when a refresh lands in another workspace, restarts from the top and says why", async () => {
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("current").textContent).toBe("NextWebSpark Search"));
    const handler = vi.mocked(onWorkspaceMoved).mock.calls.at(-1)![0];

    handler(aUser({ workspace: second, workspaces: [second] }));

    expect(replace).toHaveBeenCalledWith("/");
    expect(takeWorkspaceMove()).toBe(
      "You no longer have access to NextWebSpark Search — you're now in Meridian Search Partners.",
    );
  });

  it("re-reads the user when accepting joined but the switch failed", async () => {
    const joined = aUser({ workspace: second, workspaces: [home, second] });
    vi.mocked(authApi.acceptInvitationById).mockResolvedValue(joined);
    vi.mocked(switchWorkspaceSession).mockRejectedValue(new Error("expired"));
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home] }));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("1"));
    await waitFor(() => expect(screen.getByTestId("current").textContent).toBe("NextWebSpark Search"));
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: home, workspaces: [home, second] }));

    await userEvent.click(screen.getByRole("button", { name: "accept" }));

    await waitFor(() => expect(authApi.me).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId("current").textContent).toBe("NextWebSpark Search");
    expect(screen.getByTestId("count").textContent).toBe("2");
  });
});
