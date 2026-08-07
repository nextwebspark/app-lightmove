import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { Project } from "../../projects/api/types";
import * as positionApi from "../api/positionApi";
import type { Position } from "../api/types";
import { PositionPage } from "./PositionPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/positionApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../api/positionApi")>()),
  getPosition: vi.fn(),
  putPosition: vi.fn(),
  putCriteria: vi.fn(),
  putCompetencies: vi.fn(),
  lockPosition: vi.fn(),
  unlockPosition: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const workspace = {
  id: "w1",
  name: "NextWebSpark Search",
  slug: "nextwebspark-search",
  logoMark: "N",
  emailDomain: "nextwebspark.com",
};

const userOf = (roles: ("ADMIN" | "MEMBER")[]) => ({
  id: "u1",
  email: "alok@nextwebspark.com",
  fullName: "Alok Kumar",
  title: null,
  avatarUrl: null,
  emailVerified: true,
  onboardingHeld: false,
  pendingInvitation: null,
  workspace: { ...workspace, roles },
});

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy Group",
  positionTitle: "Chief Financial Officer",
  stage: "BRIEF",
  health: "OK",
  targetDate: null,
  team: [],
  representatives: [],
  companies: 0,
  candidates: 0,
  createdAt: "2026-07-01T00:00:00Z",
};

const seeded: Position = {
  mandateReason: "NEW_ROLE",
  internalContext: null,
  narrative: "The CFO will sit on the executive committee.",
  reportsTo: "Group CEO",
  directReports: null,
  teamSize: null,
  location: "UAE",
  employmentType: "FULL_TIME_PERMANENT",
  startTarget: null,
  salaryMin: null,
  salaryMax: null,
  currency: "USD",
  noticeValue: null,
  noticeUnit: null,
  bonusTargetPct: null,
  ltip: null,
  benefits: [],
  confidential: false,
  criteria: [
    { text: "Experience reporting to a board", mode: "REQUIRED", fromBrief: true },
    { text: "Arabic language skills", mode: "PREFERRED", fromBrief: false },
  ],
  technical: [
    { name: "Financial Reporting & Controls", weight: 60 },
    { name: "Treasury", weight: 40 },
  ],
  behavioural: [{ name: "Strategic Leadership", weight: 100 }],
  locked: false,
  lockedAt: null,
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthProvider>
          <ToastProvider>
            <Routes>
              <Route element={<Outlet context={{ project }} />}>
                <Route path="/" element={<PositionPage />} />
              </Route>
            </Routes>
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("PositionPage — the brief editor", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(userOf(["MEMBER"]));
    vi.mocked(positionApi.getPosition).mockResolvedValue(seeded);
  });

  it("renders the seeded brief: hero, template criteria and the from-brief tag", async () => {
    renderPage();

    // The title shows twice: the hero and the org row's "This role" box.
    expect(await screen.findAllByText("Chief Financial Officer")).toHaveLength(2);
    expect(screen.getByDisplayValue("The CFO will sit on the executive committee.")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Experience reporting to a board")).toBeInTheDocument();
    expect(screen.getByText("From brief")).toBeInTheDocument();
    // Employment type is a fixed-set select showing the seeded value's label.
    expect(screen.getByDisplayValue("Full-time, permanent").tagName).toBe("SELECT");
  });

  it("autosaves a criterion mode change after the debounce", async () => {
    vi.mocked(positionApi.putCriteria).mockResolvedValue({ ...seeded });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    // Flip "Arabic language skills" from Preferred to Required.
    await userEvent.click(screen.getAllByRole("button", { name: "Required" })[1]);

    await waitFor(() => expect(positionApi.putCriteria).toHaveBeenCalledTimes(1), { timeout: 2000 });
    const [, sent] = vi.mocked(positionApi.putCriteria).mock.calls[0];
    expect(sent[1].mode).toBe("REQUIRED");
  });

  it("commits a benefit on blur (no Enter) — the dropped-benefit fix", async () => {
    vi.mocked(positionApi.putPosition).mockResolvedValue({ ...seeded });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    const field = screen.getByLabelText("Add a benefit");
    await userEvent.type(field, "Car allowance");
    await userEvent.tab(); // blur without pressing Enter

    await waitFor(() => expect(positionApi.putPosition).toHaveBeenCalled(), { timeout: 2000 });
    const lastCall = vi.mocked(positionApi.putPosition).mock.calls.at(-1)!;
    expect(lastCall[1].benefits).toContain("Car allowance");
  });

  it("keeps Lock disabled while a panel is off 100%, with the checklist saying which", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue({
      ...seeded,
      technical: [{ name: "Treasury", weight: 90 }],
    });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    expect(screen.getByRole("button", { name: "Lock position" })).toBeDisabled();
    expect(screen.getByText("Technical weights total 100% (currently 90%)")).toBeInTheDocument();
  });

  it("locks a ready brief and reports the new benchmark", async () => {
    vi.mocked(positionApi.lockPosition).mockResolvedValue({ ...seeded, locked: true, lockedAt: "2026-07-17T00:00:00Z" });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    await userEvent.click(screen.getByRole("button", { name: "Lock position" }));

    expect(await screen.findByText("Position locked")).toBeInTheDocument();
    expect(positionApi.lockPosition).toHaveBeenCalledWith("p1");
  });

  it("a locked brief is read-only, and Unlock is not offered to a plain member", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue({ ...seeded, locked: true, lockedAt: "2026-07-17T00:00:00Z" });
    renderPage();
    await screen.findByText("Position locked");

    expect(screen.getByDisplayValue("Experience reporting to a board")).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Unlock" })).not.toBeInTheDocument();
  });

  it("offers Unlock to a workspace admin", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userOf(["ADMIN"]));
    vi.mocked(positionApi.getPosition).mockResolvedValue({ ...seeded, locked: true, lockedAt: "2026-07-17T00:00:00Z" });
    renderPage();

    expect(await screen.findByRole("button", { name: "Unlock" })).toBeInTheDocument();
  });
});
