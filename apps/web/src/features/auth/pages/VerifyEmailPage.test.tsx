import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import type { AuthResponse, User } from "../api/types";
import { VerifyEmailPage } from "./VerifyEmailPage";

vi.mock("../api/authApi");
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn().mockResolvedValue(null),
  setAccessToken: vi.fn(),
}));

const navigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

const verifiedUser: User = {
  id: "u1",
  email: "alok@nextwebspark.com",
  fullName: "Alok Kumar",
  title: null,
  avatarUrl: null,
  emailVerified: true,
  timezone: "Asia/Dubai",
  locale: "en",
  pendingInvitation: null,
  workspace: null,
};

const session: AuthResponse = { accessToken: "t", expiresIn: 900, user: verifiedUser };

/**
 * Where the emailed link lands — usually in a browser that has never seen this app and holds no
 * session at all. That is the case worth testing: redeeming has to sign that browser in, or the user
 * proves their mailbox and is then asked to log in.
 */
describe("VerifyEmailPage", () => {
  const renderAt = (search: string) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[`/auth/verify${search}`]}>
          <AuthProvider>
            <VerifyEmailPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    navigate.mockReset();
  });

  it("signs in a browser with no session and continues to the organisation step", async () => {
    vi.mocked(authApi.verifyEmail).mockResolvedValue(session);
    vi.mocked(authApi.me).mockResolvedValue(verifiedUser);

    renderAt("?token=abc123");

    expect(await screen.findByText("Email verified")).toBeInTheDocument();
    expect(authApi.verifyEmail).toHaveBeenCalledWith("abc123");

    await userEvent.click(screen.getByRole("button", { name: /continue/i }));
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true }),
    );
  });

  /**
   * The tab that was waiting on this link polls, so it advances the moment the link is clicked and the
   * wizard is normally finished over there — organisation, invitations, into the app — while this card
   * is still open. Routing on the snapshot taken at redemption sent that user back to a create form
   * that then answers ALREADY_IN_WORKSPACE.
   */
  it("re-reads before routing, so a workspace created in the other tab is honoured", async () => {
    vi.mocked(authApi.verifyEmail).mockResolvedValue(session);
    vi.mocked(authApi.me).mockResolvedValue({
      ...verifiedUser,
      workspace: {
        id: "w1",
        name: "Meridian",
        slug: "meridian",
        logoMark: "M",
        emailDomain: "nextwebspark.com",
        joinedAt: null,
        roles: ["ADMIN"],
      },
    });

    renderAt("?token=abc123");
    await screen.findByText("Email verified");

    await userEvent.click(screen.getByRole("button", { name: /continue/i }));

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/", { replace: true }));
    expect(navigate).not.toHaveBeenCalledWith("/signup/workspace", { replace: true });
  });

  it("says the link is broken when it carries no token", async () => {
    renderAt("");

    expect(await screen.findByText("Verification failed")).toBeInTheDocument();
    expect(authApi.verifyEmail).not.toHaveBeenCalled();
  });

  it("reports a spent link", async () => {
    vi.mocked(authApi.verifyEmail).mockRejectedValue(
      new ApiRequestError({
        code: "TOKEN_INVALID",
        detail: "That link is no longer valid.",
        status: 400,
        correlationId: "abc",
      }),
    );

    renderAt("?token=spent");

    expect(await screen.findByText("Verification failed")).toBeInTheDocument();
    expect(await screen.findByText("That link is no longer valid.")).toBeInTheDocument();
  });
});
