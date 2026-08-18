import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../AuthProvider";
import { OAuthCallbackPage } from "./OAuthCallbackPage";
import * as authApi from "../api/authApi";
import { setAccessToken } from "../../../lib/apiClient";

vi.mock("../api/authApi");

// The real apiClient would try to exchange a refresh cookie on AuthProvider's mount; here the token
// arrives in the URL fragment instead, so the client is stubbed to observe what the page does with it.
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

/**
 * The page every OAuth sign-in lands on, carrying the access token in the URL fragment. What matters:
 * the token must move from the address bar into memory and nowhere else, and every failure must end
 * on the login screen with a code — this page has no UI of its own to explain anything.
 */
describe("OAuthCallbackPage", () => {
  const user = {
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

  const renderPage = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <OAuthCallbackPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    window.location.hash = "";
  });

  it("adopts the fragment token, scrubs it from the address bar, and routes the user home", async () => {
    window.location.hash = "#token=tok-123";
    vi.mocked(authApi.me).mockResolvedValue(user);

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true }),
    );
    expect(setAccessToken).toHaveBeenCalledWith("tok-123");
    // The token must not survive in the URL — nothing may screenshot, bookmark or share it.
    expect(window.location.hash).toBe("");
  });

  it("goes back to login with a code when the fragment carries no token", async () => {
    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/login?error=OAUTH_FAILED", { replace: true }),
    );
    expect(authApi.me).not.toHaveBeenCalled();
  });

  it("clears the adopted token and goes back to login when the session cannot be read", async () => {
    window.location.hash = "#token=tok-456";
    vi.mocked(authApi.me).mockRejectedValue(new Error("session unusable"));

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/login?error=OAUTH_FAILED", { replace: true }),
    );
    // A token that could not become a session must not linger in memory half-adopted.
    expect(setAccessToken).toHaveBeenLastCalledWith(null);
  });
});
