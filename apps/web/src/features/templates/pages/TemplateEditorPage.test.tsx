import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as templateApi from "../api/templateAdminApi";
import type { OrgNode } from "../../position/api/types";
import type { TemplateDetail } from "../api/types";
import { TemplateEditorPage } from "./TemplateEditorPage";

// React Flow needs a layout engine jsdom does not have; the canvas is the brief's own and tested there.
vi.mock("../../position/components/OrgChartCanvas", () => ({
  OrgChartCanvas: ({ chart, onChange }: { chart: OrgNode[]; onChange: (chart: OrgNode[]) => void }) => (
    <ul aria-label="Org chart">
      {chart.map((seat) => (
        <li key={seat.nodeId}>{seat.mandateSeat ? "This role" : seat.title}</li>
      ))}
      <li>
        <button
          type="button"
          onClick={() =>
            onChange([
              ...chart,
              {
                nodeId: "new-seat",
                parentNodeId: "role",
                title: "Head of Tax",
                name: null,
                mandateSeat: false,
                canvasX: null,
                canvasY: null,
              },
            ])
          }
        >
          Add Head of Tax
        </button>
      </li>
    </ul>
  ),
}));

vi.mock("../api/templateAdminApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/templateAdminApi")>()),
  getTemplate: vi.fn(),
  saveTemplate: vi.fn(),
  createTemplate: vi.fn(),
  removeTemplate: vi.fn(),
  setTemplateHidden: vi.fn(),
  setTemplateActive: vi.fn(),
}));

const cfo = (overrides: Partial<TemplateDetail> = {}): TemplateDetail => ({
  code: "chief-financial-officer",
  title: "Chief Financial Officer",
  discipline: "FINANCE",
  seniority: "C_SUITE",
  summary: "Group finance.",
  origin: "LIBRARY",
  active: true,
  fallback: false,
  libraryChangedSinceCustomised: false,
  keywords: ["cfo"],
  customisedByWorkspaces: null,
  version: 7,
  revisedAt: "2026-09-02T10:00:00Z",
  revisedByName: null,
  body: {
    employmentType: "FULL_TIME_PERMANENT",
    mandateReason: null,
    confidential: null,
    noticeValue: 3,
    noticeUnit: "MONTHS",
    responsibilities: ["Group P&L stewardship"],
    narrative: "Sits on the executive committee.",
    orgChart: [
      { id: "ceo", parentId: null, title: "Group CEO", mandateSeat: false },
      { id: "role", parentId: "ceo", title: null, mandateSeat: true },
    ],
    currency: "USD",
    baseSalaryMode: "ANNUAL",
    bonusValue: null,
    bonusBasis: null,
    incentiveType: null,
    incentiveVesting: null,
    benefits: [],
    criteria: [{ text: "Board exposure", mode: "REQUIRED" }],
    competencies: [
      { panel: "TECHNICAL", name: "Reporting", description: null, weight: 100 },
      { panel: "BEHAVIOURAL", name: "Leadership", description: null, weight: 100 },
    ],
    technicalShare: 50,
  },
  ...overrides,
});

const renderEditor = () =>
  render(
    <MemoryRouter initialEntries={["/settings/templates/chief-financial-officer"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/settings/templates" element={<p>Templates list</p>} />
            <Route path="/settings/templates/:code" element={<TemplateEditorPage scope="workspace" />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const saveButtons = (name: string) => screen.getAllByRole("button", { name });

describe("TemplateEditorPage — a firm editing a template", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("keeps Save off until something changes", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());

    renderEditor();
    await screen.findByLabelText("Ideal profile");

    saveButtons("Save as my firm's copy").forEach((button) => expect(button).toBeDisabled());
  });

  it("saving a library template takes the firm's copy, quoting the version it was opened at", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());
    vi.mocked(templateApi.saveTemplate).mockResolvedValue(cfo({ origin: "CUSTOMISED", version: 8 }));

    renderEditor();
    const profile = await screen.findByLabelText("Ideal profile");
    await userEvent.clear(profile);
    await userEvent.type(profile, "Runs group finance.");
    await userEvent.click(saveButtons("Save as my firm's copy")[0]);

    expect(templateApi.saveTemplate).toHaveBeenCalledWith(
      "workspace",
      "chief-financial-officer",
      expect.objectContaining({
        version: 7,
        body: expect.objectContaining({ narrative: "Runs group finance." }),
      }),
    );
  });

  it("a panel that does not total 100 keeps Save off, and says why", async () => {
    const unbalanced = cfo();
    unbalanced.body.competencies = [{ panel: "TECHNICAL", name: "Reporting", description: null, weight: 90 }];
    vi.mocked(templateApi.getTemplate).mockResolvedValue(unbalanced);

    renderEditor();
    await userEvent.type(await screen.findByLabelText("Title"), " (Group)");

    expect(screen.getByText("Technical competency weights must total 100.")).toBeInTheDocument();
    saveButtons("Save as my firm's copy").forEach((button) => expect(button).toBeDisabled());
  });

  it("does not overwrite a save somebody else made first — Reload replaces the draft with theirs", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());
    vi.mocked(templateApi.saveTemplate).mockRejectedValue(
      new ApiRequestError({ code: "TEMPLATE_STALE", detail: "", status: 409, correlationId: "c" }),
    );

    renderEditor();
    await userEvent.type(await screen.findByLabelText("Ideal profile"), " And treasury.");
    await userEvent.click(saveButtons("Save as my firm's copy")[0]);
    expect(await screen.findByText(/Someone saved this template after you opened it/)).toBeInTheDocument();

    vi.mocked(templateApi.getTemplate).mockResolvedValue(
      cfo({ origin: "CUSTOMISED", version: 8, body: { ...cfo().body, narrative: "Their Finance" } }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Reload" }));

    expect(await screen.findByDisplayValue("Their Finance")).toBeInTheDocument();
    expect(screen.queryByText(/Someone saved this template after you opened it/)).not.toBeInTheDocument();
  });

  it("follows the brief's own steps, in the brief's order", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());

    renderEditor();
    await screen.findByLabelText("Ideal profile");

    const sections = [
      "Identity & matching",
      "Role Brief",
      "Reporting Structure",
      "Compensation Package",
      "Assessment Criteria",
    ];
    const drawn = screen.getAllByRole("region").map((section) => section.getAttribute("aria-label") ?? "");
    expect(drawn.filter((label) => sections.includes(label))).toEqual(sections);
  });

  it("drafts a reason for hire, a confidentiality level, a new seat and the split — and sends each back", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());
    vi.mocked(templateApi.saveTemplate).mockResolvedValue(cfo({ origin: "CUSTOMISED", version: 8 }));

    renderEditor();
    await userEvent.click(await screen.findByRole("radio", { name: "Succession plan" }));
    await userEvent.click(screen.getByRole("radio", { name: "Confidential" }));
    await userEvent.click(screen.getByRole("button", { name: "Add Head of Tax" }));
    const technical = screen.getByRole("textbox", { name: "Technical share" });
    await userEvent.clear(technical);
    await userEvent.type(technical, "70");
    await userEvent.click(saveButtons("Save as my firm's copy")[0]);

    expect(templateApi.saveTemplate).toHaveBeenCalledWith(
      "workspace",
      "chief-financial-officer",
      expect.objectContaining({
        body: expect.objectContaining({
          mandateReason: "SUCCESSION",
          confidential: true,
          technicalShare: 70,
          orgChart: [
            { id: "ceo", parentId: null, title: "Group CEO", mandateSeat: false },
            { id: "role", parentId: "ceo", title: null, mandateSeat: true },
            { id: "new-seat", parentId: "role", title: "Head of Tax", mandateSeat: false },
          ],
        }),
      }),
    );
  });

  it("offers the brief's four notice periods, and keeps one written outside them as recorded", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(
      cfo({ body: { ...cfo().body, noticeValue: 6, noticeUnit: "WEEKS" } }),
    );

    renderEditor();
    const notice = await screen.findByRole("radiogroup", { name: "Notice period" });

    expect(Array.from(notice.querySelectorAll("button")).map((chip) => chip.textContent)).toEqual([
      "1 month",
      "2 months",
      "3 months",
      "6 months",
      "6 weeks (as recorded)",
    ]);
    expect(screen.getByRole("radio", { name: "6 weeks (as recorded)" })).toHaveAttribute("aria-checked", "true");
  });

  it("tells a firm the library has moved past its copy, and offers the reset", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(
      cfo({ origin: "CUSTOMISED", libraryChangedSinceCustomised: true, revisedByName: "Yara Haddad" }),
    );

    renderEditor();

    expect(await screen.findByText(/has updated the library version since you customised it/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Reset to library" }).length).toBeGreaterThan(0);
    expect(screen.getByText(/last saved by Yara Haddad/)).toBeInTheDocument();
  });
});
