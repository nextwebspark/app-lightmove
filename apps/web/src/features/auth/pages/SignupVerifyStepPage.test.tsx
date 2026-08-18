import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import type { User } from "../api/types";
import { SignupVerifyStepPage } from "./SignupVerifyStepPage";

vi.mock("../api/authApi");
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const navigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

const userAt = (emailVerified: boolean): User => ({
  id: "u1",
  email: "alok@nextwebspark.com",
  fullName: "Alok Kumar",
  title: null,
  avatarUrl: null,
  emailVerified,
  timezone: "Asia/Dubai",
  locale: "en",
  pendingInvitation: null,
  workspace: null,
});

/**
 * The gate. Nothing past this step exists until the emailed link is clicked, and the link is normally
 * clicked in a different browser — so this page has to notice that happening elsewhere.
 */
describe("SignupVerifyStepPage", () => {
  const renderPage = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <SignupVerifyStepPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    navigate.mockReset();
    // After resetAllMocks the factory's implementation is gone, so the provider would boot with no
    // session and render a page with no user on it.
    vi.mocked(restoreSession).mockResolvedValue("token");
  });

  it("names the address the link went to", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userAt(false));

    renderPage();

    expect(await screen.findByText("alok@nextwebspark.com")).toBeInTheDocument();
  });

  it("moves on to the organisation step once the link has been clicked", async () => {
    vi.mocked(authApi.me)
      .mockResolvedValueOnce(userAt(false))
      .mockResolvedValue(userAt(true));

    renderPage();
    await screen.findByText("alok@nextwebspark.com");

    await userEvent.click(screen.getByRole("button", { name: /i've confirmed it/i }));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true }),
    );
  });

  /**
   * The button is the fallback, not the mechanism. Returning to this tab after clicking the link in
   * the mail client's browser has to be enough on its own — otherwise the user sits on a screen
   * telling them to do a thing they have already done.
   */
  it("advances on window focus, without the button being touched", async () => {
    vi.mocked(authApi.me)
      .mockResolvedValueOnce(userAt(false))
      .mockResolvedValue(userAt(true));

    renderPage();
    await screen.findByText("alok@nextwebspark.com");

    window.dispatchEvent(new Event("focus"));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true }),
    );
  });

  it("resends the link to the same address", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userAt(false));
    vi.mocked(authApi.resendVerification).mockResolvedValue(undefined);

    renderPage();
    await screen.findByText("alok@nextwebspark.com");

    await userEvent.click(screen.getByRole("button", { name: /resend the link/i }));

    await waitFor(() =>
      expect(authApi.resendVerification).toHaveBeenCalledWith("alok@nextwebspark.com"),
    );
  });
});
