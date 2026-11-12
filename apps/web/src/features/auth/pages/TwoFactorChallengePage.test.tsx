import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { aUser } from "../../../test/fixtures/user";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { LoginPage } from "./LoginPage";
import { TWO_FACTOR_SIGN_IN_PATH, TwoFactorChallengePage } from "./TwoFactorChallengePage";

vi.mock("../api/authApi");

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn().mockResolvedValue(null),
  setAccessToken: vi.fn(),
}));

/** The second step of a sign-in: a right password that still owes a code, and the code that completes it. */
describe("two-factor sign-in", () => {
  const CurrentPath = () => <output data-testid="path">{useLocation().pathname}</output>;

  const renderAt = (path: string, state?: unknown) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[{ pathname: path, state }]}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path={TWO_FACTOR_SIGN_IN_PATH} element={<TwoFactorChallengePage />} />
              <Route path="*" element={<p>Landed</p>} />
            </Routes>
            <CurrentPath />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.providers).mockResolvedValue({ providers: [] });
  });

  it("sends a right password that owes a code to the code step, signed in to nothing yet", async () => {
    vi.mocked(authApi.login).mockResolvedValue({ mfaRequired: true, expiresIn: 300 });
    const user = userEvent.setup();
    renderAt("/login");

    await user.type(await screen.findByLabelText(/work email/i), "priya@firm.example");
    await user.type(screen.getByPlaceholderText("••••••••"), "secret123");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByRole("heading", { name: /two-factor authentication/i })).toBeInTheDocument();
    expect(screen.getByTestId("path")).toHaveTextContent(TWO_FACTOR_SIGN_IN_PATH);
    expect(authApi.verifySecondFactor).not.toHaveBeenCalled();
  });

  it("completes the sign-in with the app's code and lands where the sign-in was headed", async () => {
    vi.mocked(authApi.verifySecondFactor).mockResolvedValue({
      accessToken: "token",
      expiresIn: 900,
      user: aUser(),
    });
    const user = userEvent.setup();
    renderAt(TWO_FACTOR_SIGN_IN_PATH, { from: "/candidates" });

    await user.type(await screen.findByLabelText("Code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify" }));

    await waitFor(() => expect(screen.getByTestId("path")).toHaveTextContent("/candidates"));
    expect(authApi.verifySecondFactor).toHaveBeenCalledWith("123456");
  });

  it("takes a recovery code in place of the app's", async () => {
    vi.mocked(authApi.verifySecondFactor).mockResolvedValue({
      accessToken: "token",
      expiresIn: 900,
      user: aUser(),
    });
    const user = userEvent.setup();
    renderAt(TWO_FACTOR_SIGN_IN_PATH);

    await user.click(await screen.findByRole("button", { name: /use a recovery code/i }));
    await user.type(screen.getByLabelText("Recovery code"), "abcd-efgh-ijkl-mnop");
    await user.click(screen.getByRole("button", { name: "Verify" }));

    await waitFor(() => expect(authApi.verifySecondFactor).toHaveBeenCalledWith("abcd-efgh-ijkl-mnop"));
  });

  it("refuses anything but six digits without asking the server", async () => {
    const user = userEvent.setup();
    renderAt(TWO_FACTOR_SIGN_IN_PATH);

    await user.type(await screen.findByLabelText("Code"), "12a456");
    await user.click(screen.getByRole("button", { name: "Verify" }));

    expect(await screen.findByText("Enter the 6-digit code from your authenticator app")).toBeInTheDocument();
    expect(authApi.verifySecondFactor).not.toHaveBeenCalled();
  });

  it("says a refused code plainly and stays on the step for another try", async () => {
    vi.mocked(authApi.verifySecondFactor).mockRejectedValue(
      new ApiRequestError({
        code: "MFA_CODE_INVALID",
        detail: "That code didn't work",
        status: 400,
        correlationId: "c1",
        fieldErrors: {},
      }),
    );
    const user = userEvent.setup();
    renderAt(TWO_FACTOR_SIGN_IN_PATH);

    await user.type(await screen.findByLabelText("Code"), "654321");
    await user.click(screen.getByRole("button", { name: "Verify" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/That code didn't work/);
    expect(screen.getByTestId("path")).toHaveTextContent(TWO_FACTOR_SIGN_IN_PATH);
    expect(screen.getByLabelText("Code")).toHaveValue("");
  });
});
