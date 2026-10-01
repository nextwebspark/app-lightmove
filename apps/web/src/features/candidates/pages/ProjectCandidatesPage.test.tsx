import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import type { Project } from "../../projects/api/types";
import * as candidatesApi from "../api/candidatesApi";
import * as poolApi from "../api/poolApi";
import type { Candidate, CandidatePipelinePage } from "../api/types";
import { ProjectCandidatesPage } from "./ProjectCandidatesPage";

vi.mock("../api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/candidatesApi")>()),
  getPipeline: vi.fn(),
  getPipelineStaff: vi.fn(),
}));
vi.mock("../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/poolApi")>()),
  tagCatalog: vi.fn(),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../reports/components/ReportCandidateDrawer", () => ({ ReportCandidateDrawer: () => null }));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

let project: Project;
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useOutletContext: () => ({ project }),
}));

const leasing: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Leasing",
  clientLogoUrl: null,
  positionTitle: "Leasing Director",
  stage: "MAPPING",
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
};

const layla = {
  id: "c1",
  triageCompanyId: null,
  companyName: "Aldar Properties",
  fullName: "Layla Nasser",
  title: "Director of Leasing",
  status: "engaged",
  personId: "person-1",
  source: "manual",
  addedAt: "2026-09-12T09:00:00Z",
  enrichedAt: null,
} as unknown as Candidate;

const page: CandidatePipelinePage = {
  candidates: [layla],
  statusCounts: { engaged: 1, identified: 2 },
  totalCount: 1,
  page: 0,
  size: 50,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/projects/p1/candidates"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/projects/:projectId/candidates" element={<ProjectCandidatesPage />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("ProjectCandidatesPage", () => {
  beforeEach(() => {
    vi.mocked(candidatesApi.getPipeline).mockReset().mockResolvedValue(page);
    vi.mocked(candidatesApi.getPipelineStaff)
      .mockReset()
      .mockResolvedValue([
        {
          candidateId: "c1",
          tagIds: [],
          alsoIn: [
            {
              candidateId: "c9",
              projectId: "p2",
              positionTitle: "Chief Operating Officer",
              status: "identified",
              addedByUserId: "u1",
              addedByName: "Alok Kumar",
              addedAt: "2026-09-20T09:00:00Z",
              source: "manual",
              workable: true,
            },
          ],
          addedByUserId: "u1",
          addedByName: "Alok Kumar",
          doNotContact: { reason: "Placed by us", setByUserId: "u1", setByName: "Alok Kumar", setAt: null },
          lastActivity: null,
        },
      ]);
    vi.mocked(poolApi.tagCatalog).mockReset().mockResolvedValue([]);
    project = leasing;
    currentUser = aUser();
  });

  it("draws the staff columns, a chip per status present and do not contact for staff", async () => {
    renderPage();

    expect(await screen.findByText("Chief Operating Officer · Identified")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Also in" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Status of Layla Nasser" })).toHaveValue("engaged");
    expect(screen.getByText("Do not contact")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^All\s*3$/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Engaged\s*1$/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Contacted/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Add from your candidates/ })).toBeInTheDocument();
  });

  it("asks the server for one status when its chip is picked", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /^Engaged\s*1$/ }));

    expect(vi.mocked(candidatesApi.getPipeline)).toHaveBeenLastCalledWith("p1", "", "engaged", 0, 50, expect.anything());
  });

  it("shows a client seat the executive, status and date only, and never asks for the staff columns", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) });
    project = {
      ...leasing,
      team: [{ userId: "u1", projectRoles: ["CLIENT"] }] as unknown as Project["team"],
    };
    renderPage();

    expect(await screen.findByText("Layla Nasser")).toBeInTheDocument();
    expect(screen.getByText("Engaged", { selector: "span" })).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Also in" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Last activity" })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Add from your candidates/ })).not.toBeInTheDocument();
    expect(vi.mocked(candidatesApi.getPipelineStaff)).not.toHaveBeenCalled();
  });
});
