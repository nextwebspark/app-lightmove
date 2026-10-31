import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { ActiveSession, MfaStatus, User } from "../../auth/api/types";
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

const TWO_FACTOR_OFF: MfaStatus = { offered: true, enabled: false, enabledAt: null, recoveryCodesLeft: 0 };

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
    platformActions: [],
    pendingInvitations: [],
  workspaces: [],
    workspace: {
      id: "w1",
      name: "NextWebSpark Search",
      slug: "nextwebspark-search",
      logoMark: "N",
      mode: "COMPANY",
      emailDomain: "nextwebspark.com",
      joinedAt: "2026-03-14T09:00:00Z",
      company: null,
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
    vi.mocked(authApi.mfaStatus).mockResolvedValue(TWO_FACTOR_OFF);
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

  it("shows two-factor authentication as present but not yet available where it is not offered", async () => {
    vi.mocked(authApi.mfaStatus).mockResolvedValue({ ...TWO_FACTOR_OFF, offered: false });
    renderPage();

    expect(await screen.findByText("Not available yet")).toBeInTheDocument();
    const toggle = screen.getByRole("switch", { name: /two-factor authentication/i });
    expect(toggle).toBeDisabled();
    expect(toggle).toHaveAttribute("aria-checked", "false");
  });

  describe("two-factor authentication", () => {
    const RECOVERY_CODES = ["AAAA-BBBB-CCCC-DDDD", "EEEE-FFFF-GGGG-HHHH"];
    const SETUP = {
      secret: "JBSWY3DPEHPK3PXP",
      otpauthUri: "otpauth://totp/Uncava:priya%40firm.example?secret=JBSWY3DPEHPK3PXP&issuer=Uncava",
    };

    const openEnableDialog = async (user: ReturnType<typeof userEvent.setup>) => {
      await waitFor(() => expect(screen.getByRole("switch", { name: /two-factor/i })).toBeEnabled());
      await user.click(screen.getByRole("switch", { name: /two-factor/i }));
      return screen.findByRole("dialog", { name: /turn on two-factor/i });
    };

    it("turns on with the app's code and the password, then shows the recovery codes once", async () => {
      vi.mocked(authApi.startMfaSetup).mockResolvedValue(SETUP);
      vi.mocked(authApi.enableMfa).mockResolvedValue({ recoveryCodes: RECOVERY_CODES });
      const user = userEvent.setup();
      renderPage();

      const dialog = await openEnableDialog(user);
      expect(await within(dialog).findByText("JBSWY3DPEHPK3PXP")).toBeInTheDocument();
      expect(within(dialog).getByTitle(/QR code/i)).toBeInTheDocument();

      await user.type(within(dialog).getByLabelText(/current password/i), "secret123");
      await user.type(within(dialog).getByLabelText(/code from your app/i), "123 456");
      await user.click(within(dialog).getByRole("button", { name: "Turn on" }));

      const codes = await screen.findByRole("list", { name: /recovery codes/i });
      expect(authApi.enableMfa).toHaveBeenCalledWith({ currentPassword: "secret123", code: "123 456" });
      expect(within(codes).getAllByRole("listitem").map((item) => item.textContent)).toEqual(RECOVERY_CODES);
      // Turning it on signed every other device out, so the list beside it is read again.
      await waitFor(() => expect(authApi.listSessions).toHaveBeenCalledTimes(2));

      await user.click(screen.getByRole("button", { name: /saved them/i }));
      expect(screen.queryByRole("list", { name: /recovery codes/i })).not.toBeInTheDocument();
    });

    it("refuses anything but six digits before asking the server", async () => {
      vi.mocked(authApi.startMfaSetup).mockResolvedValue(SETUP);
      const user = userEvent.setup();
      renderPage();

      const dialog = await openEnableDialog(user);
      await user.type(within(dialog).getByLabelText(/current password/i), "secret123");
      await user.type(within(dialog).getByLabelText(/code from your app/i), "12345");
      await user.click(within(dialog).getByRole("button", { name: "Turn on" }));

      expect(await within(dialog).findByText("Enter the 6-digit code from your authenticator app")).toBeInTheDocument();
      expect(authApi.enableMfa).not.toHaveBeenCalled();
    });

    it("shows a code the server refused under the code field", async () => {
      vi.mocked(authApi.startMfaSetup).mockResolvedValue(SETUP);
      vi.mocked(authApi.enableMfa).mockRejectedValue(
        new ApiRequestError({
          code: "MFA_CODE_INVALID",
          detail: "That code didn't work",
          status: 400,
          correlationId: "c2",
          fieldErrors: { code: "That code didn't work. Check your authenticator app and try again" },
        }),
      );
      const user = userEvent.setup();
      renderPage();

      const dialog = await openEnableDialog(user);
      await user.type(within(dialog).getByLabelText(/current password/i), "secret123");
      await user.type(within(dialog).getByLabelText(/code from your app/i), "654321");
      await user.click(within(dialog).getByRole("button", { name: "Turn on" }));

      expect(await within(dialog).findByText(/That code didn't work/)).toBeInTheDocument();
      expect(screen.queryByRole("list", { name: /recovery codes/i })).not.toBeInTheDocument();
    });

    it("says when it is on and how many recovery codes are left, and turns off with a recovery code", async () => {
      vi.mocked(authApi.mfaStatus).mockResolvedValue({
        offered: true,
        enabled: true,
        enabledAt: "2026-10-01T09:00:00Z",
        recoveryCodesLeft: 7,
      });
      vi.mocked(authApi.disableMfa).mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderPage();

      expect(await screen.findByText(/7 recovery codes left/)).toBeInTheDocument();
      const toggle = screen.getByRole("switch", { name: /two-factor/i });
      expect(toggle).toHaveAttribute("aria-checked", "true");

      await user.click(toggle);
      const dialog = await screen.findByRole("dialog", { name: /turn off two-factor/i });
      await user.type(within(dialog).getByLabelText(/current password/i), "secret123");
      await user.type(within(dialog).getByLabelText(/recovery code/i), "aaaa-bbbb-cccc-dddd");
      await user.click(within(dialog).getByRole("button", { name: "Turn off" }));

      await waitFor(() =>
        expect(authApi.disableMfa).toHaveBeenCalledWith({ currentPassword: "secret123", code: "aaaa-bbbb-cccc-dddd" }),
      );
      expect(await screen.findByText("Two-factor authentication is off")).toBeInTheDocument();
    });

    it("turns on for an account with no password with the app's code alone", async () => {
      vi.mocked(authApi.me).mockResolvedValue({ ...member, hasPassword: false });
      vi.mocked(authApi.startMfaSetup).mockResolvedValue(SETUP);
      vi.mocked(authApi.enableMfa).mockResolvedValue({ recoveryCodes: RECOVERY_CODES });
      const user = userEvent.setup();
      renderPage();

      const dialog = await openEnableDialog(user);
      expect(await within(dialog).findByText("JBSWY3DPEHPK3PXP")).toBeInTheDocument();
      expect(within(dialog).queryByLabelText(/current password/i)).not.toBeInTheDocument();

      await user.type(within(dialog).getByLabelText(/code from your app/i), "123456");
      await user.click(within(dialog).getByRole("button", { name: "Turn on" }));

      await screen.findByRole("list", { name: /recovery codes/i });
      expect(authApi.enableMfa).toHaveBeenCalledWith({ code: "123456" });
    });

    it("turns off for an account with no password with a code alone", async () => {
      vi.mocked(authApi.me).mockResolvedValue({ ...member, hasPassword: false });
      vi.mocked(authApi.mfaStatus).mockResolvedValue({
        offered: true,
        enabled: true,
        enabledAt: "2026-10-01T09:00:00Z",
        recoveryCodesLeft: 10,
      });
      vi.mocked(authApi.disableMfa).mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole("switch", { name: /two-factor/i }));
      const dialog = await screen.findByRole("dialog", { name: /turn off two-factor/i });
      expect(within(dialog).queryByLabelText(/current password/i)).not.toBeInTheDocument();
      await user.type(within(dialog).getByLabelText(/recovery code/i), "123456");
      await user.click(within(dialog).getByRole("button", { name: "Turn off" }));

      await waitFor(() => expect(authApi.disableMfa).toHaveBeenCalledWith({ code: "123456" }));
      expect(await screen.findByText("Two-factor authentication is off")).toBeInTheDocument();
    });
  });
});
