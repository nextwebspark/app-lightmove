import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { restoreSession } from "../../../lib/apiClient";
import { aUser } from "../../../test/fixtures/user";
import { AuthProvider, useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { TermsUpdateSheet } from "./TermsUpdateSheet";

vi.mock("../api/authApi");
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
}));

/**
 * Login.dc.html `screen=updated-terms` (#928): changed terms cover every signed-in screen until they are accepted, the
 * tick naming the version shown; someone else's booking page is not the app, and is left alone.
 */
describe("TermsUpdateSheet", () => {
  const renderAt = (path: string) =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={[path]}>
          <AuthProvider>
            <SignedInAs />
            <TermsUpdateSheet />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  /** Settles the session restore, so a missing sheet is a decision and not a render that came too early. */
  function SignedInAs() {
    const { user } = useAuth();
    return <p>{user ? `Signed in as ${user.email}` : "Signed out"}</p>;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("access-token");
  });

  it("covers the app once the terms changed, and the tick sends the version it showed", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ termsUpdateRequired: true, termsVersion: "2026-12-01" }));
    vi.mocked(authApi.acceptTerms).mockResolvedValue(aUser({ termsUpdateRequired: false, termsVersion: "2026-12-01" }));
    const user = userEvent.setup();
    renderAt("/");

    expect(await screen.findByRole("dialog", { name: "We've updated our terms" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Accept and continue" }));
    expect(await screen.findByText("Tick the box to accept the Terms and Privacy Policy.")).toBeInTheDocument();
    expect(authApi.acceptTerms).not.toHaveBeenCalled();

    await user.click(screen.getByRole("checkbox", { name: /I agree to the Terms/ }));
    await user.click(screen.getByRole("button", { name: "Accept and continue" }));

    await waitFor(() => expect(authApi.acceptTerms).toHaveBeenCalledWith("2026-12-01"));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("asks nothing of someone whose accepted terms are current", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser());
    renderAt("/");

    expect(await screen.findByText("Signed in as alok@nextwebspark.com")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("leaves a page that is not the app, such as a booking link, uncovered", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ termsUpdateRequired: true }));
    renderAt("/book/yara");

    expect(await screen.findByText("Signed in as alok@nextwebspark.com")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
