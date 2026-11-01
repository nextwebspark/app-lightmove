import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import { SettingsProfilePage } from "./SettingsProfilePage";

vi.mock("../../auth/api/authApi");

// AuthProvider exchanges the refresh cookie for a token before it will ask who the user is, so a test
// that wants a signed-in user has to hand it one.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/** Settings → Profile: the caller's own display fields, and the two they may not touch. */
describe("SettingsProfilePage — your own profile", () => {
  const admin = {
    id: "u1",
    email: "alok@nextwebspark.com",
    fullName: "Alok Kumar",
    title: "Managing Partner",
    avatarUrl: null,
    photoUploaded: false,
    phone: null,
    linkedinUrl: null,
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
      mode: "COMPANY" as const,
      emailDomain: "nextwebspark.com",
      joinedAt: "2026-03-14T09:00:00Z",
      company: null,
      roles: ["ADMIN" as const],
    },
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <SettingsProfilePage />
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    // resetAllMocks strips implementations set in the mock factory, so they are set here or not at all.
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(authApi.updateProfile).mockResolvedValue(admin);
  });

  it("fills the form from the session and states the standing it cannot change", async () => {
    renderPage();

    expect(await screen.findByLabelText(/full name/i)).toHaveValue("Alok Kumar");
    expect(screen.getByLabelText(/^title$/i)).toHaveValue("Managing Partner");
    expect(screen.getByLabelText(/timezone/i)).toHaveValue("Asia/Dubai");
    expect(screen.getByLabelText(/language/i)).toHaveValue("en");

    // Neither the address nor the role is an input — both are the workspace's to decide.
    expect(screen.getByText("alok@nextwebspark.com")).toBeInTheDocument();
    expect(screen.getByText("Admin — set by workspace owner")).toBeInTheDocument();
    expect(screen.getByText("Admin · joined Mar 2026")).toBeInTheDocument();
  });

  it("saves the trimmed fields, refreshes the session, and says so", async () => {
    const user = userEvent.setup();
    renderPage();

    const name = await screen.findByLabelText(/full name/i);
    await user.clear(name);
    await user.type(name, "  Alok B Kumar  ");
    await user.selectOptions(screen.getByLabelText(/timezone/i), "Asia/Riyadh");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() =>
      expect(authApi.updateProfile).toHaveBeenCalledWith({
        fullName: "Alok B Kumar",
        title: "Managing Partner",
        timezone: "Asia/Riyadh",
        locale: "en",
        phone: "",
        linkedinUrl: "",
      }),
    );
    // The topbar reads the name off the session, so a save that does not reload leaves it stale.
    expect(authApi.me).toHaveBeenCalledTimes(2);
    expect(await screen.findByText("Profile saved")).toBeInTheDocument();
  });

  it("offers every region's zone by city, and the computer's own clock in one click", async () => {
    const computer = vi
      .spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions")
      .mockReturnValue({ timeZone: "Europe/London" } as Intl.ResolvedDateTimeFormatOptions);
    const user = userEvent.setup();
    renderPage();

    const timezone = await screen.findByLabelText(/timezone/i);
    expect(screen.getByRole("option", { name: /^New York \(GMT[−-]\d+\)$/ })).toHaveValue("America/New_York");
    expect(screen.getByRole("option", { name: "UTC" })).toBeInTheDocument();
    expect(screen.getByText("Sequences, scheduled emails and your daily digest follow this clock")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Use London" }));
    expect(timezone).toHaveValue("Europe/London");
    expect(screen.queryByRole("button", { name: "Use London" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() =>
      expect(authApi.updateProfile).toHaveBeenCalledWith(expect.objectContaining({ timezone: "Europe/London" })),
    );
    computer.mockRestore();
  });

  it("sends an emptied title as null, not as an empty string", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.clear(await screen.findByLabelText(/^title$/i));
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() =>
      expect(authApi.updateProfile).toHaveBeenCalledWith(expect.objectContaining({ title: null })),
    );
  });

  it("refuses a nameless profile without asking the server", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.clear(await screen.findByLabelText(/full name/i));
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByText("Enter your full name")).toBeInTheDocument();
    expect(authApi.updateProfile).not.toHaveBeenCalled();
  });

  it("shows a server field error under the field it names", async () => {
    vi.mocked(authApi.updateProfile).mockRejectedValue(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "One or more fields are invalid",
        status: 400,
        correlationId: "c1",
        fieldErrors: { timezone: "Pick a timezone from the list" },
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /save changes/i }));

    expect(await screen.findByText("Pick a timezone from the list")).toBeInTheDocument();
  });
});
