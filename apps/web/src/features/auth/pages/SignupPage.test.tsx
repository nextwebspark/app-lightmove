import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../AuthProvider";
import { SignupPage } from "./SignupPage";
import * as authApi from "../api/authApi";
import { ApiRequestError } from "../../../lib/apiClient";

vi.mock("../api/authApi");

const navigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

/**
 * The signup form's job is to stop bad input reaching the server, and to say clearly what the server
 * said when it rejects something anyway. Both halves are worth a test — the validation rules are the
 * product's rules, and the error mapping is where a rejected signup becomes a dead end or a fixable one.
 */
describe("SignupPage", () => {
  // AuthProvider drops the query cache on every identity change, so it needs a client — the same one
  // the real app gives it. Signing in must not leave the previous user's server state behind.
  const renderPage = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <SignupPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    navigate.mockReset();
    // AuthProvider tries to restore a session on mount. There isn't one.
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
  });

  it("rejects a password with no number, in the mockup's own words", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Alok Kumar");
    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@nextwebspark.com");
    await user.type(screen.getByPlaceholderText("8+ characters"), "password"); // 8 chars, no digit
    await user.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByText("Include at least one number")).toBeInTheDocument();
    expect(authApi.signup).not.toHaveBeenCalled();
  });

  it("rejects a password under 8 characters", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Alok Kumar");
    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@nextwebspark.com");
    await user.type(screen.getByPlaceholderText("8+ characters"), "pass1");
    await user.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByText("Use at least 8 characters")).toBeInTheDocument();
    expect(authApi.signup).not.toHaveBeenCalled();
  });

  it("submits a valid form and moves on to step 2", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.signup).mockResolvedValue({
      accessToken: "t",
      expiresIn: 900,
      user: {
        id: "u1",
        email: "alok@nextwebspark.com",
        fullName: "Alok Kumar",
        title: null,
        avatarUrl: null,
        emailVerified: false,
        onboardingHeld: false,
        pendingInvitation: null,
        workspace: null,
      },
    });

    renderPage();

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Alok Kumar");
    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@nextwebspark.com");
    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.click(screen.getByRole("button", { name: /continue/i }));

    await waitFor(() => expect(authApi.signup).toHaveBeenCalledOnce());
    expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true });
  });

  /**
   * A Gmail address is rejected by the *server*, and the message has to land on the email field rather
   * than in a banner at the top. Put it in a banner and the user stares at a form with nothing visibly
   * wrong with it.
   */
  it("puts a rejected work email on the email field", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.signup).mockRejectedValue(
      new ApiRequestError({
        code: "EMAIL_NOT_WORK_ADDRESS",
        detail: "Please sign up with your work email.",
        status: 400,
        correlationId: "abc",
      }),
    );

    renderPage();

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Alok Kumar");
    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@gmail.com");
    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByText("Please sign up with your work email.")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("you@firm.com")).toHaveAttribute("aria-invalid", "true");
    expect(navigate).not.toHaveBeenCalled();
  });

  /**
   * The one email failure with a way forward. The CTA carries the typed address to prefill login —
   * and nothing else: which workspace the account belongs to is never revealed pre-auth.
   */
  it("offers 'log in instead' when the email already has an account", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.signup).mockRejectedValue(
      new ApiRequestError({
        code: "EMAIL_ALREADY_REGISTERED",
        detail: "An account with this email already exists",
        status: 409,
        correlationId: "abc",
      }),
    );

    renderPage();

    await user.type(screen.getByPlaceholderText("Yara Haddad"), "Alok Kumar");
    await user.type(screen.getByPlaceholderText("you@firm.com"), "alok@nextwebspark.com");
    await user.type(screen.getByPlaceholderText("8+ characters"), "secret123");
    await user.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByText("An account with this email already exists")).toBeInTheDocument();
    const cta = screen.getByRole("link", { name: /log in instead/i });
    expect(cta).toHaveAttribute("href", "/login");
  });
});
