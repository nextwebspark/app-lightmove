import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import type { User } from "../api/types";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { ResetPasswordPage } from "./ResetPasswordPage";

vi.mock("../api/authApi");

const navigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

/**
 * The page redeems a single-use credential and then routes a freshly signed-in user, so what matters
 * is exactly that seam: nothing is spent on bad client input, success navigates like login does, and a
 * dead link is announced as dead rather than as a retryable form error.
 */
describe("ResetPasswordPage", () => {
  const renderAt = (path: string) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[path]}>
          <AuthProvider>
            <ResetPasswordPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  const sessionFor = (overrides: Partial<User>) => ({
    accessToken: "t",
    expiresIn: 900,
    user: {
      id: "u1",
      email: "alok@nextwebspark.com",
      fullName: "Alok Kumar",
      title: null,
      avatarUrl: null,
      emailVerified: true,
      onboardingHeld: false,
      pendingInvitation: null,
      workspace: null,
      ...overrides,
    },
  });

  beforeEach(() => {
    navigate.mockReset();
    vi.mocked(authApi.resetPassword).mockReset();
    // AuthProvider tries to restore a session on mount. There isn't one.
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
  });

  it("shows the invalid-link state when the URL has no token, and never calls the server", () => {
    renderAt("/auth/reset-password");

    expect(screen.getByText("This link is incomplete")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("8+ characters")).not.toBeInTheDocument();
    expect(authApi.resetPassword).not.toHaveBeenCalled();
  });

  it("blocks mismatched passwords before the token is spent", async () => {
    const user = userEvent.setup();
    renderAt("/auth/reset-password?token=abc");

    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "different1");
    await user.click(screen.getByRole("button", { name: /set password and sign in/i }));

    expect(await screen.findByText("Those passwords don't match")).toBeInTheDocument();
    expect(authApi.resetPassword).not.toHaveBeenCalled();
  });

  it("signs a workspace user straight into the app", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.resetPassword).mockResolvedValue(
      sessionFor({
        workspace: {
          id: "w1",
          name: "NextWebSpark",
          slug: "nextwebspark",
          logoMark: null,
          emailDomain: "nextwebspark.com",
          roles: ["ADMIN"],
        },
      }),
    );

    renderAt("/auth/reset-password?token=abc");

    await user.type(screen.getByPlaceholderText("8+ characters"), "brandnew42");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "brandnew42");
    await user.click(screen.getByRole("button", { name: /set password and sign in/i }));

    await waitFor(() => expect(authApi.resetPassword).toHaveBeenCalledWith("abc", "brandnew42"));
    expect(navigate).toHaveBeenCalledWith("/", { replace: true });
  });

  it("routes a user without a workspace into the wizard, like login does", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.resetPassword).mockResolvedValue(sessionFor({ workspace: null }));

    renderAt("/auth/reset-password?token=abc");

    await user.type(screen.getByPlaceholderText("8+ characters"), "brandnew42");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "brandnew42");
    await user.click(screen.getByRole("button", { name: /set password and sign in/i }));

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true }));
  });

  it("announces a spent or expired link and offers a fresh one", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.resetPassword).mockRejectedValue(
      new ApiRequestError({
        code: "TOKEN_EXPIRED",
        detail: "This link has expired",
        status: 400,
        correlationId: "abc",
      }),
    );

    renderAt("/auth/reset-password?token=abc");

    await user.type(screen.getByPlaceholderText("8+ characters"), "brandnew42");
    await user.type(screen.getByPlaceholderText("Re-enter your password"), "brandnew42");
    await user.click(screen.getByRole("button", { name: /set password and sign in/i }));

    expect(
      await screen.findByText("This link has expired or was already used"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /request a new link/i })).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });
});
