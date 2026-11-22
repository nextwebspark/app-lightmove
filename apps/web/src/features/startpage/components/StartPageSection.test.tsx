import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as dashboardsApi from "../../dashboards/api/dashboardsApi";
import type { Dashboard } from "../../dashboards/api/types";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as startPageApi from "../api/startPageApi";
import type { StartPage } from "../api/types";
import { StartPageSection } from "./StartPageSection";

const auth: { user: User | null; reload: () => Promise<User | null> } = { user: null, reload: vi.fn() };
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => auth }));
vi.mock("../api/startPageApi");
vi.mock("../../dashboards/api/dashboardsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../dashboards/api/dashboardsApi")>()),
  listDashboards: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));

const home: StartPage = { kind: "HOME", dashboardId: null, projectId: null, section: null, available: true };

function renderSection() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <StartPageSection />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("StartPageSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auth.user = aUser({ startPage: home });
    vi.mocked(startPageApi.saveStartPage).mockImplementation(async (body) => ({
      kind: body.kind,
      dashboardId: body.dashboardId ?? null,
      projectId: body.projectId ?? null,
      section: body.section ?? null,
      available: true,
    }));
    vi.mocked(projectsApi.projects).mockResolvedValue([
      { id: "p1", positionTitle: "Chief Financial Officer", archivedAt: null } as Project,
      { id: "p2", positionTitle: "Closed search", archivedAt: "2026-01-01T00:00:00Z" } as Project,
    ]);
    vi.mocked(dashboardsApi.listDashboards).mockResolvedValue([{ id: "d1", name: "Leadership weekly" } as Dashboard]);
  });

  it("starts on the saved choice, and saves a position on the tab chosen", async () => {
    const user = userEvent.setup();
    renderSection();

    expect(screen.getByRole("radio", { name: /Home/ })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("button", { name: "Save start page" })).toBeDisabled();

    await user.click(screen.getByRole("radio", { name: /A position/ }));
    const position = await screen.findByRole("combobox", { name: "Position" });
    expect(await screen.findByRole("option", { name: "Chief Financial Officer" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Closed search" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save start page" }));
    expect(await screen.findByText("Choose a position", { selector: "span" })).toBeInTheDocument();
    expect(startPageApi.saveStartPage).not.toHaveBeenCalled();

    await user.selectOptions(position, "p1");
    await user.selectOptions(screen.getByRole("combobox", { name: "Open the position on" }), "OUTREACH");
    await user.click(screen.getByRole("button", { name: "Save start page" }));

    await waitFor(() =>
      expect(startPageApi.saveStartPage).toHaveBeenCalledWith({ kind: "POSITION", projectId: "p1", section: "OUTREACH" }),
    );
    expect(auth.reload).toHaveBeenCalled();
  });

  it("saves one of the member's dashboards, and shows a refusal beside it", async () => {
    vi.mocked(startPageApi.saveStartPage).mockRejectedValueOnce(
      new ApiRequestError({ code: "START_PAGE_UNAVAILABLE", detail: "That page is not available to you", status: 422, correlationId: "c1" }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(screen.getByRole("radio", { name: /One of your dashboards/ }));
    await screen.findByRole("option", { name: "Leadership weekly" });
    await user.selectOptions(screen.getByRole("combobox", { name: "Dashboard" }), "d1");
    await user.click(screen.getByRole("button", { name: "Save start page" }));

    expect(await screen.findByText("That page is not available to you. Choose another.")).toBeInTheDocument();
    expect(startPageApi.saveStartPage).toHaveBeenCalledWith({ kind: "DASHBOARD", dashboardId: "d1" });
  });

  it("offers a pure client Home, Positions and a position, never Outreach", async () => {
    auth.user = aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }), startPage: home });
    const user = userEvent.setup();
    renderSection();

    expect(screen.getAllByRole("radio")).toHaveLength(3);
    expect(screen.queryByRole("radio", { name: /Candidates/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /dashboards/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: /A position/ }));
    const tabs = screen.getByRole("combobox", { name: "Open the position on" });
    expect(Array.from(tabs.querySelectorAll("option")).map((option) => option.textContent)).not.toContain("Outreach");
  });

  it("says when the saved page is no longer available, and offers Home", () => {
    auth.user = aUser({ startPage: { ...home, kind: "DASHBOARD", dashboardId: "gone", available: false } });
    renderSection();

    expect(screen.getByText(/no longer available to you, so Uncava opens Home/)).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Home/ })).toHaveAttribute("aria-checked", "true");
  });
});
