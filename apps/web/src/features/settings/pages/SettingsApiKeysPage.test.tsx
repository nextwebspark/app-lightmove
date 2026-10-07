import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as apiKeysApi from "../api/apiKeysApi";
import type { ApiKey } from "../api/types";
import { SettingsApiKeysPage } from "./SettingsApiKeysPage";

vi.mock("../api/apiKeysApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/apiKeysApi")>()),
  apiKeys: vi.fn(),
  createApiKey: vi.fn(),
  revokeApiKey: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

const SECRET = "uncava_pat_7Kq2vXyR9bN4mTc8LwZp1sHe6uJfA3dGkQ5rVo0iYtBn_f9a1c03e";

function aKey(overrides: Partial<ApiKey> = {}): ApiKey {
  return {
    id: "k1",
    name: "Power BI dashboard",
    kind: "PERSONAL",
    status: "ACTIVE",
    tokenHint: "uncava_pat_7Kq2…f9a1",
    scopes: ["projects:read", "candidates.contacts:read"],
    ownerUserId: "u1",
    ownerName: "Alok Kumar",
    createdByName: "Alok Kumar",
    createdAt: "2026-09-02T09:00:00Z",
    expiresAt: new Date(Date.now() + 60 * 86_400_000).toISOString(),
    lastUsedAt: null,
    lastUsedIp: null,
    revokedAt: null,
    revokedByName: null,
    revokedReason: null,
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <SettingsApiKeysPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
  return queryClient;
}

/** Settings → API keys: the secret is shown once and kept nowhere, and only an admin chooses a workspace key. */
describe("SettingsApiKeysPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(apiKeysApi.apiKeys).mockResolvedValue([]);
  });

  it("shows a new key's secret once, then drops it from the page and every cache", async () => {
    const created = aKey({ id: "k9", name: "Excel sheet" });
    vi.mocked(apiKeysApi.createApiKey).mockResolvedValue({ key: created, secret: SECRET });
    const queryClient = renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Create key" }));
    await userEvent.type(screen.getByPlaceholderText("e.g. Power BI dashboard"), "Excel sheet");
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Create key" }));

    const reveal = await screen.findByRole("dialog", { name: "Copy your API key" });
    expect(within(reveal).getByLabelText("API key")).toHaveTextContent(SECRET);
    expect(apiKeysApi.createApiKey).toHaveBeenCalledWith({
      name: "Excel sheet",
      kind: "PERSONAL",
      scopes: ["projects:read", "companies:read", "candidates:read"],
      expiresInDays: 90,
    });

    await userEvent.keyboard("{Escape}");
    expect(screen.getByRole("dialog", { name: "Copy your API key" })).toBeInTheDocument();
    expect(within(reveal).getByText(/\$UNCAVA_API_KEY/)).toBeInTheDocument();
    expect(within(reveal).queryByText(/Bearer uncava_/)).not.toBeInTheDocument();

    await userEvent.click(within(reveal).getByRole("button", { name: "I've copied it" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(document.body.textContent).not.toContain(SECRET);
    const cached = JSON.stringify([
      queryClient.getQueryCache().getAll().map((query) => query.state.data),
      queryClient.getMutationCache().getAll().map((mutation) => mutation.state.data),
    ]);
    expect(cached).not.toContain(SECRET);
    expect(JSON.stringify({ ...localStorage })).not.toContain(SECRET);
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(SECRET);
  });

  it("opts a key in to MCP with mcp:use, off by default, and never makes one that reads nothing", async () => {
    vi.mocked(apiKeysApi.createApiKey).mockResolvedValue({ key: aKey(), secret: SECRET });
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Create key" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.type(within(dialog).getByPlaceholderText("e.g. Power BI dashboard"), "Claude Code");
    const mcp = within(dialog).getByRole("checkbox", { name: /mcp:use/ });
    expect(mcp).toHaveAttribute("aria-checked", "false");

    await userEvent.click(mcp);
    for (const scope of ["projects:read", "companies:read", "candidates:read"]) {
      await userEvent.click(within(dialog).getByRole("checkbox", { name: new RegExp(`^${scope}`) }));
    }
    expect(within(dialog).getByRole("button", { name: "Create key" })).toBeDisabled();

    await userEvent.click(within(dialog).getByRole("checkbox", { name: /^projects:read/ }));
    expect(within(dialog).getByText(/An AI agent holding it reads the same/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Create key" }));

    expect(apiKeysApi.createApiKey).toHaveBeenCalledWith(
      expect.objectContaining({ scopes: ["projects:read", "mcp:use"] }),
    );
  });

  it("offers an admin the workspace kind and the All keys view", async () => {
    vi.mocked(apiKeysApi.createApiKey).mockResolvedValue({ key: aKey({ kind: "SERVICE" }), secret: SECRET });
    renderPage();

    expect(await screen.findByRole("radio", { name: /All keys in/ })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Create key" }));
    await userEvent.type(screen.getByPlaceholderText("e.g. Power BI dashboard"), "ATS sync");
    await userEvent.click(within(screen.getByRole("radiogroup", { name: "Kind of key" })).getByRole("radio", { name: /Workspace/ }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Create key" }));

    await waitFor(() =>
      expect(apiKeysApi.createApiKey).toHaveBeenCalledWith(expect.objectContaining({ kind: "SERVICE" })),
    );
  });

  it("offers a member neither the kind choice nor the All keys view", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Create key" }));

    expect(screen.queryByRole("radiogroup", { name: "Kind of key" })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /All keys in/ })).not.toBeInTheDocument();
    expect(apiKeysApi.apiKeys).toHaveBeenCalledWith(false, expect.anything());
  });

  it("lists keys with their status and personal-data scopes, and revokes one after a confirmation", async () => {
    vi.mocked(apiKeysApi.apiKeys).mockResolvedValue([
      aKey(),
      aKey({ id: "k2", name: "Old script", status: "REVOKED", revokedAt: "2026-09-14T09:00:00Z", revokedByName: "Alok Kumar" }),
    ]);
    vi.mocked(apiKeysApi.revokeApiKey).mockResolvedValue(undefined);
    renderPage();

    expect(await screen.findByText("Power BI dashboard")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Revoked")).toBeInTheDocument();
    expect(screen.getAllByText("candidates.contacts:read")).toHaveLength(2);
    expect(screen.queryByRole("button", { name: "Revoke Old script" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Revoke Power BI dashboard" }));
    expect(apiKeysApi.revokeApiKey).not.toHaveBeenCalled();
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Revoke key" }));

    await waitFor(() => expect(apiKeysApi.revokeApiKey).toHaveBeenCalledWith("k1"));
    expect(await screen.findByText("Power BI dashboard revoked")).toBeInTheDocument();
  });

  it("says so when the key ceiling is reached", async () => {
    vi.mocked(apiKeysApi.createApiKey).mockRejectedValue(
      new ApiRequestError({ code: "API_KEY_LIMIT_REACHED", detail: "server words", status: 409, correlationId: "c1" }),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Create key" }));
    await userEvent.type(screen.getByPlaceholderText("e.g. Power BI dashboard"), "Eleventh");
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Create key" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("You have the most API keys allowed.");
  });
});
