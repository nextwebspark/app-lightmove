import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../auth/AuthProvider";
import { WorkspacePage } from "./WorkspacePage";
import * as authApi from "../../auth/api/authApi";

vi.mock("../../auth/api/authApi");

// AuthProvider exchanges the refresh cookie for a token before it will ask who the user is, so a test
// that wants a signed-in admin has to hand it one.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * The approval queue is where an admin decides what someone may see, so the role is the whole point of
 * the screen.
 *
 * <p>It used to be hardcoded to RESEARCHER, under a comment claiming "the admin decides" — the admin
 * decided nothing, and the role the applicant asked for was read from the API and never shown.
 */
describe("WorkspacePage — the approval queue", () => {
  const admin = {
    id: "u1",
    email: "alok@nextwebspark.com",
    fullName: "Alok Kumar",
    title: null,
    avatarUrl: null,
    emailVerified: true,
    workspace: {
      id: "w1",
      name: "NextWebSpark Search",
      slug: "nextwebspark-search",
      logoMark: "N",
      emailDomain: "nextwebspark.com",
      role: "ADMIN" as const,
      status: "ACTIVE" as const,
    },
  };

  const applicant = {
    memberId: "m1",
    userId: "u2",
    fullName: "Sara Al-Mansour",
    email: "sara@nextwebspark.com",
    requestedRole: "CONSULTANT" as const,
    requestedAt: "2026-07-13T10:00:00Z",
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <WorkspacePage />
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    // resetAllMocks strips implementations set in the mock factory, so they are set here or not at all.
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(authApi.pendingMembers).mockResolvedValue([applicant]);
    vi.mocked(authApi.approveMember).mockResolvedValue(applicant);
  });

  it("shows what the applicant asked for, and defaults the picker to it", async () => {
    renderPage();

    expect(await screen.findByText(/asked for Consultant/)).toBeInTheDocument();
    expect(screen.getByLabelText("Role for Sara Al-Mansour")).toHaveValue("CONSULTANT");
  });

  /** The request is a suggestion. What the admin picks is the grant — and that is what must be sent. */
  it("sends the role the admin chose, not the one that was requested", async () => {
    const user = userEvent.setup();
    renderPage();

    const picker = await screen.findByLabelText("Role for Sara Al-Mansour");
    await user.selectOptions(picker, "RESEARCHER");
    await user.click(screen.getByRole("button", { name: /approve/i }));

    await waitFor(() =>
      expect(authApi.approveMember).toHaveBeenCalledWith("m1", "RESEARCHER"),
    );
  });

  /** Deciding about a person is not something to do twice because a click registered twice. */
  it("does not let a double-click approve the same person twice", async () => {
    const user = userEvent.setup();
    // Never settles, so the row stays in flight for the whole test.
    vi.mocked(authApi.approveMember).mockReturnValue(new Promise(() => {}));

    renderPage();

    const approve = await screen.findByRole("button", { name: /approve/i });
    await user.click(approve);
    await user.click(approve);

    expect(authApi.approveMember).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: /decline/i })).toBeDisabled();
  });
});
