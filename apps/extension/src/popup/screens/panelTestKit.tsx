import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import type { ProjectSummary, WorkspaceUser } from "../../api/types";
import type { ActivePage } from "../hooks/useActivePage";
import type { ProjectSelection } from "../hooks/useProjectSelection";

export const USER: WorkspaceUser = { id: "u1", fullName: "Yara Haddad", email: "yara@meridian.example" };

export const POSITION: ProjectSummary = {
  id: "p1",
  positionTitle: "Group CFO",
  clientName: "Meridian Energy Group",
  clientLogoUrl: null,
  team: [{ userId: "u1", fullName: "Yara Haddad", avatarUrl: null, projectRoles: ["LEAD"] }],
  archivedAt: null,
};

export const PROJECTS: ProjectSelection = {
  projects: [POSITION],
  recentProjectIds: [],
  selectedProjectId: "p1",
  selectedProject: POSITION,
  selectProject: () => undefined,
  isLoading: false,
};

export function pageRead(overrides: Partial<ActivePage> = {}): ActivePage {
  return {
    subject: "person",
    person: { fullName: "Faisal Al Nuaimi", linkedinUrl: "https://www.linkedin.com/in/faisal-al-nuaimi/" },
    company: { companyName: "ACWA Power", linkedinUrl: "https://www.linkedin.com/company/acwa-power/" },
    sourceUrl: "https://www.linkedin.com/in/faisal-al-nuaimi/",
    pageKey: "person:faisal-al-nuaimi",
    isReading: false,
    hasReadOnce: true,
    readError: null,
    rescan: async () => undefined,
    ...overrides,
  } as ActivePage;
}

export const PIPELINE = {
  stages: [
    {
      id: "s1", key: "identified", label: "Research", colour: "neutral", position: 1, retired: false,
      statuses: [
        { id: "a", key: "identified", label: "Identified", colour: "neutral", position: 1, isDefault: true, outOfProcess: false, retired: false },
      ],
    },
    {
      id: "s2", key: "contacted", label: "Approach", colour: "violet", position: 2, retired: false,
      statuses: [
        { id: "b", key: "callArranged", label: "Call arranged", colour: "violet", position: 1, isDefault: true, outOfProcess: false, retired: false },
      ],
    },
  ],
};

export function candidate(overrides: Record<string, unknown> = {}) {
  return {
    id: "c1",
    personId: "person-1",
    fullName: "Faisal Al Nuaimi",
    title: "Chief Financial Officer",
    companyName: "ACWA Power",
    triageCompanyId: null,
    linkedinUrl: "https://www.linkedin.com/in/faisal-al-nuaimi/",
    enrichedAt: "2026-10-01T10:00:00Z",
    addedAt: "2026-10-12T10:00:00Z",
    status: "callArranged",
    statusLabel: "Call arranged",
    statusColour: "violet",
    statusOutOfProcess: false,
    pipelineStage: { key: "contacted", label: "Approach", colour: "violet" },
    ...overrides,
  };
}

export function renderPanel(element: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return render(element, { wrapper });
}
