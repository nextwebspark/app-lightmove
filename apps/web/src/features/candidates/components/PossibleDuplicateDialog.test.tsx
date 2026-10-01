import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser } from "../../../test/fixtures/user";
import * as poolApi from "../api/poolApi";
import type { PersonRecord } from "../api/types";
import { PossibleDuplicateDialog } from "./PossibleDuplicateDialog";

vi.mock("../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/poolApi")>()),
  getPerson: vi.fn(),
  tagCatalog: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: aUser() }) }));

const layla = {
  personId: "person-1",
  fullName: "Layla Nasser",
  title: "Director of Leasing",
  companyName: "Aldar Properties",
  locationCity: "Abu Dhabi",
  enrichedAt: null,
  positions: [
    {
      candidateId: "c1",
      projectId: "p2",
      positionTitle: "Leasing Director",
      status: "engaged",
      addedByUserId: "u1",
      addedByName: "Yara Haddad",
      addedAt: "2026-09-12T09:00:00Z",
      source: "ai_sourced",
      workable: true,
    },
  ],
  ownerUserId: null,
  doNotContact: null,
  tagIds: [],
  source: "ai_sourced",
  addedAt: "2026-09-12T09:00:00Z",
  addedByUserId: "u1",
  addedByName: "Yara Haddad",
} as unknown as PersonRecord;

function renderDialog(onUseExisting = vi.fn(), onAddAsNew = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PossibleDuplicateDialog
        fullName="Layla Nasser"
        employerName="Aldar Properties"
        personIds={["person-1"]}
        isSaving={false}
        onUseExisting={onUseExisting}
        onAddAsNew={onAddAsNew}
        onClose={vi.fn()}
      />
    </QueryClientProvider>,
  );
  return { onUseExisting, onAddAsNew };
}

describe("PossibleDuplicateDialog", () => {
  beforeEach(() => {
    vi.mocked(poolApi.getPerson).mockReset().mockResolvedValue(layla);
  });

  it("shows the person the team already has, with the positions they sit on", async () => {
    renderDialog();

    expect(await screen.findByText("Director of Leasing · Aldar Properties · Abu Dhabi")).toBeInTheDocument();
    expect(screen.getByText("Leasing Director · Engaged")).toBeInTheDocument();
    expect(screen.getByText(/added .* by Yara Haddad · Sourced/)).toBeInTheDocument();
  });

  it("adds the existing person here, or a different person, as answered", async () => {
    const { onUseExisting, onAddAsNew } = renderDialog();
    await screen.findByText("Leasing Director · Engaged");

    await userEvent.click(screen.getByRole("button", { name: "Yes — add Layla Nasser here" }));
    expect(onUseExisting).toHaveBeenCalledWith("person-1");

    await userEvent.click(screen.getByRole("button", { name: "No — add a different person" }));
    expect(onAddAsNew).toHaveBeenCalled();
  });
});
