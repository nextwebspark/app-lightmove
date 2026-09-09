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
    hasPassword: true,
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
    window.history.replaceState(null, "", "/auth/callback");
    document.cookie = "lm_oauth_popup=; Path=/; Max-Age=0";
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

  it("forwards a refusal to the login screen, carrying the code it was given", async () => {
    window.history.replaceState(null, "", "/auth/callback?error=EMAIL_NOT_VERIFIED");

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/login?error=EMAIL_NOT_VERIFIED", { replace: true }),
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

  /**
   * Inside a popup this page establishes nothing. The opener is the tab that stays, so it is the tab
   * that gets the session — and the access token the server put in this URL is thrown away unread
   * rather than forwarded, so no credential ever crosses between the two windows.
   */
  describe("inside a sign-in popup", () => {
    const openAsPopup = () => {
      document.cookie = "lm_oauth_popup=handshake-1; Path=/";
    };

    it("reports success to the opener, closes, and adopts nothing itself", async () => {
      openAsPopup();
      window.location.hash = "#token=tok-123";
      const postMessage = vi.fn();
      vi.stubGlobal("opener", { postMessage });
      const close = vi.spyOn(window, "close").mockImplementation(() => {});

      renderPage();

      await waitFor(() =>
        expect(postMessage).toHaveBeenCalledWith(
          expect.objectContaining({ handshakeId: "handshake-1", outcome: { status: "success" } }),
          window.location.origin,
        ),
      );
      await waitFor(() => expect(close).toHaveBeenCalled());

      // The opener mints its own token from the refresh cookie; this window must not use, keep or
      // forward the one it was handed.
      expect(setAccessToken).not.toHaveBeenCalled();
      expect(authApi.me).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
      expect(postMessage.mock.calls[0][0]).not.toHaveProperty("outcome.token");
      expect(window.location.hash).toBe("");

      vi.unstubAllGlobals();
    });

    it("reports a cancellation as its own outcome rather than a failure", async () => {
      openAsPopup();
      window.history.replaceState(null, "", "/auth/callback?error=OAUTH_CANCELLED");
      const postMessage = vi.fn();
      vi.stubGlobal("opener", { postMessage });
      vi.spyOn(window, "close").mockImplementation(() => {});

      renderPage();

      await waitFor(() =>
        expect(postMessage).toHaveBeenCalledWith(
          expect.objectContaining({
            outcome: { status: "error", code: "OAUTH_CANCELLED" },
          }),
          window.location.origin,
        ),
      );
      expect(navigate).not.toHaveBeenCalled();

      vi.unstubAllGlobals();
    });

    it("consumes the handshake, so a later redirect sign-in is not mistaken for a popup", async () => {
      openAsPopup();
      window.location.hash = "#token=tok-123";
      vi.stubGlobal("opener", { postMessage: vi.fn() });
      vi.spyOn(window, "close").mockImplementation(() => {});

      renderPage();

      await waitFor(() => expect(document.cookie).not.toContain("handshake-1"));
      vi.unstubAllGlobals();
    });
  });
});
