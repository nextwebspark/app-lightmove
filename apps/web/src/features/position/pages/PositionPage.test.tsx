import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { Project } from "../../projects/api/types";
import * as positionApi from "../api/positionApi";
import type { Position, PositionTemplate } from "../api/types";
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
  withdrawPublication: vi.fn(),
  listTemplates: vi.fn(),
  applyTemplate: vi.fn(),
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
    strategicPriorities: [
      { name: "Capital discipline", selected: false },
      { name: "Portfolio growth", selected: false },
    ],
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

const catalog: PositionTemplate[] = [
  {
    id: "t-cfo",
    code: "chief-financial-officer",
    title: "Chief Financial Officer",
    discipline: "FINANCE",
    seniority: "C_SUITE",
    summary: "Group finance, the capital structure and the shareholder relationship.",
    shared: true,
  },
  {
    id: "t-cco",
    code: "chief-compliance-officer",
    title: "Chief Compliance Officer",
    discipline: "GOVERNANCE",
    seniority: "C_SUITE",
    summary: "The compliance programme and the regulatory relationship.",
    shared: true,
  },
  {
    id: "t-hoc",
    code: "head-of-compliance",
    title: "Head of Compliance",
    discipline: "GOVERNANCE",
    seniority: "N_MINUS_1",
    summary: "Day-to-day compliance, monitoring and the regulatory submissions.",
    shared: true,
  },
];

/** What the compliance template redraws the brief into. */
const redrafted: Position = {
  ...seeded,
  details: {
    ...seeded.details,
    roleTitle: "Chief Financial Officer",
    department: "Compliance",
    responsibilities: ["Group compliance framework and policy"],
  },
  assessment: {
    criteria: [{ text: "Led compliance for a regulated entity", mode: "REQUIRED", fromBrief: true }],
    technical: [{ name: "Regulatory Framework & Licensing", description: null, weight: 100 }],
    behavioural: [{ name: "Independence & Objectivity", description: null, weight: 100 }],
  },
};

/** Where the wizard navigated to, for the step that leaves the screen entirely. */
function Whereabouts() {
  return <span data-testid="location">{useLocation().pathname}</span>;
}

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
            <Whereabouts />
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const published: Position = {
  ...seeded,
  publication: { publishedAt: "2026-08-27T10:00:00Z", publishedBy: "Alok Kumar" },
};

describe("PositionPage", () => {
  beforeEach(() => {
    vi.mocked(restoreSession).mockResolvedValue(null);
    vi.mocked(authApi.me).mockResolvedValue(user);
    vi.mocked(positionApi.getPosition).mockResolvedValue(seeded);
    vi.mocked(positionApi.listTemplates).mockResolvedValue(catalog);
  });

  it("opens on step one and reads the brief back in the summary rail", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Position details" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("Chief Financial Officer")).toBeInTheDocument();

    const rail = screen.getByRole("complementary");
    expect(within(rail).getByText("Reports to Group CEO")).toBeInTheDocument();
    // One of six: details is complete. The seed also balances both competency panels to 100, but
    // nobody has reached step five, and the rail does not tick a step on the seed's behalf.
    expect(within(rail).getByText("17% Done")).toBeInTheDocument();
  });

  it("only counts a step done once it has been reached", async () => {
    renderPage();
    const user = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: /Assessment criteria/ }));

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

  it("lights, drops and adds a strategic priority", async () => {
    vi.mocked(positionApi.putContext).mockResolvedValue(seeded);
    renderPage();
    const user = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: /Mandate context/ }));

    // A chip in the palette is off until somebody lights it.
    const growth = screen.getByRole("button", { name: "Portfolio growth" });
    expect(growth).toHaveAttribute("aria-pressed", "false");
    await user.click(growth);
    await waitFor(() => expect(positionApi.putContext).toHaveBeenCalled());
    expect(
      vi.mocked(positionApi.putContext).mock.calls.at(-1)?.[1].strategicPriorities,
    ).toEqual([
      { name: "Capital discipline", selected: false },
      { name: "Portfolio growth", selected: true },
    ]);

    await user.click(screen.getByRole("button", { name: "Remove Capital discipline" }));
    expect(
      vi.mocked(positionApi.putContext).mock.calls.at(-1)?.[1].strategicPriorities,
    ).toEqual([{ name: "Portfolio growth", selected: true }]);

    // Anything the palette does not offer is typed in, and arrives lit — adding one is choosing it.
    await user.click(screen.getByRole("button", { name: "+ Add priority" }));
    await user.type(screen.getByRole("textbox", { name: "Name the priority" }), "Lender confidence{Enter}");
    expect(
      vi.mocked(positionApi.putContext).mock.calls.at(-1)?.[1].strategicPriorities,
    ).toEqual([
      { name: "Portfolio growth", selected: true },
      { name: "Lender confidence", selected: true },
    ]);
  });

  it("publishes from the rail and shows the brief as published, still editable", async () => {
    vi.mocked(positionApi.publish).mockResolvedValue(published);
    renderPage();
    const user = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: "Publish position profile" }));

    expect(await screen.findByText("✓ Published")).toBeInTheDocument();
    // Publishing is a stamp, not a lock: step one's fields keep accepting input.
    expect(screen.getByDisplayValue("Chief Financial Officer")).toBeEnabled();
  });

  it("opens a published brief on its own review, complete, for whoever comes back to it", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue(published);
    renderPage();

    // Nobody has walked the wizard in this sitting — publishing is what says the whole brief has
    // been through, and it is stored, so a colleague opening it cold reads the same thing.
    expect(await screen.findByRole("heading", { name: "Review & publish" })).toBeInTheDocument();
    expect(screen.getByText("Position profile published")).toBeInTheDocument();

    const rail = screen.getByRole("complementary");
    expect(within(rail).getByText("50% Done")).toBeInTheDocument();
    expect(within(rail).getByRole("button", { name: /Move to strategy/ })).toBeInTheDocument();
  });

  it("takes a published brief back into edit through the section it names", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue(published);
    renderPage();
    const user = userEvent.setup();

    // A published brief reads back rather than inviting edits until somebody says they mean to.
    expect(await screen.findByText("Position profile published")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();

    const rail = screen.getByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: "Edit position" }));

    const sections = screen.getAllByRole("button", { name: "Edit" });
    expect(sections).toHaveLength(5);
    await user.click(sections[1]);
    expect(screen.getByRole("heading", { name: "Mandate context" })).toBeInTheDocument();
  });

  it("closes an edit of a published brief by publishing it again, and never by withdrawing", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue(published);
    renderPage();
    const user = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: "Edit position" }));
    expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(5);

    // Calls, not implementations: the mocks are shared across this file's tests.
    vi.mocked(positionApi.publish).mockClear();
    vi.mocked(positionApi.withdrawPublication).mockClear();
    await user.click(within(rail).getByRole("button", { name: "Publish changes" }));

    // Back to reading it: the sections stop offering their way in, and the rail leads on again.
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument(),
    );
    expect(within(rail).getByRole("button", { name: "Edit position" })).toBeInTheDocument();
    // The stamp is already there. Publishing again must not move it, and must never be the
    // withdrawal the same button used to perform.
    expect(positionApi.publish).not.toHaveBeenCalled();
    expect(positionApi.withdrawPublication).not.toHaveBeenCalled();
  });

  it("moves on to the mandate's market once the brief is published", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue(published);
    renderPage();
    const user = userEvent.setup();

    const rail = await screen.findByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: /Move to strategy/ }));

    expect(await screen.findByTestId("location")).toHaveTextContent("/projects/p1/strategy");
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

  it("suggests role templates, and lets a title nothing matches be typed anyway", async () => {
    renderPage();
    const user = userEvent.setup();

    const title = await screen.findByRole("combobox", { name: /Role title/ });
    await user.clear(title);
    await user.type(title, "complian");

    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      expect.stringContaining("Chief Compliance Officer"),
      expect.stringContaining("Head of Compliance"),
    ]);

    // Enter on a typed title commits nothing: the field is the value, and the list is an offer.
    await user.type(title, "{Enter}");
    expect(positionApi.applyTemplate).not.toHaveBeenCalled();
    expect(title).toHaveValue("complian");
  });

  it("drafts the brief from a picked template, and takes its title", async () => {
    vi.mocked(positionApi.applyTemplate).mockResolvedValue(redrafted);
    vi.mocked(positionApi.putDetails).mockResolvedValue(redrafted);
    renderPage();
    const user = userEvent.setup();

    const title = await screen.findByRole("combobox", { name: /Role title/ });
    await user.clear(title);
    await user.type(title, "Chief Compliance");
    await user.click(within(screen.getByRole("listbox")).getByRole("option", { name: /Chief Compliance Officer/ }));

    await waitFor(() =>
      expect(positionApi.applyTemplate).toHaveBeenCalledWith("p1", "t-cco"),
    );
    // The title is the type-ahead's to write, through the ordinary details save — the template
    // itself never renames the mandate.
    await waitFor(() =>
      expect(positionApi.putDetails).toHaveBeenCalledWith(
        "p1",
        expect.objectContaining({ roleTitle: "Chief Compliance Officer", department: "Compliance" }),
      ),
    );

    // Every step reseats from the redraft, not just the one in view: step five is the proof, because
    // its own autosave would otherwise put the old panels back over the new brief.
    const rail = screen.getByRole("complementary");
    await user.click(within(rail).getByRole("button", { name: /Assessment criteria/ }));
    expect(await screen.findByDisplayValue("Regulatory Framework & Licensing")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("Treasury")).not.toBeInTheDocument();
  });

  it("keeps the title typeable when the catalog cannot be read", async () => {
    vi.mocked(positionApi.listTemplates).mockRejectedValue(new Error("nope"));
    renderPage();
    const user = userEvent.setup();

    const title = await screen.findByRole("combobox", { name: /Role title/ });
    await user.clear(title);
    await user.type(title, "Group CFO – Energy Division");

    expect(title).toHaveValue("Group CFO – Energy Division");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });
});
