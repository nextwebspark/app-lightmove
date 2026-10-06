import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import type { User } from "../../auth/api/types";
import * as consentApi from "../api/oauthConsentApi";
import type { ConsentContext } from "../api/types";
import { returnToClient } from "../lib/clientRedirect";
import { OAuthConsentPage } from "./OAuthConsentPage";

const auth: { user: User | null; loading: boolean; signOut: () => Promise<void> } = {
  user: null,
  loading: false,
  signOut: vi.fn(),
};
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => auth }));
vi.mock("../api/oauthConsentApi");
vi.mock("../lib/clientRedirect");

const REQUEST =
  "?response_type=code&client_id=claude&redirect_uri=https%3A%2F%2Fclaude.ai%2Fcb" +
  "&scope=projects%3Aread+companies%3Aread+candidates%3Aread+candidates.contacts%3Aread&state=client-state" +
  "&code_challenge=abc&code_challenge_method=S256&resource=https%3A%2F%2Fbeta.uncava.com%2Fapi%2Fv1%2Fmcp";

const staff = (workspaceId = "ws-af"): User =>
  ({
    id: "u1",
    email: "yara@alfuttaim.com",
    fullName: "Yara Haddad",
    emailVerified: true,
    workspace: { id: workspaceId, name: "Al-Futtaim", roles: ["ADMIN"] },
    workspaces: [],
    pendingInvitations: [],
  }) as unknown as User;

const context = (overrides: Partial<ConsentContext> = {}): ConsentContext => ({
  clientId: "claude",
  clientName: "Claude",
  clientKind: "CIMD",
  clientHost: "claude.ai",
  verified: true,
  clientUri: "https://claude.ai",
  logoUri: null,
  redirectHost: "claude.ai",
  requestedScopes: ["projects:read", "companies:read", "candidates:read", "candidates.contacts:read"],
  workspaces: [{ id: "ws-af", name: "Al-Futtaim", eligible: true }],
  ...overrides,
});

/** The login route as far as the consent screen cares: it signs in and returns to `from`, as LoginPage does. */
function LoginStub() {
  const location = useLocation();
  const navigate = useNavigate();
  const from = (location.state as { from?: string } | null)?.from ?? "/";
  return (
    <div>
      <p>Sign in to continue to {from}</p>
      <button
        type="button"
        onClick={() => {
          auth.user = staff();
          navigate(from, { replace: true });
        }}
      >
        Sign in
      </button>
    </div>
  );
}

const renderAt = (search: string) =>
  render(
    <MemoryRouter initialEntries={[`/oauth/consent${search}`]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/oauth/consent" element={<OAuthConsentPage />} />
          <Route path="/login" element={<LoginStub />} />
          <Route path="/" element={<p>Projects</p>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("OAuthConsentPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    auth.user = staff();
    auth.loading = false;
    vi.mocked(consentApi.getConsentContext).mockResolvedValue(context());
  });

  it("sends a signed-out visitor to sign in and back to the request with every parameter intact", async () => {
    auth.user = null;
    const user = userEvent.setup();
    renderAt(REQUEST);

    expect(await screen.findByText(`Sign in to continue to /oauth/consent${REQUEST}`)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Claude wants to read your Uncava data")).toBeInTheDocument();
    expect(consentApi.getConsentContext).toHaveBeenCalledWith(
      "claude",
      "https://claude.ai/cb",
      "projects:read companies:read candidates:read candidates.contacts:read",
    );
  });

  it("offers what was asked, personal data unticked, and allows with the ticked scopes", async () => {
    vi.mocked(consentApi.storeAuthorizationRequest).mockResolvedValue({
      clientId: "claude",
      state: "stored-state",
      requestedScopes: [],
      workspaceId: "ws-af",
    });
    vi.mocked(consentApi.answerConsent).mockResolvedValue({ redirectUri: "https://claude.ai/cb?code=c1&state=s" });
    const user = userEvent.setup();
    renderAt(REQUEST);

    expect(await screen.findByRole("checkbox", { name: /Positions/ })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("checkbox", { name: /Contact details/ })).toHaveAttribute("aria-checked", "false");
    expect(screen.queryByRole("checkbox", { name: /Compensation/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /Companies/ }));
    await user.click(screen.getByRole("button", { name: "Allow" }));

    expect(await screen.findByText("Claude is connected")).toBeInTheDocument();
    expect(screen.getByText(/read positions and executives in Al-Futtaim/)).toBeInTheDocument();
    const stored = vi.mocked(consentApi.storeAuthorizationRequest).mock.calls[0];
    expect(stored[0].get("code_challenge")).toBe("abc");
    expect(stored[1]).toBe("ws-af");
    expect(consentApi.answerConsent).toHaveBeenCalledWith("claude", "stored-state", [
      "projects:read",
      "candidates:read",
    ]);
    expect(returnToClient).toHaveBeenCalledWith("https://claude.ai/cb?code=c1&state=s");
  });

  it("connects the workspace picked, not the session's", async () => {
    auth.user = staff("ws-af");
    vi.mocked(consentApi.getConsentContext).mockResolvedValue(
      context({
        workspaces: [
          { id: "ws-af", name: "Al-Futtaim", eligible: true },
          { id: "ws-meridian", name: "Meridian Search Partners", eligible: true },
          { id: "ws-portal", name: "Client portal", eligible: false },
        ],
      }),
    );
    vi.mocked(consentApi.storeAuthorizationRequest).mockResolvedValue({
      clientId: "claude",
      state: "s2",
      requestedScopes: [],
      workspaceId: "ws-meridian",
    });
    vi.mocked(consentApi.answerConsent).mockResolvedValue({ redirectUri: "https://claude.ai/cb?code=c2" });
    const user = userEvent.setup();
    renderAt(REQUEST);

    expect(await screen.findByRole("radio", { name: "Al-Futtaim" })).toHaveAttribute("aria-checked", "true");
    expect(screen.queryByRole("radio", { name: "Client portal" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Meridian Search Partners" }));
    await user.click(screen.getByRole("button", { name: "Allow" }));

    await waitFor(() => expect(consentApi.storeAuthorizationRequest).toHaveBeenCalled());
    expect(vi.mocked(consentApi.storeAuthorizationRequest).mock.calls[0][1]).toBe("ws-meridian");
  });

  it("denies with no scope, and the client is sent its access_denied", async () => {
    vi.mocked(consentApi.storeAuthorizationRequest).mockResolvedValue({
      clientId: "claude",
      state: "s3",
      requestedScopes: [],
      workspaceId: "ws-af",
    });
    vi.mocked(consentApi.answerConsent).mockResolvedValue({
      redirectUri: "https://claude.ai/cb?error=access_denied&state=s",
    });
    const user = userEvent.setup();
    renderAt(REQUEST);

    await user.click(await screen.findByRole("button", { name: "Deny" }));

    expect(await screen.findByText(/was told no and can read nothing/)).toBeInTheDocument();
    expect(consentApi.answerConsent).toHaveBeenCalledWith("claude", "s3", []);
    expect(returnToClient).toHaveBeenCalledWith("https://claude.ai/cb?error=access_denied&state=s");
  });

  it("shows a pure client representative the refused state and asks nothing", async () => {
    vi.mocked(consentApi.getConsentContext).mockResolvedValue(
      context({ workspaces: [{ id: "ws-af", name: "Al-Futtaim", eligible: false }] }),
    );
    renderAt(REQUEST);

    expect(await screen.findByText("AI apps are for your firm's staff")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Allow" })).not.toBeInTheDocument();
    expect(consentApi.storeAuthorizationRequest).not.toHaveBeenCalled();
  });

  it("warns that a self-registered app is unverified, naming where it sends you", async () => {
    vi.mocked(consentApi.getConsentContext).mockResolvedValue(
      context({ clientName: "my-agent", clientKind: "DCR", clientHost: null, verified: false, redirectHost: "127.0.0.1:33418" }),
    );
    renderAt(REQUEST);

    expect(await screen.findByText("Uncava can't confirm who made this app")).toBeInTheDocument();
    expect(screen.getByText("Unverified")).toBeInTheDocument();
    expect(screen.getByText("Registered itself")).toBeInTheDocument();
  });

  it("draws a verified app's own logo, and never a self-registered app's", async () => {
    vi.mocked(consentApi.getConsentContext).mockResolvedValue(context({ logoUri: "https://claude.ai/images/claude.svg" }));
    const { container, unmount } = renderAt(REQUEST);
    await screen.findByText("Claude wants to read your Uncava data");
    expect(container.querySelector("img[src^=\"https:\"]")).toHaveAttribute("src", "https://claude.ai/images/claude.svg");
    unmount();

    vi.mocked(consentApi.getConsentContext).mockResolvedValue(
      context({ clientKind: "DCR", clientHost: null, verified: false, logoUri: "https://claude.ai/images/claude.svg" }),
    );
    const unverified = renderAt(REQUEST);
    await screen.findByText("Uncava can't confirm who made this app");
    expect(unverified.container.querySelector("img[src^=\"https:\"]")).toBeNull();
  });

  it("shows an unknown client or redirect as an error and never sends the browser anywhere", async () => {
    vi.mocked(consentApi.getConsentContext).mockRejectedValue(
      new ApiRequestError({ code: "OAUTH_CLIENT_NOT_FOUND", detail: "internal", status: 404, correlationId: "c" }),
    );
    renderAt(REQUEST);

    expect(await screen.findByRole("alert")).toHaveTextContent(/never registered/);
    expect(returnToClient).not.toHaveBeenCalled();
  });

  it("shows the authorization server's own error without asking anyone to sign in", async () => {
    auth.user = null;
    renderAt("?error=invalid_request");

    expect(await screen.findByText("This connection can't go ahead")).toBeInTheDocument();
    expect(consentApi.getConsentContext).not.toHaveBeenCalled();
  });
});
