import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../components/ui";
import { AuthProvider } from "../features/auth/AuthProvider";
import * as authApi from "../features/auth/api/authApi";
import * as clientsApi from "../features/clients/api/clientsApi";
import * as consentApi from "../features/oauth/api/oauthConsentApi";
import * as projectsApi from "../features/projects/api/projectsApi";
import * as templateAdminApi from "../features/templates/api/templateAdminApi";
import * as triageApi from "../features/triage/api/triageApi";
import * as workspaceApi from "../features/workspace/api/workspaceApi";
import { AppRoutes } from "./routes";

vi.mock("../features/auth/api/authApi");
vi.mock("../features/oauth/api/oauthConsentApi");
vi.mock("../features/projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../features/clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/clients/api/clientsApi")>()),
  clients: vi.fn(),
}));
vi.mock("../features/triage/api/triageApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/triage/api/triageApi")>()),
  getTriageCounts: vi.fn(),
}));
vi.mock("../features/templates/api/templateAdminApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/templates/api/templateAdminApi")>()),
  listTemplates: vi.fn(),
}));
vi.mock("../features/workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../features/workspace/api/workspaceApi")>()),
  members: vi.fn(),
  workspace: vi.fn(),
}));
vi.mock("../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../lib/apiClient");

const userWith = (roles: ("ADMIN" | "MEMBER" | "CLIENT")[], mode: "AGENCY" | "COMPANY" = "COMPANY") => ({
  id: "u1",
  email: "someone@firm.example",
  fullName: "Someone",
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
    mode,
    emailDomain: "firm.example",
    joinedAt: null,
    company: null,
    roles,
  },
});

const unverifiedUser = () => ({
  id: "u1",
  email: "someone@firm.example",
  fullName: "Someone",
  title: null,
  avatarUrl: null,
  emailVerified: false,
  hasPassword: true,
  timezone: "Asia/Dubai",
  locale: "en",
  platformActions: [],
  pendingInvitations: [],
  workspaces: [],
  workspace: null,
});

function Pathname() {
  const location = useLocation();
  return (
    <>
      <div data-testid="pathname">{location.pathname}</div>
      <div data-testid="from">{String((location.state as { from?: string } | null)?.from ?? "")}</div>
    </>
  );
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
 * The hard gate. Verification is step 2 of signup, and the steps after it must not be reachable by
 * typing their URL — the API refuses /onboarding/** to an unverified session, so a user who got past
 * the router would only meet a 403 with nowhere to go.
 */
describe("routes — the verification gate", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
  });

  it.each(["/signup/workspace", "/signup/invite"])(
    "sends an unverified user who types %s back to the verify step",
    async (path) => {
      vi.mocked(authApi.me).mockResolvedValue(unverifiedUser());

      renderAt(path);

      await waitFor(() =>
        expect(screen.getByTestId("pathname").textContent).toBe("/signup/verify-email"),
      );
    },
  );

  it("lets a verified user with no workspace reach the organisation step", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...unverifiedUser(), emailVerified: true });

    renderAt("/signup/workspace");

    await waitFor(() =>
      expect(screen.getByTestId("pathname").textContent).toBe("/signup/workspace"),
    );
  });
});

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

  it.each(["/clients", "/team", "/candidates"])("bounces a pure client who types %s", async (path) => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["CLIENT"]));

    renderAt(path);

    // waitFor, because the guards render Booting until the session restore resolves — asserting
    // straight away would read the pathname before any redirect could have happened. And an exact
    // match, because toHaveTextContent is a substring test and "/clients" contains "/".
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
    expect(screen.queryByText("Add your first business unit")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new business unit/i })).not.toBeInTheDocument();
  });

  // The predicate is "holds CLIENT and no staff role". Someone who is both is staff, and losing these
  // two pages for them would be the same bug pointed the other way.
  it("keeps the registry for a member who also holds CLIENT", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER", "CLIENT"]));

    renderAt("/clients");

    expect(await screen.findByText("Add your first business unit")).toBeInTheDocument();
  });

  it("keeps the roster for a member who also holds CLIENT", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER", "CLIENT"]));

    renderAt("/team");

    expect(await screen.findByText(/0 members/)).toBeInTheDocument();
  });
});

describe("routes — the nav names the registry by the workspace's mode", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
  });

  it.each([
    ["COMPANY", "Business units"],
    ["AGENCY", "Clients"],
  ] as const)("a %s workspace's nav offers %s", async (mode, label) => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"], mode));

    renderAt("/");

    expect(await screen.findByRole("link", { name: new RegExp(`^${label}`) })).toHaveAttribute("href", "/clients");
  });
});

/**
 * Settings is two gates behind one shell. Account is the caller's own account, so a member — and a
 * portal guest, who has a name and a timezone like anyone else — must reach it; the workspace sections
 * stay admin-only. Gating the whole area on ADMIN, as it once was, left a non-admin no settings at all.
 */
describe("routes — the settings gates", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
  });

  it("lands /settings on Profile, the section everyone can read", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER"]));

    renderAt("/settings");

    await waitFor(() =>
      expect(screen.getByTestId("pathname").textContent).toBe("/settings/profile"),
    );
    expect(await screen.findByText("How you appear across the workspace")).toBeInTheDocument();
  });

  it("keeps Security in Account, where a non-admin reaches it", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER"]));
    vi.mocked(authApi.listSessions).mockResolvedValue([]);

    renderAt("/settings/security");

    expect(
      await screen.findByText("Password, two-factor authentication and sessions"),
    ).toBeInTheDocument();
  });

  it("opens /settings on General for an admin the first time, and where they last were after", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(authApi.listSessions).mockResolvedValue([]);
    localStorage.removeItem("lightmove.settings.lastSection.u1");

    const first = renderAt("/settings");
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/settings/general"));
    first.unmount();

    localStorage.setItem("lightmove.settings.lastSection.u1", "/settings/security");
    renderAt("/settings");
    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/settings/security"));
    localStorage.removeItem("lightmove.settings.lastSection.u1");
  });

  it("never opens /settings on a section the caller can no longer reach", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER"]));
    localStorage.setItem("lightmove.settings.lastSection.u1", "/settings/general");

    renderAt("/settings");

    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/settings/profile"));
    localStorage.removeItem("lightmove.settings.lastSection.u1");
  });

  it("sends the old Members address to Team, the one roster", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));

    renderAt("/settings/members");

    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/team"));
  });

  it.each(["/settings/general", "/settings/templates", "/settings/integrations"])(
    "bounces a non-admin who types %s",
    async (path) => {
      vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER"]));

      renderAt(path);

      await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
    },
  );

  it("keeps the workspace sections for an admin", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(workspaceApi.workspace).mockResolvedValue({
      id: "w1",
      name: "Meridian",
      slug: "meridian",
      logoMark: "M",
      mode: "COMPANY" as const,
      calendarSync: "RECALL" as const,
      emailDomain: "firm.example",
      defaultRegion: "GCC",
      defaultCurrency: "USD",
      plan: "FREE",
      memberCount: 1,
      createdAt: "2026-03-14T09:00:00Z",
      persona: { summary: null, sectors: [], competitors: [], geographies: [], notes: null },
      company: null,
    });

    renderAt("/settings/general");

    expect(await screen.findByText("Workspace identity and defaults")).toBeInTheDocument();
  });
});

/**
 * The template library is LightMove staff's, not any workspace's: being a workspace admin does not
 * reach it, and a super admin reaches it whatever their workspace role.
 */
describe("routes — the platform gate", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(templateAdminApi.listTemplates).mockResolvedValue([]);
  });

  it("bounces a workspace admin who is not LightMove staff from the template library", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));

    renderAt("/settings/template-library");

    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
  });

  it("opens the template library to a super admin, and puts it in their settings rail", async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      ...userWith(["MEMBER"]),
      platformActions: ["TEMPLATE_LIBRARY_MANAGE" as const],
    });

    renderAt("/settings/template-library");

    expect(await screen.findByRole("table", { name: "Templates" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Template library" }).length).toBeGreaterThan(0);
  });
});

/**
 * Pairing the browser extension is a one-gesture flow: the panel opens /extension/connect, the page
 * mints a token and hands it over. A consultant who is signed out lands on login instead, and dropping
 * where they were headed left them on the projects list with the extension still unpaired.
 */
describe("routes — returning to where the guard interrupted", () => {
  beforeEach(() => {
    vi.mocked(restoreSession).mockResolvedValue(null);
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
  });

  it("remembers the extension pairing page a signed-out consultant was headed for", async () => {
    renderAt("/extension/connect");

    await waitFor(() => expect(screen.getByTestId("pathname")).toHaveTextContent("/login"));
    expect(screen.getByTestId("from")).toHaveTextContent("/extension/connect");
  });

  it("signs an AI app's visitor in by password and lands back on its consent request, not the projects list", async () => {
    const consent = "/oauth/consent?client_id=claude&redirect_uri=https%3A%2F%2Fclaude.ai%2Fcb&scope=projects%3Aread";
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: "t", expiresIn: 900, user: userWith(["MEMBER"]) });
    vi.mocked(consentApi.getConsentContext).mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderAt(consent);

    await user.type(await screen.findByLabelText(/email/i), "someone@firm.example");
    await user.type(screen.getByLabelText(/password/i), "a long password");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(screen.getByTestId("pathname")).toHaveTextContent("/oauth/consent"));
    // The path changes on navigate; the consent form mounts and asks a tick later, which a slow CI runner shows.
    await waitFor(() =>
      expect(consentApi.getConsentContext).toHaveBeenCalledWith("claude", "https://claude.ai/cb", "projects:read"),
    );
  });
});

/**
 * A typo'd URL, a stale bookmark, a deleted mandate and one the caller is not seated on all used to
 * bounce silently to My projects, leaving the four indistinguishable. They now render a screen that
 * says so — and keep the URL, so the address bar still shows what was asked for.
 */
describe("routes — the not-found screen", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(userWith(["MEMBER"]));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
    vi.mocked(triageApi.getTriageCounts).mockResolvedValue({
      inUniverse: 0,
      shortlisted: 0,
      declined: 0,
    });
  });

  it("renders an unknown route in the app shell instead of redirecting", async () => {
    renderAt("/nowhere-in-particular");

    expect(await screen.findByText("We couldn't open that page")).toBeInTheDocument();
    expect(screen.getByTestId("pathname").textContent).toBe("/nowhere-in-particular");
    // The shell, not a bare page: the rail is how the user gets anywhere else from here.
    expect(screen.getByRole("link", { name: /my positions/i })).toBeInTheDocument();
  });

  it("renders a project id it cannot read, rather than the list", async () => {
    renderAt("/projects/not-a-project/strategy");

    expect(await screen.findByText("We couldn't open that position")).toBeInTheDocument();
    expect(screen.getByTestId("pathname").textContent).toBe("/projects/not-a-project/strategy");
  });

  // Neither cause may be claimed: the app cannot tell a deleted mandate from one the caller is not
  // seated on, because the server deliberately does not say.
  it("blames neither the resource nor the caller's access", async () => {
    renderAt("/projects/not-a-project");

    const body = await screen.findByText(/may have been deleted/);
    expect(body).toHaveTextContent(/may not be on its team/);
  });

  it("offers the way back to My projects", async () => {
    renderAt("/nowhere-in-particular");

    await userEvent.click(await screen.findByRole("button", { name: "Go to My positions" }));

    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/"));
  });
});

describe("routes — an unknown address with no session", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue(null);
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
  });

  it("says the page wasn't found and offers both ways in, keeping the address", async () => {
    renderAt("/terms");

    expect(await screen.findByText("We couldn’t find that page")).toBeInTheDocument();
    expect(screen.getByTestId("pathname").textContent).toBe("/terms");
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
    expect(screen.getByRole("link", { name: "Create an account" })).toHaveAttribute("href", "/signup");
  });

  it("still sends a known in-app address to sign in, remembering it", async () => {
    renderAt("/projects/p1");

    await waitFor(() => expect(screen.getByTestId("pathname")).toHaveTextContent("/login"));
    expect(screen.getByTestId("from")).toHaveTextContent("/projects/p1");
  });
});

describe("routes — changing a mistyped address on the verify step", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(unverifiedUser());
  });

  it("lands on step 1 with the name kept, not on sign in", async () => {
    vi.mocked(authApi.logout).mockResolvedValue(undefined);
    renderAt("/signup/verify-email");

    await userEvent.click(await screen.findByRole("button", { name: "Change email" }));

    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/signup"));
    expect(await screen.findByLabelText("Full name")).toHaveValue("Someone");
  });

  it("still gets there when the server refuses the sign-out", async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error("503"));
    renderAt("/signup/verify-email");

    await userEvent.click(await screen.findByRole("button", { name: "Change email" }));

    await waitFor(() => expect(screen.getByTestId("pathname").textContent).toBe("/signup"));
  });
});

/** The way back out of a position goes to the list it was opened from, and says so. */
describe("routes — back from a position", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
    vi.mocked(projectsApi.projects).mockResolvedValue([
      {
        id: "p1",
        clientId: "c1",
        clientName: "Beta Client",
        clientLogoUrl: null,
        positionTitle: "CFO Search",
        stage: "DELIVERED",
        health: "OK",
        targetDate: null,
        projectType: "SEARCH",
        startDate: null,
        deliveryDate: null,
        mappingTargetDate: null,
        team: [],
        representatives: [],
        companies: 0,
        candidates: 0,
        mappedCandidates: 0,
        engagedCandidates: 0,
        mappedCompanies: 0,
        createdAt: "2026-07-13T10:00:00Z",
      },
    ]);
  });

  it("returns to All positions with its stage and search, from the rail and the crumb", async () => {
    renderAt("/all?stage=DELIVERED&q=cfo");

    await userEvent.click((await screen.findAllByRole("link", { name: "Open CFO Search" }))[0]);

    const back = await screen.findByRole("link", { name: "All positions" });
    expect(back).toHaveAttribute("href", "/all?stage=DELIVERED&q=cfo");
    expect(back).toHaveAttribute("title", "Back to All positions");
    expect(screen.getByRole("link", { name: "Positions" })).toHaveAttribute("href", "/all?stage=DELIVERED&q=cfo");
  });
});
