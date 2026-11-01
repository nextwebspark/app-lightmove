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

// jsdom has no canvas or layout: the cropper reports a framed square at once, and drawing it is a stub.
vi.mock("react-easy-crop", async () => {
  const { useEffect } = await import("react");
  return {
    default: ({ onCropComplete }: { onCropComplete: (area: unknown, pixels: unknown) => void }) => {
      // Once, as the real one reports on load: the page's handler is a new function every render.
      // eslint-disable-next-line react-hooks/exhaustive-deps
      useEffect(() => onCropComplete({}, { x: 0, y: 0, width: 400, height: 400 }), []);
      return <div data-testid="cropper" />;
    },
  };
});
vi.mock("../lib/croppedPhoto", () => ({ croppedPhoto: vi.fn() }));
const { croppedPhoto } = await import("../lib/croppedPhoto");

/** Settings → Profile: the caller's own display fields, and the two they may not touch. */
describe("SettingsProfilePage — your own profile", () => {
  const admin = {
    id: "u1",
    email: "alok@nextwebspark.com",
    fullName: "Alok Kumar",
    title: "Managing Partner",
    avatarUrl: null,
    avatarEditable: true,
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
    vi.mocked(croppedPhoto).mockResolvedValue(new Blob(["jpeg"], { type: "image/jpeg" }));
    URL.createObjectURL = vi.fn(() => "blob:picked");
    URL.revokeObjectURL = vi.fn();
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

  describe("the photo", () => {
    const picked = new File(["png"], "me.png", { type: "image/png" });

    it("frames a picked picture and uploads the square, then refreshes the session", async () => {
      vi.mocked(authApi.uploadAvatar).mockResolvedValue({ ...admin, avatarUrl: "/api/v1/avatars/abc" });
      const user = userEvent.setup();
      renderPage();

      await user.upload(await screen.findByLabelText("Choose a profile photo"), picked);
      expect(await screen.findByRole("dialog", { name: "Profile photo" })).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Save photo" }));

      await waitFor(() => expect(authApi.uploadAvatar).toHaveBeenCalledWith(expect.any(Blob)));
      expect(croppedPhoto).toHaveBeenCalledWith("blob:picked", { x: 0, y: 0, width: 400, height: 400 });
      expect(await screen.findByText("Profile photo saved")).toBeInTheDocument();
      await waitFor(() => expect(authApi.me).toHaveBeenCalledTimes(2));
      expect(screen.queryByRole("dialog", { name: "Profile photo" })).not.toBeInTheDocument();
      expect(authApi.uploadAvatar).toHaveBeenCalledTimes(1);
    });

    it("says a picture it could not draw is the browser's, and a failed send is the connection's", async () => {
      vi.mocked(croppedPhoto).mockRejectedValueOnce(new Error("decode"));
      vi.mocked(authApi.uploadAvatar).mockRejectedValueOnce(new TypeError("Failed to fetch"));
      const user = userEvent.setup();
      renderPage();

      await user.upload(await screen.findByLabelText("Choose a profile photo"), picked);
      await user.click(await screen.findByRole("button", { name: "Save photo" }));
      expect(await screen.findByRole("alert")).toHaveTextContent("This browser can't open that image");
      expect(authApi.uploadAvatar).not.toHaveBeenCalled();

      await user.click(screen.getByRole("button", { name: "Save photo" }));
      expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach Uncava");
      expect(screen.getByRole("dialog", { name: "Profile photo" })).toBeInTheDocument();
    });

    it("keeps the dialog open with the server's reason when it refuses the picture", async () => {
      vi.mocked(authApi.uploadAvatar).mockRejectedValue(
        new ApiRequestError({
          code: "VALIDATION_FAILED",
          detail: "One or more fields are invalid",
          status: 400,
          correlationId: "c1",
          fieldErrors: { file: "Use a JPG or PNG image" },
        }),
      );
      const user = userEvent.setup();
      renderPage();

      await user.upload(await screen.findByLabelText("Choose a profile photo"), picked);
      await user.click(await screen.findByRole("button", { name: "Save photo" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Use a JPG or PNG image");
      expect(screen.getByRole("dialog", { name: "Profile photo" })).toBeInTheDocument();
    });

    it("offers Remove photo only while there is one, and removes it", async () => {
      vi.mocked(authApi.me).mockResolvedValue({ ...admin, avatarUrl: "/api/v1/avatars/abc" });
      vi.mocked(authApi.removeAvatar).mockResolvedValue(admin);
      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole("button", { name: "Remove photo" }));

      await waitFor(() => expect(authApi.removeAvatar).toHaveBeenCalled());
      expect(await screen.findByText("Profile photo removed")).toBeInTheDocument();
    });

    it("draws no Remove photo for initials", async () => {
      renderPage();

      expect(await screen.findByRole("button", { name: "Change photo" })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Remove photo" })).not.toBeInTheDocument();
    });

    it("offers no change where the organisation's sign-in supplies the photo", async () => {
      vi.mocked(authApi.me).mockResolvedValue({ ...admin, avatarEditable: false, avatarUrl: "/api/v1/avatars/abc" });
      renderPage();

      expect(await screen.findByText("Your photo comes from your organisation's sign-in")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Change photo" })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Remove photo" })).not.toBeInTheDocument();
    });
  });
});
