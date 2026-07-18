import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import { SettingsMembersPage } from "./SettingsMembersPage";

vi.mock("../../auth/api/authApi");
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn(),
  invitations: vi.fn(),
  changeMemberRoles: vi.fn(),
  removeMember: vi.fn(),
}));

// AuthProvider exchanges the refresh cookie for a token before it will ask who the user is, so a test
// that wants a signed-in admin has to hand it one.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/** Membership is invitation-only, so this screen is the roster and its role grants — no queue. */
describe("SettingsMembersPage — the roster", () => {
  const admin = {
    id: "u1",
    email: "alok@nextwebspark.com",
    fullName: "Alok Kumar",
    title: null,
    avatarUrl: null,
    emailVerified: true,
    onboardingHeld: false,
    pendingInvitation: null,
    workspace: {
      id: "w1",
      name: "NextWebSpark Search",
      slug: "nextwebspark-search",
      logoMark: "N",
      emailDomain: "nextwebspark.com",
      roles: ["ADMIN" as const],
    },
  };

  const sara = {
    memberId: "m1",
    userId: "u2",
    fullName: "Sara Al-Mansour",
    email: "sara@nextwebspark.com",
    title: null,
    roles: ["MEMBER" as const],
    joinedAt: "2026-07-13T10:00:00Z",
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <SettingsMembersPage />
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
    vi.mocked(workspaceApi.members).mockResolvedValue([sara]);
    vi.mocked(workspaceApi.invitations).mockResolvedValue([]);
    vi.mocked(workspaceApi.changeMemberRoles).mockResolvedValue({ ...sara, roles: ["ADMIN"] });
  });

  it("shows the member with the roles they hold", async () => {
    renderPage();

    expect(await screen.findByText("Sara Al-Mansour")).toBeInTheDocument();
    expect(screen.getByLabelText("Role for Sara Al-Mansour")).toHaveValue("MEMBER");
  });

  /** The API takes the full set the member holds afterwards — picking Admin sends exactly that set. */
  it("sends the picked role as a replace-set", async () => {
    const user = userEvent.setup();
    renderPage();

    const picker = await screen.findByLabelText("Role for Sara Al-Mansour");
    await user.selectOptions(picker, "ADMIN");

    await waitFor(() =>
      expect(workspaceApi.changeMemberRoles).toHaveBeenCalledWith("m1", ["ADMIN"]),
    );
  });
});
