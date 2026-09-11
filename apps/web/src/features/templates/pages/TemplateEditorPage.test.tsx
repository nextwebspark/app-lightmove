import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as templateApi from "../api/templateAdminApi";
import type { TemplateDetail } from "../api/types";
import { TemplateEditorPage } from "./TemplateEditorPage";

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
    department: "Finance",
    employmentType: "FULL_TIME_PERMANENT",
    narrative: null,
    responsibilities: ["Group P&L stewardship"],
    reportsTo: "Group CEO",
    directReports: [],
    strategicPriorities: [],
    noticeValue: 3,
    noticeUnit: "MONTHS",
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
    await screen.findByLabelText("Department");

    saveButtons("Save as my firm's copy").forEach((button) => expect(button).toBeDisabled());
  });

  it("saving a library template takes the firm's copy, quoting the version it was opened at", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());
    vi.mocked(templateApi.saveTemplate).mockResolvedValue(cfo({ origin: "CUSTOMISED", version: 8 }));

    renderEditor();
    const department = await screen.findByLabelText("Department");
    await userEvent.clear(department);
    await userEvent.type(department, "Group Finance");
    await userEvent.click(saveButtons("Save as my firm's copy")[0]);

    expect(templateApi.saveTemplate).toHaveBeenCalledWith(
      "workspace",
      "chief-financial-officer",
      expect.objectContaining({
        version: 7,
        body: expect.objectContaining({ department: "Group Finance" }),
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

  it("does not overwrite a save somebody else made first — it offers to reload", async () => {
    vi.mocked(templateApi.getTemplate).mockResolvedValue(cfo());
    vi.mocked(templateApi.saveTemplate).mockRejectedValue(
      new ApiRequestError({ code: "TEMPLATE_STALE", detail: "", status: 409, correlationId: "c" }),
    );

    renderEditor();
    await userEvent.type(await screen.findByLabelText("Department"), " & Treasury");
    await userEvent.click(saveButtons("Save as my firm's copy")[0]);

    expect(await screen.findByText(/Someone saved this template after you opened it/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reload" })).toBeInTheDocument();
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
