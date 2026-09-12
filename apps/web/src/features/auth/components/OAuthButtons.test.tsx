import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderBare, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OAuthButtons } from "./OAuthButtons";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import * as oauthPopup from "../oauthPopup";

vi.mock("../api/authApi");

// AuthProvider would otherwise try to exchange a refresh cookie on mount.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn().mockResolvedValue(null),
  setAccessToken: vi.fn(),
}));

/** A fresh client per test, so one test's cached provider list cannot answer the next one's. */
const render = (onError: (message: string | null) => void = vi.fn()) => {
  const client = new QueryClient();
  return {
    client,
    ...renderBare(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AuthProvider>
            <OAuthButtons onError={onError} />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
};

/**
 * The point of these buttons is that the server decides which exist.
 *
 * Adding an identity provider is meant to be a configuration change on the API and nothing else, so
 * the case that matters most is an id this file has never heard of: it must still produce a working
 * button rather than nothing at all.
 */
describe("OAuthButtons", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("offers one button per configured provider", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["google", "linkedin"] });

    render();

    expect(await screen.findByRole("button", { name: /Continue with Google/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Continue with LinkedIn/ })).toBeInTheDocument();
  });

  it("still offers a provider it has no mark for", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["okta"] });

    render();

    expect(await screen.findByRole("button", { name: /Continue with Okta/ })).toBeInTheDocument();
  });

  it("labels a hyphenated registration id word by word", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["azure-ad"] });

    render();

    expect(await screen.findByRole("button", { name: /Continue with Azure Ad/ })).toBeInTheDocument();
  });

  it("renders nothing at all when no provider is configured", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: [] });

    const { client, container } = render();

    // The first render is empty regardless — the query has not settled and the list defaults to
    // none — so asserting before resolution would pass even if the resolved-empty path broke.
    await vi.waitFor(() =>
      expect(client.getQueryState(["auth", "providers"])?.status).toBe("success"),
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("opens the provider's consent screen in a popup rather than leaving the page", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["linkedin"] });
    const startOAuthSignIn = vi.spyOn(oauthPopup, "startOAuthSignIn").mockReturnValue(() => {});

    render();
    (await screen.findByRole("button", { name: /Continue with LinkedIn/ })).click();

    expect(startOAuthSignIn).toHaveBeenCalledWith("linkedin", expect.anything());
  });

  it("holds the other providers while one attempt is in flight", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["google", "linkedin"] });
    vi.spyOn(oauthPopup, "startOAuthSignIn").mockReturnValue(() => {});

    render();
    (await screen.findByRole("button", { name: /Continue with LinkedIn/ })).click();

    expect(await screen.findByRole("button", { name: /Continue with Google/ })).toBeDisabled();
  });

  /**
   * The whole point of the change: backing out at the provider is not a failure, so the screen must
   * be told to show nothing and the button must come back ready for another go.
   */
  it("says nothing and re-arms the button when the user cancels", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["google", "linkedin"] });
    const onError = vi.fn();
    let cancel = () => {};
    vi.spyOn(oauthPopup, "startOAuthSignIn").mockImplementation((_id, handlers) => {
      cancel = handlers.onCancel;
      return () => {};
    });

    render(onError);
    (await screen.findByRole("button", { name: /Continue with LinkedIn/ })).click();
    await vi.waitFor(() => expect(screen.getByRole("button", { name: /Google/ })).toBeDisabled());

    cancel();

    await vi.waitFor(() => expect(screen.getByRole("button", { name: /Google/ })).toBeEnabled());
    // Cleared on the way in, and never given a message to show.
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(null);
  });

  it("shows a real refusal, and re-arms the button", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["linkedin"] });
    const onError = vi.fn();
    let refuse = (_code: string) => {};
    vi.spyOn(oauthPopup, "startOAuthSignIn").mockImplementation((_id, handlers) => {
      refuse = handlers.onError;
      return () => {};
    });

    render(onError);
    (await screen.findByRole("button", { name: /Continue with LinkedIn/ })).click();

    refuse("EMAIL_NOT_WORK_ADDRESS");

    await vi.waitFor(() =>
      expect(onError).toHaveBeenLastCalledWith(
        "Please sign in with your work account. LightMove is for search firms.",
      ),
    );
    await vi.waitFor(() => expect(screen.getByRole("button", { name: /LinkedIn/ })).toBeEnabled());
  });
});
