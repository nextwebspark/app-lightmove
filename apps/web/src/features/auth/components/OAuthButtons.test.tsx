import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderBare, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OAuthButtons } from "./OAuthButtons";
import * as authApi from "../api/authApi";

vi.mock("../api/authApi");

/** A fresh client per test, so one test's cached provider list cannot answer the next one's. */
const render = () => {
  const client = new QueryClient();
  return {
    client,
    ...renderBare(
      <QueryClientProvider client={client}>
        <OAuthButtons />
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

  it("sends the browser to the provider's authorisation path, which is named by the id", async () => {
    vi.mocked(authApi.providers).mockResolvedValue({ providers: ["linkedin"] });
    // jsdom refuses an assignment to window.location.href, and the assertion is what the component
    // navigates to rather than that a navigation happened.
    const location = { href: "" } as Location;
    vi.spyOn(window, "location", "get").mockReturnValue(location);

    render();
    (await screen.findByRole("button", { name: /Continue with LinkedIn/ })).click();

    expect(location.href).toBe("/oauth2/authorization/linkedin");
  });
});
