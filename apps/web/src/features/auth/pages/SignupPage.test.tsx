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
  const renderPage = () =>
    render(
      <MemoryRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </MemoryRouter>,
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
});
