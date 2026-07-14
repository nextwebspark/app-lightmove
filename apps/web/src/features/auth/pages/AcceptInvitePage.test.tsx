import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
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
 * The invitation is the flow that was silently broken: the backend could redeem one, and nothing in the
 * SPA ever asked it to, so an invited person landed in the approval queue they were meant to skip.
 *
 * These cover the states an invitee can actually turn up in — because every one of them used to be the
 * same state: bounced to a login screen for an account they did not have.
 */
describe("AcceptInvitePage", () => {
  const invitation = {
    email: "sara@nextwebspark.com",
    role: "CONSULTANT" as const,
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
    expect(screen.getByText(/Alok Kumar invited you as Consultant/)).toBeInTheDocument();
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
});
