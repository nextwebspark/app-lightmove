import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as projectsApi from "../../projects/api/projectsApi";
import * as workspaceApi from "../api/workspaceApi";
import { TeamPage } from "./TeamPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/workspaceApi")>()),
  members: vi.fn(),
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

/** A refused roster must not be reported as "0 members" — a count the caller could not read. */
describe("TeamPage — a refused read", () => {
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

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue({
      id: "u1",
      email: "lead@firm.example",
      fullName: "A Lead",
      title: null,
      avatarUrl: null,
      emailVerified: true,
      onboardingHeld: false,
      pendingInvitation: null,
      workspace: {
        id: "w1",
        name: "Meridian",
        slug: "meridian",
        logoMark: "M",
        emailDomain: "firm.example",
        roles: ["ADMIN" as const],
      },
    });
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
