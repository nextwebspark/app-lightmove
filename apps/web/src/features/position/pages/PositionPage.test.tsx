import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
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
  putDetails: vi.fn(),
  putContext: vi.fn(),
  putReporting: vi.fn(),
  putCompensation: vi.fn(),
  putCriteria: vi.fn(),
  putCompetencies: vi.fn(),
  publish: vi.fn(),
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
  joinedAt: null,
};

const user = {
  id: "u1",
  email: "alok@nextwebspark.com",
  fullName: "Alok Kumar",
  title: null,
  avatarUrl: null,
  emailVerified: true,
  hasPassword: true,
  timezone: "Asia/Dubai",
  locale: "en",
  pendingInvitation: null,
  workspace: { ...workspace, roles: ["ADMIN"] as ("ADMIN" | "MEMBER")[] },
};

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
  details: {
    roleTitle: "Chief Financial Officer",
    department: "Group Finance",
    location: "Abu Dhabi, UAE",
    employmentType: "FULL_TIME_PERMANENT",
    seniority: "C_SUITE",
    responsibilities: ["Group P&L stewardship"],
    narrative: "A hands-on CFO.",
  },
  context: {
    mandateReason: "NEW_ROLE",
    businessDriver: null,
    strategicPriorities: [],
    hiringUrgency: "STANDARD",
    confidential: false,
    internalContext: null,
  },
  reporting: {
    orgChart: [
      { nodeId: "n-manager", parentNodeId: null, title: "Group CEO", name: null, mandateSeat: false, canvasX: null, canvasY: null },
      { nodeId: "n-seat", parentNodeId: "n-manager", title: null, name: null, mandateSeat: true, canvasX: null, canvasY: null },
    ],
    teamSize: null,
    targetStart: null,
    noticeValue: null,
    noticeUnit: null,
  },
  compensation: {
    currency: "USD",
    salaryMin: null,
    salaryMax: null,
    baseSalaryMode: "ANNUAL",
    bonusValue: null,
    bonusBasis: null,
    incentiveType: null,
    incentiveAmount: null,
    incentiveVesting: null,
    benefits: [],
  },
  assessment: {
    criteria: [{ text: "Board reporting experience", mode: "REQUIRED", fromBrief: true }],
    technical: [
      { name: "Treasury", description: "Debt and liquidity", weight: 60 },
      { name: "Controls", description: null, weight: 40 },
    ],
    behavioural: [{ name: "Strategic Leadership", description: null, weight: 100 }],
  },
  publication: { publishedAt: null, publishedBy: null },
  document: null,
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
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

describe("PositionPage", () => {
  beforeEach(() => {
    vi.mocked(restoreSession).mockResolvedValue(null);
    vi.mocked(authApi.me).mockResolvedValue(user);
    vi.mocked(positionApi.getPosition).mockResolvedValue(seeded);
  });

  it("opens on step one and reads the brief back in the summary rail", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Position details" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("Chief Financial Officer")).toBeInTheDocument();

    const rail = screen.getByRole("complementary");
    expect(within(rail).getByText("Reports to Group CEO")).toBeInTheDocument();
    // Two of six steps are done: details is complete, and both panels total 100.
    expect(within(rail).getByText("33% Done")).toBeInTheDocument();
  });

  it("walks forward with Next and jumps from the rail", async () => {
    renderPage();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: /Next: Mandate context/ }));
    expect(screen.getByRole("heading", { name: "Mandate context" })).toBeInTheDocument();

    const rail = screen.getByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: /Compensation/ }));
    expect(screen.getByRole("heading", { name: "Compensation package" })).toBeInTheDocument();
  });

  it("autosaves the step being edited, and only that step", async () => {
    const saved: Position = { ...seeded, details: { ...seeded.details, department: "Finance" } };
    vi.mocked(positionApi.putDetails).mockResolvedValue(saved);
    renderPage();
    const user = userEvent.setup();

    const department = await screen.findByDisplayValue("Group Finance");
    await user.clear(department);
    await user.type(department, "Finance");

    await waitFor(() => expect(positionApi.putDetails).toHaveBeenCalled());
    expect(positionApi.putContext).not.toHaveBeenCalled();
    expect(vi.mocked(positionApi.putDetails).mock.calls.at(-1)?.[1].department).toBe("Finance");
  });

  it("publishes from the rail and shows the brief as published, still editable", async () => {
    const published: Position = {
      ...seeded,
      publication: { publishedAt: "2026-08-27T10:00:00Z", publishedBy: "Alok Kumar" },
    };
    vi.mocked(positionApi.publish).mockResolvedValue(published);
    renderPage();
    const user = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: "Publish position profile" }));

    expect(await screen.findByText("✓ Published")).toBeInTheDocument();
    // Publishing is a stamp, not a lock: step one's fields keep accepting input.
    expect(screen.getByDisplayValue("Chief Financial Officer")).toBeEnabled();
  });

  it("locks a competency so its weight holds while another is dragged", async () => {
    vi.mocked(positionApi.putCompetencies).mockResolvedValue(seeded);
    renderPage();
    const person = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await person.click(within(rail).getByRole("button", { name: /Assessment criteria/ }));

    const treasurySlider = screen.getByRole("slider", { name: "Treasury slider" });
    expect(treasurySlider).toBeEnabled();

    await person.click(screen.getByRole("button", { name: "Lock Treasury" }));

    // A locked row states itself: the slider and the number both stop accepting input, so the lock
    // is visible rather than something you discover by dragging and nothing moving.
    expect(screen.getByRole("slider", { name: "Treasury slider" })).toBeDisabled();
    expect(screen.getByRole("spinbutton", { name: "Treasury weight" })).toBeDisabled();

    await person.click(screen.getByRole("button", { name: "Unlock Treasury" }));
    expect(screen.getByRole("slider", { name: "Treasury slider" })).toBeEnabled();
  });

  it("offers every competency a keyboard-reachable reorder handle", async () => {
    renderPage();
    const person = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await person.click(within(rail).getByRole("button", { name: /Assessment criteria/ }));

    // Order is the ranking, so reordering must be reachable without a mouse. What is asserted here is
    // the affordance: a real <button>, named for its row, that takes focus and announces itself to
    // dnd-kit's keyboard sensor.
    //
    // The drag itself is deliberately NOT driven here. dnd-kit resolves a drop from element geometry,
    // and jsdom reports every rect as zero at the origin, so a keyboard drag "succeeds" against
    // fabricated layout and proves nothing about the real thing. The reordering logic is covered
    // where it actually lives — moveRow, in lib/competencyRows.test.ts — and the drag and keyboard
    // paths are checked in a browser.
    const handle = screen.getByRole("button", { name: "Reorder Treasury" });
    expect(handle).toHaveAttribute("aria-roledescription", "sortable");
    handle.focus();
    expect(handle).toHaveFocus();
  });

  it("shows the attached position description rather than promising an auto-fill", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue({
      ...seeded,
      document: {
        fileName: "CFO Position Description.pdf",
        contentType: "application/pdf",
        fileSize: 254_000,
        uploadedAt: "2026-08-27T10:00:00Z",
      },
    });
    renderPage();

    expect(await screen.findByText("CFO Position Description.pdf")).toBeInTheDocument();
    expect(screen.queryByText(/auto-fill/i)).not.toBeInTheDocument();
  });
});
