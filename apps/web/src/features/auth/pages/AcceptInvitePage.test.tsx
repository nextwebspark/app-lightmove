import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../AuthProvider";
import { AcceptInvitePage } from "./AcceptInvitePage";
import * as authApi from "../api/authApi";
import { pendingInvite } from "../pendingInvite";

vi.mock("../api/authApi");

// AuthProvider boots by exchanging the refresh cookie for an access token, and only then asks who the
// user is. Without a token it never calls `me()` at all — so a test that wants a signed-in user has to
// hand it one. The rest of apiClient (ApiRequestError, in particular) stays real.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * Since membership is invitation-only, this page is the only door into an existing workspace. These
 * cover the states an invitee can actually turn up in — token in hand from the emailed link, or
 * token-less in a fresh tab where only the server-derived invitation can route them.
 */
describe("AcceptInvitePage", () => {
  const invitation = {
    email: "sara@nextwebspark.com",
    role: "MEMBER" as const,
    workspaceName: "NextWebSpark Search",
    inviterName: "Alok Kumar",
  };

  const renderAt = (search = "?token=abc123") =>
    render(
      <MemoryRouter initialEntries={[`/auth/accept-invite${search}`]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <AcceptInvitePage />
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    vi.mocked(authApi.previewInvitation).mockResolvedValue(invitation);
    // Anonymous by default: no refresh cookie, so no session.
    vi.mocked(restoreSession).mockResolvedValue(null);
  });

  it("shows a stranger what they were invited to, and offers them an account", async () => {
    // The ordinary case. The invitee has never heard of us.
    renderAt();

    expect(await screen.findByText(/Join NextWebSpark Search/)).toBeInTheDocument();
    expect(screen.getByText(/Alok Kumar invited you as Member/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /create your account/i })).toBeInTheDocument();
  });

  /**
   * The token has to survive signup and the verification click, both full page loads. Losing it is how
   * the invitee ends up back at square one — and the address travels with it, because acceptance checks
   * the account's email against the invited one.
   */
  it("remembers the token and the invited address across the signup detour", async () => {
    renderAt();
    await screen.findByText(/Join NextWebSpark Search/);

    expect(pendingInvite.peek()).toEqual({ token: "abc123", email: "sara@nextwebspark.com" });
  });

  it("refuses to let a forwarded invitation admit somebody else", async () => {
    vi.mocked(restoreSession).mockResolvedValue("access-token");
    vi.mocked(authApi.me).mockResolvedValue({
      id: "u2",
      email: "omar@nextwebspark.com", // not the invited address
      fullName: "Omar Khalil",
      title: null,
      avatarUrl: null,
      emailVerified: true,
      onboardingHeld: false,
      pendingInvitation: null,
      workspace: null,
    });

    renderAt();

    expect(
      await screen.findByText(/This invitation is for sara@nextwebspark.com/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /accept invitation/i })).not.toBeInTheDocument();
  });

  it("tells an expired invitation apart from an invalid one", async () => {
    vi.mocked(authApi.previewInvitation).mockRejectedValue(
      new ApiRequestError({
        code: "INVITATION_EXPIRED",
        detail: "This invitation has expired",
        status: 410,
        correlationId: "x",
      }),
    );

    renderAt();

    expect(await screen.findByText("This invitation has expired")).toBeInTheDocument();
  });

  /**
   * The fresh-tab case: the emailed token lives in another tab's sessionStorage, but the server knows
   * the invitation — `user.pendingInvitation` — and the page must accept from it, token-lessly.
   */
  it("renders and accepts from the server-derived invitation when there is no token", async () => {
    const sara = {
      id: "u3",
      email: "sara@nextwebspark.com",
      fullName: "Sara Al-Mansour",
      title: null,
      avatarUrl: null,
      emailVerified: true,
      onboardingHeld: false,
      pendingInvitation: { workspaceName: "NextWebSpark Search", role: "MEMBER" as const },
      workspace: null,
    };
    vi.mocked(restoreSession).mockResolvedValue("access-token");
    vi.mocked(authApi.me).mockResolvedValue(sara);
    vi.mocked(authApi.acceptPendingInvitation).mockResolvedValue({
      ...sara,
      pendingInvitation: null,
    });

    const user = userEvent.setup();
    renderAt(""); // no ?token — the bug this flow exists to fix

    expect(await screen.findByText(/Join NextWebSpark Search/)).toBeInTheDocument();
    expect(screen.getByText(/You were invited as Member/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /accept invitation/i }));

    await waitFor(() => expect(authApi.acceptPendingInvitation).toHaveBeenCalled());
    // The token path is never touched — there is no token to redeem.
    expect(authApi.acceptInvitation).not.toHaveBeenCalled();
  });
});
