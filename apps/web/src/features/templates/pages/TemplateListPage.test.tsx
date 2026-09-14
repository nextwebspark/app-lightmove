import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as templateApi from "../api/templateAdminApi";
import type { TemplateDetail, TemplateOverview, TemplateScope } from "../api/types";
import { TemplateListPage } from "./TemplateListPage";

vi.mock("../api/templateAdminApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../api/templateAdminApi")>()),
  listTemplates: vi.fn(),
  setTemplateHidden: vi.fn(),
  setTemplateActive: vi.fn(),
}));

const row = (overrides: Partial<TemplateOverview>): TemplateOverview => ({
  code: "chief-executive-officer",
  title: "Chief Executive Officer",
  discipline: "EXECUTIVE",
  seniority: "C_SUITE",
  summary: null,
  origin: "LIBRARY",
  active: true,
  fallback: false,
  libraryChangedSinceCustomised: false,
  keywords: ["ceo"],
  customisedByWorkspaces: null,
  revisedAt: "2026-09-02T10:00:00Z",
  revisedByName: null,
  ...overrides,
});

const generic = (overrides: Partial<TemplateOverview> = {}) =>
  row({ code: "generic-executive", title: "Senior Executive (generic)", seniority: "N_MINUS_1", fallback: true, ...overrides });

const renderPage = (scope: TemplateScope) =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <TemplateListPage scope={scope} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

// jsdom applies no CSS, so the phone cards and the grid are both in the document.
const grid = () => screen.getByRole("table", { name: "Templates" });

describe("TemplateListPage — a firm's templates", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it("says where each template came from, and which copies the library has moved past", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([
      row({ code: "group-treasury-lead", title: "Group Treasury Lead", discipline: "FINANCE", origin: "OWN", revisedByName: "Alok Kumar" }),
      row({
        code: "chief-financial-officer",
        title: "Chief Financial Officer",
        discipline: "FINANCE",
        origin: "CUSTOMISED",
        libraryChangedSinceCustomised: true,
      }),
      row({ code: "chief-risk-officer", title: "Chief Risk Officer", discipline: "GOVERNANCE", origin: "HIDDEN" }),
      generic(),
    ]);

    renderPage("workspace");

    const table = grid();
    expect(await within(table).findByText("Your own")).toBeInTheDocument();
    expect(within(table).getByText("Customised")).toBeInTheDocument();
    expect(within(table).getByText("Library updated")).toBeInTheDocument();
    expect(within(table).getByText("Hidden")).toBeInTheDocument();
    expect(within(table).getByText("Alok Kumar")).toBeInTheDocument();
    expect(within(table).queryByText("Firm copies")).not.toBeInTheDocument();
  });

  it("narrows the grid by search, matching keywords as well as titles", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([
      row({ code: "group-treasury-lead", title: "Group Treasury Lead", discipline: "FINANCE", origin: "OWN", keywords: ["treasury"] }),
      row({ code: "chief-risk-officer", title: "Chief Risk Officer", discipline: "GOVERNANCE", origin: "HIDDEN", keywords: ["cro"] }),
      row({}),
    ]);

    renderPage("workspace");
    await within(grid()).findByText("Group Treasury Lead");

    await userEvent.type(screen.getByRole("textbox", { name: /Search/ }), "treasury");
    expect(within(grid()).getByText("Group Treasury Lead")).toBeInTheDocument();
    expect(within(grid()).queryByText("Chief Executive Officer")).not.toBeInTheDocument();

    await userEvent.clear(screen.getByRole("textbox", { name: /Search/ }));
    await userEvent.type(screen.getByRole("textbox", { name: /Search/ }), "ceo");
    expect(within(grid()).getByText("Chief Executive Officer")).toBeInTheDocument();
    expect(within(grid()).queryByText("Chief Risk Officer")).not.toBeInTheDocument();
  });

  it("hides a library template from the firm, and never offers to hide the fallback", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([row({}), generic()]);
    vi.mocked(templateApi.setTemplateHidden).mockResolvedValue({} as TemplateDetail);

    renderPage("workspace");
    await within(grid()).findByText("Chief Executive Officer");
    await userEvent.click(within(grid()).getByRole("button", { name: "Hide Chief Executive Officer" }));

    expect(templateApi.setTemplateHidden).toHaveBeenCalledWith("chief-executive-officer", true);
    expect(screen.queryByRole("button", { name: "Hide Senior Executive (generic)" })).not.toBeInTheDocument();
  });

  it("says the list could not be read, rather than that there are no templates", async () => {
    vi.mocked(templateApi.listTemplates).mockRejectedValue(
      new ApiRequestError({ code: "FORBIDDEN", detail: "", status: 403, correlationId: "c" }),
    );

    renderPage("workspace");

    expect((await screen.findAllByText(/could not be loaded/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/No templates match/)).not.toBeInTheDocument();
  });
});

describe("TemplateListPage — the library", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it("archives rather than hides, counts firm copies, and never offers to archive the fallback", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([
      row({ origin: null, customisedByWorkspaces: 3 }),
      generic({ origin: null, customisedByWorkspaces: 0 }),
    ]);
    vi.mocked(templateApi.setTemplateActive).mockResolvedValue({} as TemplateDetail);

    renderPage("library");
    const table = grid();
    await within(table).findByText("Chief Executive Officer");
    await userEvent.click(within(table).getByRole("button", { name: "Archive Chief Executive Officer" }));

    expect(templateApi.setTemplateActive).toHaveBeenCalledWith("chief-executive-officer", false);
    expect(within(table).getByText("Firm copies")).toBeInTheDocument();
    expect(within(table).getByText("3")).toBeInTheDocument();
    expect(within(table).getByText("Fallback")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive Senior Executive (generic)" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Hide/ })).not.toBeInTheDocument();
  });
});
