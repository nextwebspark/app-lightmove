import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { ForgotPasswordPage } from "./ForgotPasswordPage";

vi.mock("../api/authApi");

/**
 * The page's one promise is that the sent-state reads identically whether the address exists or not —
 * the server always answers 202, and the UI must not add an oracle on top of it. The rest is ordinary
 * form behaviour: bad input stays client-side, rate limiting is said out loud.
 */
describe("ForgotPasswordPage", () => {
  const renderPage = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <ForgotPasswordPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.mocked(authApi.requestPasswordReset).mockReset();
    // AuthProvider tries to restore a session on mount. There isn't one.
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
  });

  it("prefills the email carried over from the login form", () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter
          initialEntries={[{ pathname: "/forgot-password", state: { email: "alok@nextwebspark.com" } }]}
        >
          <AuthProvider>
            <ForgotPasswordPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getByPlaceholderText("you@firm.com")).toHaveValue("alok@nextwebspark.com");
  });

  it("starts blank when the login form had nothing typed", () => {
    renderPage();

    expect(screen.getByPlaceholderText("you@firm.com")).toHaveValue("");
  });

  it("rejects a malformed email without calling the server", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("you@firm.com"), "not-an-email");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(await screen.findByText("That doesn't look like a valid email")).toBeInTheDocument();
    expect(authApi.requestPasswordReset).not.toHaveBeenCalled();
  });

  it("shows the enumeration-safe sent state after submitting", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.requestPasswordReset).mockResolvedValue();

    renderPage();

    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@nextwebspark.com");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    await waitFor(() =>
      expect(authApi.requestPasswordReset).toHaveBeenCalledWith("alok@nextwebspark.com"),
    );
    // "If an account exists" — never "we sent it", which would confirm the account.
    expect(await screen.findByText(/if an account exists for/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /send reset link/i })).not.toBeInTheDocument();
  });

  it("keeps the form up and says so when rate limited", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.requestPasswordReset).mockRejectedValue(
      new ApiRequestError({
        code: "RATE_LIMITED",
        detail: "Too many requests",
        status: 429,
        correlationId: "abc",
      }),
    );

    renderPage();

    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@nextwebspark.com");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(await screen.findByText("Too many requests — slow down a little.")).toBeInTheDocument();
    // Still on the form — the user can wait and retry without starting over.
    expect(screen.getByRole("button", { name: /send reset link/i })).toBeInTheDocument();
  });
});
