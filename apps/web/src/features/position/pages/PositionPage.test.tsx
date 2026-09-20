import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as positionApi from "../api/positionApi";
import type { Position, PositionTemplate } from "../api/types";
import { PositionPage } from "./PositionPage";

vi.mock("../../../lib/countries", () => import("../../../test/countries"));
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
  attachDocument: vi.fn(),
  removeDocument: vi.fn(),
  saveDocument: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  updateProject: vi.fn(),
}));

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy Group",
  clientLogoUrl: null,
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
    locationCity: "Abu Dhabi",
    locationCountry: "United Arab Emirates",
    employmentType: "FULL_TIME_PERMANENT",
    seniority: "C_SUITE",
    responsibilities: ["Group P&L stewardship"],
    narrative: "A hands-on CFO.",
  },
  context: {
    mandateReason: "NEW_ROLE",
    businessDriver: null,
    strategicPriorities: [{ name: "Capital discipline", selected: false }],
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
    technicalShare: 60,
  },
  publication: { publishedAt: null, publishedBy: null },
  document: null,
};

const published: Position = {
  ...seeded,
  publication: { publishedAt: "2026-08-27T10:00:00Z", publishedBy: "Alok Kumar" },
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
];

/** What the compliance template redraws the brief into. */
const redrafted: Position = {
  ...seeded,
  details: { ...seeded.details, department: "Compliance", responsibilities: ["Group compliance framework"] },
  assessment: {
    criteria: [{ text: "Led compliance for a regulated entity", mode: "REQUIRED", fromBrief: true }],
    technical: [{ name: "Regulatory Framework & Licensing", description: null, weight: 100 }],
    behavioural: [{ name: "Independence & Objectivity", description: null, weight: 100 }],
    technicalShare: 50,
  },
};

const renderPage = (path = "/") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route path="/" element={<PositionPage />} />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const rail = () => screen.getByRole("complementary");
/** The arguments of a mock's most recent call — what the last autosave actually sent. */
const lastCall = (mocked: unknown): unknown[] =>
  (mocked as { mock: { calls: unknown[][] } }).mock.calls.at(-1) ?? [];

describe("PositionPage", () => {
  beforeEach(() => {
    // A vi.fn() keeps its call history across tests; only the calls this test makes may count.
    vi.clearAllMocks();
    vi.mocked(positionApi.getPosition).mockResolvedValue(seeded);
    vi.mocked(positionApi.listTemplates).mockResolvedValue(catalog);
  });

  describe("the rail and the step in the URL", () => {
    it("opens on the Role Brief and reads every step back in the rail", async () => {
      renderPage();

      expect(await screen.findByRole("heading", { name: "Role Brief" })).toBeInTheDocument();
      expect(within(rail()).getByText("Chief Financial Officer · Abu Dhabi")).toBeInTheDocument();
      expect(within(rail()).getByText("Group CEO · C-Suite")).toBeInTheDocument();
      expect(within(rail()).getByText("Awaiting package input")).toBeInTheDocument();
      expect(within(rail()).getByText("Technical 60% · Behavioural 40%")).toBeInTheDocument();
      expect(within(rail()).getByText("Not yet published")).toBeInTheDocument();
      expect(within(rail()).getByRole("link", { name: /Role Brief/ })).toHaveAttribute("aria-current", "page");
    });

    it("opens the step the URL names, and the rail's links walk between them", async () => {
      renderPage("/?step=compensation");
      const person = userEvent.setup();

      expect(await screen.findByRole("heading", { name: "Compensation Package" })).toBeInTheDocument();

      await person.click(within(rail()).getByRole("link", { name: /Assessment Criteria/ }));
      expect(screen.getByRole("heading", { name: "Assessment Criteria" })).toBeInTheDocument();
      expect(within(rail()).getByRole("link", { name: /Assessment Criteria/ })).toHaveAttribute("href", "/?step=assessment");
    });

    it("opens a published brief on its own review, for whoever comes back to it", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      renderPage();

      expect(await screen.findByRole("heading", { name: "Review & publish" })).toBeInTheDocument();
      expect(screen.getByText(/Position profile published by Alok Kumar/)).toBeInTheDocument();
      expect(within(rail()).getByText("Published 27 Aug 2026")).toBeInTheDocument();
    });

    it("shows a refusal rather than an empty brief when the read fails", async () => {
      vi.mocked(positionApi.getPosition).mockRejectedValue(new Error("403"));
      renderPage();

      expect(await screen.findByText("Couldn't load this brief")).toBeInTheDocument();
      expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
    });
  });

  describe("the Role Brief", () => {
    it("autosaves the details being edited, and only that section", async () => {
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      const city = await screen.findByRole("textbox", { name: "City" });
      await person.clear(city);
      await person.type(city, "Riyadh");

      await waitFor(() => expect(positionApi.putDetails).toHaveBeenCalled());
      expect(positionApi.putContext).not.toHaveBeenCalled();
      expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ locationCity: "Riyadh" });
    });

    it("takes the country from the served vocabulary, spelled as the catalog spells it", async () => {
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      const country = await screen.findByPlaceholderText("Country");
      await person.click(country);
      await person.keyboard("Saudi{Enter}");

      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ locationCountry: "Saudi Arabia" }),
      );
    });

    it("writes a chip's choice to the section it belongs to, and clears it on a second press", async () => {
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      vi.mocked(positionApi.putContext).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await person.click(await screen.findByRole("radio", { name: "Temporary" }));
      await waitFor(() => expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ employmentType: "TEMPORARY" }));

      await person.click(screen.getByRole("radio", { name: "Temporary" }));
      await waitFor(() => expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ employmentType: null }));

      // The reason for hire is the mandate context's, and a decision rather than typing.
      await person.click(screen.getByRole("radio", { name: "Succession plan" }));
      await waitFor(() => expect(lastCall(positionApi.putContext)[1]).toMatchObject({ mandateReason: "SUCCESSION" }));
    });

    it("offers a stored arrangement nobody offers as recorded rather than clearing it", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue({
        ...seeded,
        details: { ...seeded.details, employmentType: "RETAINED_ADVISORY" },
        // Six weeks is nothing on offer; ninety days would read as the three-month pill it equals.
        reporting: { ...seeded.reporting, noticeValue: 6, noticeUnit: "WEEKS" },
      });
      renderPage();

      expect(await screen.findByRole("radio", { name: "Advisory (as recorded)" })).toHaveAttribute("aria-checked", "true");
      expect(screen.getByRole("radio", { name: "6 weeks (as recorded)" })).toHaveAttribute("aria-checked", "true");
      expect(screen.queryByRole("radio", { name: "None" })).not.toBeInTheDocument();
    });

    it("plans a notice period in whole months through the reporting section", async () => {
      vi.mocked(positionApi.putReporting).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await person.click(await screen.findByRole("radio", { name: "3 months" }));

      await waitFor(() =>
        expect(lastCall(positionApi.putReporting)[1]).toMatchObject({ noticeValue: 3, noticeUnit: "MONTHS" }),
      );
    });

    it("writes the target start to the project, which is where the mandate keeps it", async () => {
      vi.mocked(projectsApi.updateProject).mockResolvedValue({ ...project, targetDate: "2026-12-01" });
      const { container } = renderPage();

      await screen.findByRole("heading", { name: "Role Brief" });
      const date = container.querySelector('input[type="date"]') as HTMLInputElement;
      fireEvent.change(date, { target: { value: "2026-12-01" } });

      await waitFor(() => expect(projectsApi.updateProject).toHaveBeenCalledWith("p1", { targetDate: "2026-12-01" }));
      expect(await screen.findByText("01 Dec 2026")).toBeInTheDocument();
      expect(positionApi.putReporting).not.toHaveBeenCalled();
    });

    it("adds and removes a responsibility", async () => {
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await person.type(await screen.findByRole("textbox", { name: "Add a responsibility" }), "Treasury{Enter}");
      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({
          responsibilities: ["Group P&L stewardship", "Treasury"],
        }),
      );

      await person.click(screen.getByRole("button", { name: "Remove Group P&L stewardship" }));
      await waitFor(() => expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ responsibilities: ["Treasury"] }));
    });

    it("edits the ideal profile in place", async () => {
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      expect(await screen.findByText("A hands-on CFO.")).toBeInTheDocument();
      await person.click(screen.getByRole("button", { name: "Edit" }));
      const narrative = screen.getByRole("textbox", { name: "Ideal profile" });
      await person.type(narrative, " Steady under a board.");

      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ narrative: "A hands-on CFO. Steady under a board." }),
      );
      await person.tab();
      expect(screen.getByText("A hands-on CFO. Steady under a board.")).toBeInTheDocument();
    });
  });

  describe("the role title and the template it suggests", () => {
    it("type-aheads the catalog and drafts the brief from the template picked", async () => {
      vi.mocked(positionApi.applyTemplate).mockResolvedValue(redrafted);
      vi.mocked(positionApi.putDetails).mockResolvedValue(redrafted);
      renderPage();
      const person = userEvent.setup();

      const title = await screen.findByRole("combobox", { name: "Role title" });
      await person.clear(title);
      await person.type(title, "compl");
      await person.click(screen.getByRole("option", { name: /Chief Compliance Officer/ }));

      await waitFor(() => expect(positionApi.applyTemplate).toHaveBeenCalledWith("p1", "t-cco"));
      // The title is the mandate's and travels through the ordinary details write, not the template.
      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ roleTitle: "Chief Compliance Officer" }),
      );
      expect(screen.getByText("Group compliance framework")).toBeInTheDocument();
    });

    it("stays a free-text title when the catalog cannot be read", async () => {
      vi.mocked(positionApi.listTemplates).mockRejectedValue(new Error("nope"));
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      const title = await screen.findByRole("combobox", { name: "Role title" });
      await person.type(title, " – Energy");

      expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({ roleTitle: "Chief Financial Officer – Energy" }),
      );
    });
  });

  describe("the position description", () => {
    const attached: Position = {
      ...seeded,
      document: { fileName: "CFO-brief.pdf", contentType: "application/pdf", fileSize: 798_720, uploadedAt: "2026-09-07T09:00:00Z" },
    };

    it("attaches a file and then shows it as the card, with no extract control", async () => {
      vi.mocked(positionApi.attachDocument).mockResolvedValue(attached);
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));

      expect(await screen.findByRole("button", { name: "CFO-brief.pdf" })).toBeInTheDocument();
      expect(screen.getByText("780 KB · added 07 Sept 2026")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Extract|Read from document/ })).not.toBeInTheDocument();
    });

    it("downloads and removes the attached file", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(attached);
      vi.mocked(positionApi.saveDocument).mockResolvedValue(undefined);
      vi.mocked(positionApi.removeDocument).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await person.click(await screen.findByRole("button", { name: "CFO-brief.pdf" }));
      expect(positionApi.saveDocument).toHaveBeenCalledWith("p1", "CFO-brief.pdf");

      await person.click(screen.getByRole("button", { name: "Remove" }));
      expect(await screen.findByText("Attach the position description")).toBeInTheDocument();
    });
  });

  describe("compensation", () => {
    it("states a bonus as a fixed amount and totals it into the package", async () => {
      const banded: Position = {
        ...seeded,
        compensation: { ...seeded.compensation, currency: "SAR", salaryMin: 32_000, salaryMax: 37_000, baseSalaryMode: "MONTHLY" },
      };
      vi.mocked(positionApi.getPosition).mockResolvedValue(banded);
      vi.mocked(positionApi.putCompensation).mockResolvedValue(banded);
      renderPage("/?step=compensation");
      const person = userEvent.setup();

      await person.click(await screen.findByRole("radio", { name: "Fixed amount" }));
      await waitFor(() => expect(lastCall(positionApi.putCompensation)[1]).toMatchObject({ bonusBasis: "FIXED_AMOUNT" }));

      await person.type(screen.getByRole("textbox", { name: "Bonus target" }), "150000");
      await waitFor(() => expect(lastCall(positionApi.putCompensation)[1]).toMatchObject({ bonusValue: 150_000 }));
      expect(screen.getByText("SAR 534,000 – SAR 594,000")).toBeInTheDocument();
    });

    it("adds and removes a benefit line at once", async () => {
      vi.mocked(positionApi.putCompensation).mockResolvedValue(seeded);
      renderPage("/?step=compensation");
      const person = userEvent.setup();

      // A `list` of presets makes the add row's input a combobox to assistive tech.
      await person.type(await screen.findByRole("combobox", { name: "New benefit name" }), "Housing allowance{Enter}");
      await waitFor(() =>
        expect(lastCall(positionApi.putCompensation)[1]).toMatchObject({
          benefits: [{ name: "Housing allowance", amount: null, frequency: "MONTHLY" }],
        }),
      );

      await person.click(screen.getByRole("button", { name: "Remove Housing allowance" }));
      await waitFor(() => expect(lastCall(positionApi.putCompensation)[1]).toMatchObject({ benefits: [] }));
    });
  });

  describe("assessment", () => {
    it("adds a criterion of the consultant's own and turns it into a tie-breaker", async () => {
      vi.mocked(positionApi.putCriteria).mockResolvedValue(seeded);
      renderPage("/?step=assessment");
      const person = userEvent.setup();

      await person.type(await screen.findByRole("textbox", { name: "Add a criterion" }), "Arabic language skills{Enter}");
      await waitFor(() =>
        expect(lastCall(positionApi.putCriteria)[1]).toEqual([
          { text: "Board reporting experience", mode: "REQUIRED", fromBrief: true },
          { text: "Arabic language skills", mode: "REQUIRED", fromBrief: false },
        ]),
      );

      const modes = screen.getByRole("radiogroup", { name: "Criterion 2 mode" });
      await person.click(within(modes).getByRole("radio", { name: "Preferred" }));
      await waitFor(() =>
        expect((lastCall(positionApi.putCriteria)[1] as { mode: string }[])[1].mode).toBe("PREFERRED"),
      );
      expect(screen.getByText("From brief")).toBeInTheDocument();
    });

    it("writes the split and both panels through one section write", async () => {
      vi.mocked(positionApi.putCompetencies).mockResolvedValue(seeded);
      renderPage("/?step=assessment");
      const person = userEvent.setup();

      const technicalShare = await screen.findByRole("textbox", { name: "Technical share" });
      await person.clear(technicalShare);
      await person.type(technicalShare, "70");

      await waitFor(() => expect(lastCall(positionApi.putCompetencies)[3]).toBe(70));
      expect(screen.getByRole("textbox", { name: "Behavioural share" })).toHaveValue("30");
      expect(within(rail()).getByText("Technical 70% · Behavioural 30%")).toBeInTheDocument();
    });

    it("adds a competency to the panel it was asked for and no other", async () => {
      vi.mocked(positionApi.putCompetencies).mockResolvedValue(seeded);
      renderPage("/?step=assessment");
      const person = userEvent.setup();

      const technical = await screen.findByRole("region", { name: "Technical competencies" });
      await person.click(within(technical).getByRole("button", { name: "+ Add competency" }));

      await waitFor(() => expect(lastCall(positionApi.putCompetencies)[1]).toHaveLength(3));
      const [, technicalSent, behaviouralSent, share] = lastCall(positionApi.putCompetencies);
      expect((technicalSent as unknown[]).at(-1)).toEqual({ name: "New competency", description: null, weight: 0 });
      expect(behaviouralSent).toHaveLength(1);
      expect(share).toBe(60);
    });

    it("rebalances the others when a weight is committed, and a locked row holds still", async () => {
      vi.mocked(positionApi.putCompetencies).mockResolvedValue(seeded);
      renderPage("/?step=assessment");
      const person = userEvent.setup();

      const controls = await screen.findByRole("textbox", { name: "Controls (row 2) weight" });
      await person.clear(controls);
      await person.type(controls, "30{Enter}");
      // The panel keeps totalling 100: what Controls gave up, Treasury took.
      expect(screen.getByRole("textbox", { name: "Treasury (row 1) weight" })).toHaveValue("70");

      await person.click(screen.getByRole("button", { name: "Lock Treasury (row 1)" }));
      expect(screen.getByRole("textbox", { name: "Treasury (row 1) weight" })).toBeDisabled();
      await person.click(screen.getByRole("button", { name: "Unlock Treasury (row 1)" }));
      expect(screen.getByRole("textbox", { name: "Treasury (row 1) weight" })).toBeEnabled();
    });

    it("lets the last competency in a panel be removed", async () => {
      vi.mocked(positionApi.putCompetencies).mockResolvedValue(seeded);
      renderPage("/?step=assessment");
      const person = userEvent.setup();

      await person.click(await screen.findByRole("button", { name: "Remove Strategic Leadership (row 1)" }));

      await waitFor(() => expect(lastCall(positionApi.putCompetencies)[2]).toEqual([]));
      const behavioural = screen.getByRole("region", { name: "Behavioural competencies" });
      expect(within(behavioural).getByText("No competencies yet.")).toBeInTheDocument();
    });
  });

  describe("review and publish", () => {
    it("reads every section back with whether it is done, and the way in", async () => {
      renderPage("/?step=review");
      const person = userEvent.setup();

      const brief = await screen.findByRole("region", { name: "Role Brief" });
      expect(within(brief).getByText("Complete")).toBeInTheDocument();
      expect(within(brief).getByText("Abu Dhabi, United Arab Emirates")).toBeInTheDocument();

      const compensation = screen.getByRole("region", { name: "Compensation" });
      expect(within(compensation).getByText("Needs attention")).toBeInTheDocument();
      expect(within(compensation).getByText("No base salary band yet.")).toBeInTheDocument();

      const assessment = screen.getByRole("region", { name: "Assessment Criteria" });
      expect(within(assessment).getByText("60% weighting")).toBeInTheDocument();
      expect(within(assessment).getByText("1 rule active")).toBeInTheDocument();

      expect(screen.getByText("2 of 5 sections complete")).toBeInTheDocument();

      await person.click(within(compensation).getByRole("link", { name: /Edit section/ }));
      expect(screen.getByRole("heading", { name: "Compensation Package" })).toBeInTheDocument();
    });

    it("publishes from the rail and shows the brief as published, still editable", async () => {
      vi.mocked(positionApi.publish).mockResolvedValue(published);
      renderPage();
      const person = userEvent.setup();

      await screen.findByRole("heading", { name: "Role Brief" });
      await person.click(within(rail()).getByRole("button", { name: "Publish profile" }));

      expect(await within(rail()).findByText("Published 27 Aug 2026")).toBeInTheDocument();
      expect(within(rail()).getByRole("button", { name: "Publish changes" })).toBeInTheDocument();
      // Publishing is a stamp, not a lock: the fields keep accepting input.
      expect(screen.getByRole("combobox", { name: "Role title" })).toBeEnabled();
    });

    it("withdraws a publication from the review", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      vi.mocked(positionApi.withdrawPublication).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await person.click(await screen.findByRole("button", { name: "Withdraw publication" }));

      expect(await within(rail()).findByText("Not yet published")).toBeInTheDocument();
    });
  });
});
