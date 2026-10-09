import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as projectsApi from "../../projects/api/projectsApi";
import * as workspaceApi from "../api/workspaceApi";
import type { User } from "../../auth/api/types";
import type { Member } from "../api/types";
import { TeamPage } from "./TeamPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/workspaceApi")>()),
  members: vi.fn(),
  invitations: vi.fn(),
  pendingInvitationCount: vi.fn(),
  changeMemberRoles: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const userWith = (roles: ("ADMIN" | "MEMBER")[]): User => ({
  id: "u1",
  email: "lead@firm.example",
  fullName: "A Lead",
  title: null,
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
    name: "Meridian",
    slug: "meridian",
    logoMark: "M",
    mode: "COMPANY" as const,
    emailDomain: "firm.example",
    joinedAt: null,
    company: null,
    roles,
  },
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthProvider>
          <ToastProvider>
            <TeamPage />
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

/** A refused roster must not be reported as "0 members" — a count the caller could not read. */
describe("TeamPage — a refused read", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
  });

  it("says the roster could not be loaded, and states no count", async () => {
    vi.mocked(workspaceApi.members).mockRejectedValue(new Error("403"));

    renderPage();

    expect(await screen.findByText("Couldn't load the roster")).toBeInTheDocument();
    expect(screen.queryByText(/0 members/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /invite/i })).not.toBeInTheDocument();
  });
});

const member = (overrides: Partial<Member>): Member => ({
  memberId: "m1",
  userId: "u1",
  fullName: "A Lead",
  email: "lead@firm.example",
  title: null,
  avatarUrl: null,
  roles: ["ADMIN"],
  joinedAt: "2026-01-01T00:00:00Z",
  ...overrides,
});

/** One roster for everyone: an admin manages people and invitations on it, a member reads it. */
describe("TeamPage — the one roster", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([
      member({}),
      member({
        memberId: "m2",
        userId: "u2",
        fullName: "Rania Haddad",
        email: "rania@firm.example",
        roles: ["MEMBER"],
      }),
    ]);
  });

  it("gives an admin role pickers, removal and the waiting invitations", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(workspaceApi.invitations).mockResolvedValue([
      {
        id: "i1",
        email: "omar@firm.example",
        role: "MEMBER",
        invitedByName: "A Lead",
        createdAt: new Date(Date.now() - 3 * 86_400_000).toISOString(),
        expiresAt: "2099-01-01T00:00:00Z",
      },
    ]);

    renderPage();

    expect(
      (
        await screen.findAllByRole("combobox", {
          name: "Workspace role for Rania Haddad",
        })
      ).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: "Remove Rania Haddad" }).length).toBeGreaterThan(0);
    expect(await screen.findByText("Outstanding invitations · 1")).toBeInTheDocument();
    expect(screen.getByText(/sent 3 days ago by A Lead/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Resend" })).toBeInTheDocument();
    expect(screen.getByText("2 members · each position has its own lead")).toBeInTheDocument();
  });

  it("shows a member the roster and how many invitations wait, with nothing to change", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER"]));
    vi.mocked(workspaceApi.pendingInvitationCount).mockResolvedValue({
      count: 2,
    });

    renderPage();

    expect(
      await screen.findByText("2 invitations are waiting to be accepted. An admin manages them."),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Rania Haddad").length).toBeGreaterThan(0);
    expect(screen.queryByRole("combobox", { name: /Workspace role for/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Remove/ })).not.toBeInTheDocument();
    expect(workspaceApi.invitations).not.toHaveBeenCalled();
  });

  it("says a refused invitations read failed, rather than that nobody is waiting", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(workspaceApi.invitations).mockRejectedValue(new Error("403"));

    renderPage();

    expect(await screen.findByText("Couldn't load the invitations waiting to be accepted.")).toBeInTheDocument();
  });

  it("asks before an admin makes themselves a Member", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(workspaceApi.invitations).mockResolvedValue([]);

    renderPage();

    const [own] = await screen.findAllByRole("combobox", { name: "Workspace role for A Lead" });
    await user.selectOptions(own, "MEMBER");

    expect(await screen.findByRole("dialog", { name: "Make yourself a Member?" })).toBeInTheDocument();
    expect(workspaceApi.changeMemberRoles).not.toHaveBeenCalled();
    expect(screen.getAllByText("(you)").length).toBeGreaterThan(0);
  });
});
