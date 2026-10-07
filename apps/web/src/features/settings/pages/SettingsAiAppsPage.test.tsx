import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as oauthGrantsApi from "../api/oauthGrantsApi";
import type { OAuthGrant } from "../api/types";
import { SettingsAiAppsPage } from "./SettingsAiAppsPage";

vi.mock("../api/oauthGrantsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/oauthGrantsApi")>()),
  oauthGrants: vi.fn(),
  revokeOAuthGrant: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

function aGrant(overrides: Partial<OAuthGrant> = {}): OAuthGrant {
  return {
    id: "g1",
    clientId: "https://claude.ai/oauth/mcp-client-metadata",
    clientName: "Claude",
    clientKind: "CIMD",
    clientHost: "claude.ai",
    verified: true,
    redirectHost: "claude.ai",
    logoUri: null,
    scopes: ["projects:read", "candidates.contacts:read"],
    ownerUserId: "u1",
    ownerName: "Alok Kumar",
    connectedAt: "2026-09-12T09:00:00Z",
    lastUsedAt: null,
    expiresAt: null,
    ...overrides,
  };
}

function renderPage() {
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsAiAppsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** Settings → Connected AI apps: what was allowed, and the one act here — disconnecting it. */
describe("SettingsAiAppsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(oauthGrantsApi.oauthGrants).mockResolvedValue([]);
  });

  it("lists each connection with its trust, where it is known from, its use and its scopes", async () => {
    vi.mocked(oauthGrantsApi.oauthGrants).mockResolvedValue([
      aGrant(),
      aGrant({
        id: "g2",
        clientId: "c-dcr",
        clientName: "my-agent",
        clientKind: "DCR",
        clientHost: null,
        verified: false,
        redirectHost: "127.0.0.1",
        logoUri: "https://claude.ai/logo.svg",
        scopes: ["projects:read"],
      }),
    ]);
    renderPage();

    const rows = await screen.findAllByRole("listitem");
    expect(within(rows[0]).getByText("Verified")).toBeInTheDocument();
    expect(within(rows[0]).getByText("claude.ai")).toBeInTheDocument();
    expect(within(rows[0]).getByText(/never used/)).toBeInTheDocument();
    expect(within(rows[0]).getByText("candidates.contacts:read")).toBeInTheDocument();
    expect(rows[0].querySelector("[data-mark]")).toHaveAttribute("data-mark", "claude");
    expect(within(rows[1]).getByText("Unverified")).toBeInTheDocument();
    expect(within(rows[1]).getByText("registered itself · 127.0.0.1")).toBeInTheDocument();
    expect(rows[1].querySelector("[data-mark]")).toHaveAttribute("data-mark", "letter");
    expect(screen.getByText(/\/api\/v1\/mcp$/)).toBeInTheDocument();
  });

  it("disconnects a connection after a confirmation, and it leaves the list", async () => {
    vi.mocked(oauthGrantsApi.oauthGrants).mockResolvedValueOnce([aGrant()]).mockResolvedValue([]);
    vi.mocked(oauthGrantsApi.revokeOAuthGrant).mockResolvedValue(undefined);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Disconnect Claude" }));
    const dialog = screen.getByRole("dialog", { name: "Disconnect Claude?" });
    expect(within(dialog).getByText(/its next request is refused/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Disconnect" }));

    expect(oauthGrantsApi.revokeOAuthGrant).toHaveBeenCalledWith("g1");
    expect(await screen.findByText("Claude disconnected")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("listitem")).not.toBeInTheDocument());
  });

  it("offers an admin the whole workspace and anyone's disconnect, naming whose each is", async () => {
    vi.mocked(oauthGrantsApi.oauthGrants).mockImplementation(async (all) =>
      all ? [aGrant({ id: "g3", ownerUserId: "u2", ownerName: "Sara Al-Mansour" })] : [],
    );
    renderPage();

    await userEvent.click(await screen.findByRole("radio", { name: /All in NextWebSpark Search/ }));

    expect(await screen.findByText(/Sara Al-Mansour · connected/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Disconnect Claude" })).toBeInTheDocument();
    expect(oauthGrantsApi.oauthGrants).toHaveBeenLastCalledWith(true, expect.anything());
  });

  it("tells an admin disconnecting a colleague's app that the colleague is the one asked again", async () => {
    vi.mocked(oauthGrantsApi.oauthGrants).mockImplementation(async (all) =>
      all ? [aGrant({ id: "g3", ownerUserId: "u2", ownerName: "Sara Al-Mansour" })] : [],
    );
    renderPage();

    await userEvent.click(await screen.findByRole("radio", { name: /All in NextWebSpark Search/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Disconnect Claude" }));

    const dialog = screen.getByRole("dialog", { name: "Disconnect Claude?" });
    expect(dialog).toHaveTextContent("Sara Al-Mansour's Claude loses access at once");
    expect(dialog).toHaveTextContent("Sara Al-Mansour will see the consent screen");
    expect(dialog).not.toHaveTextContent("you will see");
  });

  it("gives a member only their own connections, with no All view", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    renderPage();

    expect(await screen.findByText("You haven't connected an AI app")).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /All in/ })).not.toBeInTheDocument();
    expect(oauthGrantsApi.oauthGrants).toHaveBeenCalledWith(false, expect.anything());
  });

  it("points an empty page at the connection guide, opened beside Settings", async () => {
    renderPage();

    await screen.findByText("You haven't connected an AI app");
    const guide = screen.getByRole("link", { name: "Read the connection guide" });
    expect(guide).toHaveAttribute("href", "/docs/mcp");
    expect(guide).toHaveAttribute("target", "_blank");
  });
});
