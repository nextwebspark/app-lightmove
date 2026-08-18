import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { ActiveSession, User } from "../../auth/api/types";
import { SettingsSecurityPage } from "./SettingsSecurityPage";

vi.mock("../../auth/api/authApi");

// AuthProvider exchanges the refresh cookie for a token before it will ask who the user is, so a test
// that wants a signed-in user has to hand it one.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const HOUR_AGO = new Date(Date.now() - 3_600_000).toISOString();
const NOW = new Date().toISOString();

/** Settings → Security: the caller's own password and the devices signed in as them. */
describe("SettingsSecurityPage", () => {
  const member: User = {
    id: "u1",
    email: "alok@nextwebspark.com",
    fullName: "Alok Kumar",
    title: "Managing Partner",
    avatarUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    locale: "en",
    pendingInvitation: null,
    workspace: {
      id: "w1",
      name: "NextWebSpark Search",
      slug: "nextwebspark-search",
      logoMark: "N",
      emailDomain: "nextwebspark.com",
      joinedAt: "2026-03-14T09:00:00Z",
      roles: ["MEMBER"],
    },
  };

  const thisDevice: ActiveSession = {
    id: "s1",
    device: "macOS — Safari",
    deviceKind: "DESKTOP",
    ipAddress: "102.44.18.7",
    lastActiveAt: NOW,
    current: true,
  };

  const thePhone: ActiveSession = {
    id: "s2",
    device: "iPhone — Safari",
    deviceKind: "MOBILE",
    ipAddress: "88.201.4.19",
    lastActiveAt: HOUR_AGO,
    current: false,
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <SettingsSecurityPage />
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    // resetAllMocks strips implementations set in the mock factory, so they are set here or not at all.
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(member);
    vi.mocked(authApi.listSessions).mockResolvedValue([thisDevice, thePhone]);
  });

  it("lists every signed-in device and marks the one being used", async () => {
    renderPage();

    expect(await screen.findByText("macOS — Safari")).toBeInTheDocument();
    expect(screen.getByText("This device")).toBeInTheDocument();
    expect(screen.getByText(/102\.44\.18\.7 · active now/)).toBeInTheDocument();

    // The IP is the point of the line: an address the owner does not recognise is the signal.
    expect(screen.getByText(/88\.201\.4\.19 · 1 hour ago/)).toBeInTheDocument();

    // Only the other device is revocable — ending your own session is signing out.
    expect(screen.getAllByRole("button", { name: /revoke/i })).toHaveLength(1);
  });

  it("revokes one session and refreshes the list", async () => {
    vi.mocked(authApi.revokeSession).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /revoke/i }));

    await waitFor(() => expect(authApi.revokeSession).toHaveBeenCalledWith("s2"));
    expect(await screen.findByText("Session signed out")).toBeInTheDocument();
    expect(authApi.listSessions).toHaveBeenCalledTimes(2);
  });

  it("signs out all others and says how many went", async () => {
    vi.mocked(authApi.revokeOtherSessions).mockResolvedValue({ revoked: 3 });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /sign out all others/i }));

    expect(await screen.findByText("3 other sessions signed out")).toBeInTheDocument();
  });

  it("offers no bulk sign-out when there is nothing else signed in", async () => {
    vi.mocked(authApi.listSessions).mockResolvedValue([thisDevice]);
    renderPage();

    expect(await screen.findByText("This device")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /sign out all others/i })).not.toBeInTheDocument();
  });

  it("reports a refused read as an error, never as an empty list", async () => {
    vi.mocked(authApi.listSessions).mockRejectedValue(
      new ApiRequestError({
        code: "REFRESH_TOKEN_INVALID",
        detail: "Your session has ended",
        status: 401,
        correlationId: "c1",
      }),
    );
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(/could not be loaded/i);
    expect(screen.queryByText("This device")).not.toBeInTheDocument();
  });

  it("changes the password and refreshes the sessions the change just revoked", async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue({
      accessToken: "fresh",
      expiresIn: 900,
      user: member,
    });
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText(/current password/i), "oldpassword1");
    await user.type(screen.getByLabelText(/^new password$/i), "brandnew42");
    await user.type(screen.getByLabelText(/confirm new password/i), "brandnew42");
    await user.click(screen.getByRole("button", { name: /update password/i }));

    await waitFor(() =>
      expect(authApi.changePassword).toHaveBeenCalledWith({
        currentPassword: "oldpassword1",
        newPassword: "brandnew42",
      }),
    );
    expect(await screen.findByText("Password updated")).toBeInTheDocument();
    // Every other session died with the old password; a list that still showed them would be lying.
    expect(authApi.listSessions).toHaveBeenCalledTimes(2);
  });

  it("shows a wrong current password under the field that caused it", async () => {
    vi.mocked(authApi.changePassword).mockRejectedValue(
      new ApiRequestError({
        code: "CURRENT_PASSWORD_INVALID",
        detail: "That is not your current password",
        status: 400,
        correlationId: "c1",
        fieldErrors: { currentPassword: "That is not your current password" },
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText(/current password/i), "wrongpassword1");
    await user.type(screen.getByLabelText(/^new password$/i), "brandnew42");
    await user.type(screen.getByLabelText(/confirm new password/i), "brandnew42");
    await user.click(screen.getByRole("button", { name: /update password/i }));

    expect(await screen.findByText("That is not your current password")).toBeInTheDocument();
  });

  it("refuses a mismatched confirmation without asking the server", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText(/current password/i), "oldpassword1");
    await user.type(screen.getByLabelText(/^new password$/i), "brandnew42");
    await user.type(screen.getByLabelText(/confirm new password/i), "brandnew43");
    await user.click(screen.getByRole("button", { name: /update password/i }));

    expect(await screen.findByText("Those passwords don't match")).toBeInTheDocument();
    expect(authApi.changePassword).not.toHaveBeenCalled();
  });

  it("offers the reset link instead of a form to an account that signs in with a provider", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...member, hasPassword: false });
    renderPage();

    expect(await screen.findByRole("link", { name: /set-password link/i })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
    expect(screen.queryByLabelText(/current password/i)).not.toBeInTheDocument();
  });

  it("shows two-factor authentication as present but not yet available", async () => {
    renderPage();

    const toggle = await screen.findByRole("switch", { name: /two-factor authentication/i });
    expect(toggle).toBeDisabled();
    expect(toggle).toHaveAttribute("aria-checked", "false");
    expect(screen.getByText("Not available yet")).toBeInTheDocument();
  });
});
