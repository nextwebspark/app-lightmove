import { sampleProgress } from "../../../test/sampleProject";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as positionApi from "../api/positionApi";
import type { Position, PositionExtraction, PositionTemplate } from "../api/types";
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
  extractDetails: vi.fn(),
  extractContext: vi.fn(),
  extractReporting: vi.fn(),
  extractAssessment: vi.fn(),
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
  projectType: "MAPPING",
  startDate: "2026-07-01",
  mappingTargetDate: null,
  shortlistTargetDate: null,
  progress: sampleProgress(),
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
    responsibilities: [{ text: "Group P&L stewardship", source: "MANUAL" }],
    narrative: "A hands-on CFO.",
    fieldSources: {},
  },
  context: {
    mandateReason: "NEW_ROLE",
    businessDriver: null,
    strategicPriorities: [{ name: "Capital discipline", selected: false }],
    confidential: false,
    internalContext: null,
    fieldSources: {},
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
    fieldSources: {},
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
    criteria: [{ text: "Board reporting experience", mode: "REQUIRED", source: "TEMPLATE" }],
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
  details: {
    ...seeded.details,
    department: "Compliance",
    responsibilities: [{ text: "Group compliance framework", source: "TEMPLATE" }],
  },
  assessment: {
    criteria: [{ text: "Led compliance for a regulated entity", mode: "REQUIRED", source: "TEMPLATE" }],
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
            <Route path="/projects/:projectId/strategy" element={<h1>Strategy</h1>} />
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
    it("opens on the Role Brief, with the five steps in the rail and that one current", async () => {
      renderPage();

      expect(await screen.findByRole("heading", { name: "Role Brief" })).toBeInTheDocument();
      expect(within(rail()).getAllByRole("link").map((link) => link.textContent)).toEqual([
        "Role Brief",
        "Reporting",
        "Compensation",
        "Assessment Criteria",
        "Review & Publish",
      ]);
      expect(within(rail()).getByRole("link", { name: "Role Brief" })).toHaveAttribute("aria-current", "page");
      expect(within(rail()).getByRole("button", { name: "Publish profile" })).toBeInTheDocument();
    });

    it("opens the step the URL names, and the rail's links walk between them", async () => {
      renderPage("/?step=compensation");
      const person = userEvent.setup();

      expect(await screen.findByRole("heading", { name: "Compensation Package" })).toBeInTheDocument();

      await person.click(within(rail()).getByRole("link", { name: /Assessment Criteria/ }));
      expect(screen.getByRole("heading", { name: "Assessment Criteria" })).toBeInTheDocument();
      expect(within(rail()).getByRole("link", { name: /Assessment Criteria/ })).toHaveAttribute("href", "/?step=assessment");
    });

    it("walks the steps from the foot of each one, forwards and back", async () => {
      renderPage();
      const person = userEvent.setup();

      // The first step has nothing behind it, and the last nothing ahead.
      await screen.findByRole("heading", { name: "Role Brief" });
      expect(screen.queryByRole("link", { name: /^Back to/ })).not.toBeInTheDocument();

      await person.click(screen.getByRole("link", { name: "Next: Reporting" }));
      expect(screen.getByRole("heading", { name: "Reporting Structure" })).toBeInTheDocument();

      await person.click(screen.getByRole("link", { name: "Back to Role Brief" }));
      expect(screen.getByRole("heading", { name: "Role Brief" })).toBeInTheDocument();
    });

    it("offers no next step past the review, and nothing onward until it is published", async () => {
      renderPage("/?step=review");

      expect(await screen.findByRole("link", { name: "Back to Assessment Criteria" })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /^Next:/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Move to Strategy" })).not.toBeInTheDocument();
    });

    it("opens a published brief on its own review, reading back rather than offering edits", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      renderPage();

      expect(await screen.findByRole("heading", { name: "Review & publish" })).toBeInTheDocument();
      expect(screen.getByText(/Position profile published by Alok Kumar · 27 Aug 2026/)).toBeInTheDocument();
      expect(within(rail()).getByRole("button", { name: "Edit position" })).toBeInTheDocument();
      // Saving a draft of the published brief would record nothing, and no section offers a way in.
      expect(within(rail()).getByRole("button", { name: "Save draft" })).toBeDisabled();
      expect(screen.queryByRole("link", { name: /Edit section/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Withdraw publication" })).not.toBeInTheDocument();
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
          responsibilities: [
            { text: "Group P&L stewardship", source: "MANUAL" },
            { text: "Treasury", source: "MANUAL" },
          ],
        }),
      );

      await person.click(screen.getByRole("button", { name: "Remove Group P&L stewardship" }));
      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({
          responsibilities: [{ text: "Treasury", source: "MANUAL" }],
        }),
      );
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
    const emptyExtraction: PositionExtraction = {
      extractionSource: "none",
      fields: [],
      suggestedTemplate: null,
      usualDirectReports: null,
    };
    const noReading = () => {
      vi.mocked(positionApi.extractDetails).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractContext).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractReporting).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractAssessment).mockResolvedValue(emptyExtraction);
    };

    it("attaches a file and then shows it as the card, with Extract with AI to read it again", async () => {
      vi.mocked(positionApi.attachDocument).mockResolvedValue(attached);
      noReading();
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));

      expect(await screen.findByRole("button", { name: "CFO-brief.pdf" })).toBeInTheDocument();
      expect(screen.getByText("780 KB · added 07 Sept 2026")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Extract with AI/ })).toBeInTheDocument();
      // Attaching already read it once — the four section calls fire without a second press.
      await waitFor(() => expect(positionApi.extractDetails).toHaveBeenCalledTimes(1));
      expect(positionApi.extractContext).toHaveBeenCalledTimes(1);
      expect(positionApi.extractReporting).toHaveBeenCalledTimes(1);
      expect(positionApi.extractAssessment).toHaveBeenCalledTimes(1);
    });

    it("reads again from the Reporting and Assessment step headers, without walking back to step one", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(attached);
      vi.mocked(positionApi.putReporting).mockResolvedValue(attached);
      noReading();
      renderPage();
      const person = userEvent.setup();

      // A plain read of an already-attached brief reads nothing, so the count below is this press alone.
      await screen.findByRole("button", { name: "CFO-brief.pdf" });
      await person.click(within(rail()).getByRole("link", { name: "Reporting" }));
      await person.click(await screen.findByRole("button", { name: /Read from document/ }));
      await waitFor(() => expect(positionApi.extractReporting).toHaveBeenCalledTimes(1));
      // One press reads every section, exactly as Extract with AI does — the screens share one reading.
      expect(positionApi.extractDetails).toHaveBeenCalledTimes(1);

      await person.click(within(rail()).getByRole("link", { name: "Assessment Criteria" }));
      expect(await screen.findByRole("button", { name: /Read from document/ })).toBeInTheDocument();
    });

    it("offers no read control with nothing attached, nor on Compensation once there is", async () => {
      noReading();
      renderPage();
      const person = userEvent.setup();

      await screen.findByText("Attach the position description");
      await person.click(within(rail()).getByRole("link", { name: "Reporting" }));
      expect(await screen.findByRole("heading", { name: "Reporting Structure" })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Read from document/ })).not.toBeInTheDocument();

      vi.mocked(positionApi.getPosition).mockResolvedValue(attached);
      await person.click(within(rail()).getByRole("link", { name: "Compensation" }));
      expect(screen.queryByRole("button", { name: /Read from document/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Extract with AI/ })).not.toBeInTheDocument();
    });

    it("downloads and removes the attached file", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(attached);
      vi.mocked(positionApi.saveDocument).mockResolvedValue(undefined);
      vi.mocked(positionApi.removeDocument).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await person.click(await screen.findByRole("button", { name: "CFO-brief.pdf" }));
      expect(positionApi.saveDocument).toHaveBeenCalledWith("p1", "CFO-brief.pdf");
      // Reading a document nobody attached this session must never happen off a plain read of the brief.
      expect(positionApi.extractDetails).not.toHaveBeenCalled();

      await person.click(screen.getByRole("button", { name: "Remove" }));
      expect(await screen.findByText("Attach the position description")).toBeInTheDocument();
    });

    it("reads a document on attach, fills what nobody typed over, and leaves a MANUAL field alone", async () => {
      const withManualDepartment: Position = {
        ...seeded,
        details: { ...seeded.details, fieldSources: { department: "MANUAL" } },
      };
      vi.mocked(positionApi.getPosition).mockResolvedValue(withManualDepartment);
      vi.mocked(positionApi.attachDocument).mockResolvedValue({ ...withManualDepartment, document: attached.document });
      vi.mocked(positionApi.extractDetails).mockResolvedValue({
        extractionSource: "model",
        fields: [
          { id: 1, fieldKey: "department", value: "Group Treasury", confidence: "high", snippet: "leads Group Treasury", origin: "document" },
          { id: 2, fieldKey: "locationCity", value: "Dubai", confidence: "medium", snippet: "based in Dubai", origin: "document" },
        ],
        suggestedTemplate: null,
        usualDirectReports: null,
      });
      vi.mocked(positionApi.extractContext).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractReporting).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractAssessment).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.putDetails).mockResolvedValue(withManualDepartment);
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));

      await waitFor(() => expect(screen.getByRole("textbox", { name: "City" })).toHaveValue("Dubai"));
      await waitFor(() => expect(positionApi.putDetails).toHaveBeenCalled());
      expect(lastCall(positionApi.putDetails)[1]).toMatchObject({
        department: "Group Finance",
        locationCity: "Dubai",
        fieldSources: expect.objectContaining({ department: "MANUAL", locationCity: "DOCUMENT" }),
      });
      // The department stayed MANUAL, so only the city counts toward the strip's own receipt.
      expect(await screen.findByText("1 field")).toBeInTheDocument();
    });

    it("typing over a document-filled field makes it MANUAL, so a later autosave never claims DOCUMENT for it", async () => {
      vi.mocked(positionApi.attachDocument).mockResolvedValue(attached);
      vi.mocked(positionApi.extractDetails).mockResolvedValue({
        extractionSource: "model",
        fields: [{ id: 1, fieldKey: "locationCity", value: "Dubai", confidence: "medium", snippet: "based in Dubai", origin: "document" }],
        suggestedTemplate: null,
        usualDirectReports: null,
      });
      vi.mocked(positionApi.extractContext).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractReporting).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractAssessment).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));
      await waitFor(() => expect(screen.getByRole("textbox", { name: "City" })).toHaveValue("Dubai"));
      expect(screen.getByRole("button", { name: "Read from the document" })).toBeInTheDocument();

      const city = screen.getByRole("textbox", { name: "City" });
      await person.clear(city);
      await person.type(city, "Abu Dhabi");

      await waitFor(() =>
        expect(lastCall(positionApi.putDetails)[1]).toMatchObject({
          locationCity: "Abu Dhabi",
          fieldSources: expect.objectContaining({ locationCity: "MANUAL" }),
        }),
      );
      // The correction is the person's own now — the sparkle (and the Undo it offers) is gone.
      expect(screen.queryByRole("button", { name: "Read from the document" })).not.toBeInTheDocument();
    });

    it("undoes a field a reading filled, restoring what was there before", async () => {
      vi.mocked(positionApi.attachDocument).mockResolvedValue(attached);
      vi.mocked(positionApi.extractDetails).mockResolvedValue({
        extractionSource: "model",
        fields: [{ id: 1, fieldKey: "locationCity", value: "Dubai", confidence: "medium", snippet: "based in Dubai", origin: "document" }],
        suggestedTemplate: null,
        usualDirectReports: null,
      });
      vi.mocked(positionApi.extractContext).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractReporting).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractAssessment).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.putDetails).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));
      await waitFor(() => expect(screen.getByRole("textbox", { name: "City" })).toHaveValue("Dubai"));

      // fireEvent rather than userEvent.click: two clicks in a row make userEvent move the simulated
      // pointer from the marker to the popover, which fires the marker's own leave/blur before the
      // popover's click lands — real only for a mouse path crossing two separate elements, not for a
      // press-and-release in place.
      fireEvent.focus(screen.getByRole("button", { name: "Read from the document" }));
      fireEvent.click(await screen.findByRole("button", { name: "Undo" }));

      await waitFor(() => expect(screen.getByRole("textbox", { name: "City" })).toHaveValue("Abu Dhabi"));
    });

    it("offers the matched template's usual direct reports as suggested seats after a reading", async () => {
      vi.mocked(positionApi.attachDocument).mockResolvedValue(attached);
      vi.mocked(positionApi.extractDetails).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractContext).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractReporting).mockResolvedValue({
        extractionSource: "none",
        fields: [],
        suggestedTemplate: null,
        usualDirectReports: ["Head of Treasury"],
      });
      vi.mocked(positionApi.extractAssessment).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.putReporting).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));
      await waitFor(() => expect(positionApi.extractReporting).toHaveBeenCalled());

      await person.click(within(rail()).getByRole("link", { name: "Reporting" }));
      await person.click(await screen.findByRole("button", { name: /Head of Treasury/ }));

      await waitFor(() =>
        expect((lastCall(positionApi.putReporting)[1] as { orgChart: unknown[] }).orgChart).toEqual(
          expect.arrayContaining([expect.objectContaining({ title: "Head of Treasury", source: "MANUAL" })]),
        ),
      );
    });

    it("offers to draft from a template the document reads like, and re-reads once applied", async () => {
      vi.mocked(positionApi.attachDocument).mockResolvedValue(attached);
      vi.mocked(positionApi.extractDetails).mockResolvedValue({
        ...emptyExtraction,
        suggestedTemplate: catalog[1],
      });
      vi.mocked(positionApi.extractContext).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractReporting).mockResolvedValue(emptyExtraction);
      vi.mocked(positionApi.extractAssessment).mockResolvedValue(emptyExtraction);
      // The document stays attached across a template redraft — real API responses carry it; the shared
      // `redrafted` fixture doesn't, since the test it was built for never attaches one first.
      vi.mocked(positionApi.applyTemplate).mockResolvedValue({ ...redrafted, document: attached.document });
      renderPage();
      const person = userEvent.setup();

      const input = await screen.findByLabelText("Position description file");
      await person.upload(input, new File(["%PDF-1.4"], "CFO-brief.pdf", { type: "application/pdf" }));

      expect(await screen.findByText(/Chief Compliance Officer/)).toBeInTheDocument();
      await person.click(screen.getByRole("button", { name: "Apply" }));

      await waitFor(() => expect(positionApi.applyTemplate).toHaveBeenCalledWith("p1", "t-cco"));
      // Re-reads after applying: the second read is on top of the one attaching already fired.
      await waitFor(() => expect(positionApi.extractDetails).toHaveBeenCalledTimes(2));
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
          { text: "Board reporting experience", mode: "REQUIRED", source: "TEMPLATE" },
          { text: "Arabic language skills", mode: "REQUIRED", source: "MANUAL" },
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
    });

    it("adds a competency to the panel it was asked for and no other", async () => {
      vi.mocked(positionApi.putCompetencies).mockResolvedValue(seeded);
      renderPage("/?step=assessment");
      const person = userEvent.setup();

      const technical = await screen.findByRole("region", { name: "Technical competencies" });
      await person.click(within(technical).getByRole("button", { name: "+ Add competency" }));

      await waitFor(() => expect(lastCall(positionApi.putCompetencies)[1]).toHaveLength(3));
      const [, technicalSent, behaviouralSent, share] = lastCall(positionApi.putCompetencies);
      expect((technicalSent as unknown[]).at(-1)).toEqual({
        name: "New competency",
        description: null,
        weight: 0,
        source: "MANUAL",
      });
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

      expect(await within(rail()).findByRole("button", { name: "Publish changes" })).toBeInTheDocument();
      // Publishing is a stamp, not a lock: the fields keep accepting input.
      expect(screen.getByRole("combobox", { name: "Role title" })).toBeEnabled();
    });

    it("reopens a published brief, and publishing the changes closes it back up", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      renderPage();
      const person = userEvent.setup();

      await screen.findByRole("heading", { name: "Review & publish" });
      await person.click(within(rail()).getByRole("button", { name: "Edit position" }));

      expect(within(rail()).getByRole("button", { name: "Publish changes" })).toBeInTheDocument();
      expect(within(rail()).getByRole("button", { name: "Save draft" })).toBeEnabled();
      expect(screen.getAllByRole("link", { name: /Edit section/ })).toHaveLength(4);
      // Leading on belongs to the foot of the last page, changes in flight or not.
      expect(screen.getByRole("button", { name: "Move to Strategy" })).toBeInTheDocument();

      // Saying the brief is ready again is the way out of editing it, not a second way to save.
      await person.click(within(rail()).getByRole("button", { name: "Publish changes" }));

      expect(await within(rail()).findByRole("button", { name: "Edit position" })).toBeInTheDocument();
      expect(within(rail()).getByRole("button", { name: "Save draft" })).toBeDisabled();
      expect(screen.queryByRole("link", { name: /Edit section/ })).not.toBeInTheDocument();
    });

    it("counts landing on a live step as reopening it, so the rail stops claiming a read-back", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      renderPage("/?step=compensation");

      expect(await screen.findByRole("heading", { name: "Compensation Package" })).toBeInTheDocument();
      expect(within(rail()).getByRole("button", { name: "Publish changes" })).toBeInTheDocument();
    });

    it("sends a published brief on to the mandate's own market", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      renderPage();
      const person = userEvent.setup();

      await person.click(await screen.findByRole("button", { name: "Move to Strategy" }));

      expect(await screen.findByRole("heading", { name: "Strategy" })).toBeInTheDocument();
    });

    it("withdraws a publication once the brief has been reopened", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue(published);
      vi.mocked(positionApi.withdrawPublication).mockResolvedValue(seeded);
      renderPage();
      const person = userEvent.setup();

      await screen.findByRole("heading", { name: "Review & publish" });
      await person.click(within(rail()).getByRole("button", { name: "Edit position" }));
      await person.click(screen.getByRole("button", { name: "Withdraw publication" }));

      expect(await within(rail()).findByRole("button", { name: "Publish profile" })).toBeInTheDocument();
    });
  });
});
