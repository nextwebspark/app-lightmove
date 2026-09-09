import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
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
});
