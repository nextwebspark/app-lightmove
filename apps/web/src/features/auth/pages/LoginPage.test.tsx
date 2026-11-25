import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../AuthProvider";
import { LoginPage } from "./LoginPage";
import * as authApi from "../api/authApi";

vi.mock("../api/authApi");

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn().mockResolvedValue(null),
  setAccessToken: vi.fn(),
}));

/**
 * How a refused OAuth sign-in reads on the screen it lands on.
 *
 * The case worth a test of its own is the one that used to be wrong: someone who backs out at the
 * provider's consent screen was told "sign-in did not complete, try again", which reads as a broken
 * button. A cancellation must leave the page silent.
 */
describe("LoginPage", () => {
  /** MemoryRouter never touches window.location, so the URL under test has to be read from the router. */
  const CurrentSearch = () => <output data-testid="search">{useLocation().search}</output>;

  const renderAt = (path: string) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[path]}>
          <AuthProvider>
            <LoginPage />
            <CurrentSearch />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.providers).mockResolvedValue({ providers: [] });
  });

  it("says nothing at all when the sign-in was cancelled", async () => {
    renderAt("/login?error=OAUTH_CANCELLED");

    await waitFor(() => expect(screen.getByRole("button", { name: "Continue" })).toBeInTheDocument());
    expect(screen.queryByText(/didn't complete/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("explains a refusal it has a sentence for", async () => {
    renderAt("/login?error=EMAIL_NOT_WORK_ADDRESS");

    expect(
      await screen.findByText(/Please sign in with your work account/i),
    ).toBeInTheDocument();
  });

  it("falls back to one plain sentence for a code it does not know", async () => {
    renderAt("/login?error=SOMETHING_NEW");

    expect(await screen.findByText(/Sign-in didn't complete/i)).toBeInTheDocument();
  });

  /** Left in place it survives a reload and resurrects a banner for an attempt that is long over. */
  it("strips the error out of the address bar once it has been said", async () => {
    renderAt("/login?error=EMAIL_NOT_WORK_ADDRESS");

    await screen.findByText(/Please sign in with your work account/i);

    await waitFor(() => expect(screen.getByTestId("search")).toHaveTextContent(""));
    // The message stays on screen; only the parameter that would replay it is gone.
    expect(screen.getByText(/Please sign in with your work account/i)).toBeInTheDocument();
  });

  describe("email first", () => {
    it("asks for the password only after the email, whatever the address", async () => {
      const user = userEvent.setup();
      renderAt("/login");

      expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
      await user.type(await screen.findByLabelText("Work email"), "yara@firm.example");
      await user.click(screen.getByRole("button", { name: "Continue" }));

      expect(await screen.findByLabelText("Password")).toBeInTheDocument();
      expect(screen.getByLabelText("Work email")).toHaveValue("yara@firm.example");
      expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
      expect(authApi.login).not.toHaveBeenCalled();
    });

    it("refuses to move on without a valid email", async () => {
      const user = userEvent.setup();
      renderAt("/login");

      await user.click(await screen.findByRole("button", { name: "Continue" }));

      expect(await screen.findByText("Enter your email")).toBeInTheDocument();
      expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    });

    it("goes back to the providers with the email kept", async () => {
      const user = userEvent.setup();
      renderAt("/login");

      await user.type(await screen.findByLabelText("Work email"), "yara@firm.example");
      await user.click(screen.getByRole("button", { name: "Continue" }));
      await user.click(await screen.findByRole("button", { name: "Sign in with Google or SSO" }));

      expect(await screen.findByRole("button", { name: "Continue" })).toBeInTheDocument();
      expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
      expect(screen.getByLabelText("Work email")).toHaveValue("yara@firm.example");
    });

    it("signs in with the email and password", async () => {
      vi.mocked(authApi.login).mockReturnValue(new Promise(() => {}));
      const user = userEvent.setup();
      renderAt("/login");

      await user.type(await screen.findByLabelText("Work email"), "yara@firm.example");
      await user.click(screen.getByRole("button", { name: "Continue" }));
      await user.type(await screen.findByLabelText("Password"), "secret123");
      await user.click(screen.getByRole("button", { name: "Sign in" }));

      await waitFor(() =>
        expect(authApi.login).toHaveBeenCalledWith({ email: "yara@firm.example", password: "secret123" }),
      );
    });

    it("says a refused password on the password step", async () => {
      vi.mocked(authApi.login).mockRejectedValue(
        new ApiRequestError({
          code: "INVALID_CREDENTIALS",
          detail: "Invalid email or password",
          status: 401,
          correlationId: "ref-1",
        }),
      );
      const user = userEvent.setup();
      renderAt("/login");

      await user.type(await screen.findByLabelText("Work email"), "yara@firm.example");
      await user.click(screen.getByRole("button", { name: "Continue" }));
      await user.type(await screen.findByLabelText("Password"), "wrong-one1");
      await user.click(screen.getByRole("button", { name: "Sign in" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password. Please try again.");
      expect(screen.getByLabelText("Password")).toBeInTheDocument();
    });

    it("shows what was typed when the eye is pressed", async () => {
      const user = userEvent.setup();
      renderAt("/login");

      await user.type(await screen.findByLabelText("Work email"), "yara@firm.example");
      await user.click(screen.getByRole("button", { name: "Continue" }));
      const password = await screen.findByLabelText("Password");
      expect(password).toHaveAttribute("type", "password");

      await user.click(screen.getByRole("button", { name: "Show what you typed" }));

      expect(password).toHaveAttribute("type", "text");
    });

    it("links the terms and privacy policy, and offers single sign-on", async () => {
      renderAt("/login");

      expect(await screen.findByRole("link", { name: "Terms of Service" })).toHaveAttribute(
        "href",
        "https://uncava.com/terms",
      );
      expect(screen.getByRole("link", { name: "Privacy Policy" })).toHaveAttribute("href", "https://uncava.com/privacy");
      expect(screen.getByRole("link", { name: "Continue with SSO" })).toHaveAttribute("href", "/login/sso");
    });
  });
});
