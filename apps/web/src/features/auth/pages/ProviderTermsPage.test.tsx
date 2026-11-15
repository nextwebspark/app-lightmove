import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { aUser } from "../../../test/fixtures/user";
import { AuthProvider } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { ProviderTermsPage } from "./ProviderTermsPage";

vi.mock("../api/authApi");

const navigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

/**
 * The terms step of a first Google or LinkedIn sign-in (#928): the provider's details are shown, nothing is created
 * until the box is ticked, and a hold that lapsed says so rather than offering a button that cannot work.
 */
describe("ProviderTermsPage", () => {
  const renderPage = () =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <AuthProvider>
            <ProviderTermsPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
    vi.mocked(authApi.pendingRegistration).mockResolvedValue({
      provider: "google",
      email: "yara@meridian.example",
      fullName: "Yara Haddad",
    });
  });

  it("shows what the provider told us, and creates nothing until the box is ticked", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Create your account with Google")).toBeInTheDocument();
    expect(screen.getByText("Yara Haddad")).toBeInTheDocument();
    expect(screen.getByText("yara@meridian.example")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Tick the box to accept the Terms and Privacy Policy.")).toBeInTheDocument();
    expect(authApi.completeRegistration).not.toHaveBeenCalled();
  });

  it("creates the account with the tick and lands where a new account belongs", async () => {
    const created = aUser({ emailVerified: true, workspace: null });
    vi.mocked(authApi.completeRegistration).mockResolvedValue({ accessToken: "t", expiresIn: 900, user: created });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("checkbox", { name: /I agree to the Terms/ }));
    await user.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(authApi.completeRegistration).toHaveBeenCalledWith(true));
    expect(navigate).toHaveBeenCalledWith("/signup/workspace", { replace: true });
  });

  it("says a lapsed hold has expired, and offers the way back rather than the button", async () => {
    vi.mocked(authApi.pendingRegistration).mockRejectedValue(
      new ApiRequestError({ code: "REGISTRATION_EXPIRED", detail: "gone", status: 410, correlationId: "x" }),
    );
    renderPage();

    expect(await screen.findByText("This sign-up has expired")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to sign up" })).toHaveAttribute("href", "/signup");
    expect(screen.queryByRole("button", { name: "Create account" })).not.toBeInTheDocument();
  });

  it("treats a failed read as a connection problem, not an expiry, and recovers on a retry", async () => {
    let online = false;
    vi.mocked(authApi.pendingRegistration).mockImplementation(async () => {
      if (!online) throw new TypeError("Failed to fetch");
      return { provider: "google", email: "yara@meridian.example", fullName: "Yara Haddad" };
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText(/Could not reach Uncava/)).toBeInTheDocument();
    expect(screen.queryByText("This sign-up has expired")).not.toBeInTheDocument();

    online = true;
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("button", { name: "Create account" })).toBeInTheDocument();
  });
});
