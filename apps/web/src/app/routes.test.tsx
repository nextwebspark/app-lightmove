import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../components/ui";
import { AuthProvider } from "../features/auth/AuthProvider";
import * as authApi from "../features/auth/api/authApi";
import * as clientsApi from "../features/clients/api/clientsApi";
import * as projectsApi from "../features/projects/api/projectsApi";
import * as workspaceApi from "../features/workspace/api/workspaceApi";
import { AppRoutes } from "./routes";

vi.mock("../features/auth/api/authApi");
vi.mock("../features/projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../features/clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/clients/api/clientsApi")>()),
  clients: vi.fn(),
}));
vi.mock("../features/workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/workspace/api/workspaceApi")>()),
  members: vi.fn(),
}));
vi.mock("../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../lib/apiClient");

const userWith = (roles: ("ADMIN" | "MEMBER" | "CLIENT")[]) => ({
  id: "u1",
  email: "someone@firm.example",
  fullName: "Someone",
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
    roles,
  },
});

function Pathname() {
  return <div data-testid="pathname">{useLocation().pathname}</div>;
}

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthProvider>
          <ToastProvider>
            <AppRoutes />
            <Pathname />
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

/**
 * The staff surfaces are guarded by the router, not only by the nav that hides them. A portal guest
 * who types /clients or /team used to be served the firm's internal screen — the API refused every
 * call it made, but the page rendered, offered an unusable create form, and reported a client count
 * the guest was never allowed to read.
 */
describe("routes — the staff guard", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
  });

  it.each(["/clients", "/team"])("bounces a pure client who types %s", async (path) => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["CLIENT"]));

    renderAt(path);

    // waitFor, because the guards render Booting until the session restore resolves — asserting
    // straight away would read the pathname before any redirect could have happened. And an exact
    // match, because toHaveTextContent is a substring test and "/clients" contains "/".
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
    expect(screen.queryByText("Add your first client")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new client/i })).not.toBeInTheDocument();
  });

  // The predicate is "holds CLIENT and no staff role". Someone who is both is staff, and losing these
  // two pages for them would be the same bug pointed the other way.
  it("keeps the registry for a member who also holds CLIENT", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER", "CLIENT"]));

    renderAt("/clients");

    expect(await screen.findByText("Add your first client")).toBeInTheDocument();
  });

  it("keeps the roster for a member who also holds CLIENT", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER", "CLIENT"]));

    renderAt("/team");

    expect(await screen.findByText(/0 members/)).toBeInTheDocument();
  });
});
