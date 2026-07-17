import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../AuthProvider";
import { AcceptInvitePage } from "./AcceptInvitePage";
import * as authApi from "../api/authApi";

vi.mock("../api/authApi");

// AuthProvider boots by exchanging the refresh cookie for an access token, and only then asks who the
// user is. Without a token it never calls `me()` — so a signed-in test has to hand it one. The rest of
// apiClient (ApiRequestError, in particular) stays real.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * Since membership is invitation-only, this page is the only door into an existing workspace. The common
 * arrival is a stranger with a token and no account, who sets a password here and is ACTIVE immediately —
 * no verification step. The signed-in and token-less cases are covered too.
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
    vi.mocked(authApi.previewInvitation).mockResolvedValue(invitation);
    // Anonymous by default: no refresh cookie, so no session.
    vi.mocked(restoreSession).mockResolvedValue(null);
  });

  it("shows a stranger what they were invited to and a form to set a password", async () => {
    renderAt();

    expect(await screen.findByText(/Join NextWebSpark Search/)).toBeInTheDocument();
    expect(screen.getByText(/Alok Kumar invited you as Member/)).toBeInTheDocument();
    // The invited address is shown but not theirs to change.
    expect(screen.getByDisplayValue("sara@nextwebspark.com")).toHaveAttribute("readonly");
    expect(screen.getByPlaceholderText("Re-enter your password")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /accept & join/i })).toBeInTheDocument();
  });

  it("creates the invited account in one step — name and password, no verification", async () => {
    vi.mocked(authApi.acceptInvitationSignup).mockResolvedValue({
      accessToken: "t",
      expiresIn: 900,
      user: {
        id: "u3",
        email: invitation.email,
        fullName: "Sara Al-Mansour",
        title: null,
        avatarUrl: null,
        emailVerified: true,
        onboardingHeld: false,
        pendingInvitation: null,
        workspace: {
          id: "w1",
          name: "NextWebSpark Search",
          slug: "nws",
          logoMark: null,
          emailDomain: "nextwebspark.com",
          roles: ["MEMBER"],
        },
      },
    });

    const user = userEvent.setup();
    renderAt();
    await screen.findByText(/Join NextWebSpark Search/);

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Sara Al-Mansour");
    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "secret123");
    await user.click(screen.getByRole("button", { name: /accept & join/i }));

    await waitFor(() =>
      expect(authApi.acceptInvitationSignup).toHaveBeenCalledWith(
        "abc123",
        "Sara Al-Mansour",
        "secret123",
      ),
    );
  });

  it("blocks mismatched passwords before calling the server", async () => {
    const user = userEvent.setup();
    renderAt();
    await screen.findByText(/Join NextWebSpark Search/);

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Sara Al-Mansour");
    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "different1");
    await user.click(screen.getByRole("button", { name: /accept & join/i }));

    expect(await screen.findByText("Those passwords don't match")).toBeInTheDocument();
    expect(authApi.acceptInvitationSignup).not.toHaveBeenCalled();
  });

  it("sends an already-registered address to log in instead of creating a duplicate", async () => {
    vi.mocked(authApi.acceptInvitationSignup).mockRejectedValue(
      new ApiRequestError({
        code: "EMAIL_ALREADY_REGISTERED",
        detail: "An account with this email already exists",
        status: 409,
        correlationId: "x",
      }),
    );

    const user = userEvent.setup();
    renderAt();
    await screen.findByText(/Join NextWebSpark Search/);

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Sara Al-Mansour");
    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "secret123");
    await user.click(screen.getByRole("button", { name: /accept & join/i }));

    const cta = await screen.findByRole("link", { name: /log in to accept/i });
    expect(cta).toHaveAttribute("href", "/login");
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
   * The fresh-tab case for someone who already had an account: no token in this tab, but the server knows
   * the invitation — `user.pendingInvitation` — and the page accepts from it, token-lessly.
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
    vi.mocked(authApi.acceptPendingInvitation).mockResolvedValue({ ...sara, pendingInvitation: null });

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
