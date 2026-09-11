import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
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

describe("TemplateListPage — a firm's templates", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("says where each template came from, and which copies the library has moved past", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([
      row({ code: "group-treasury-lead", title: "Group Treasury Lead", discipline: "FINANCE", origin: "OWN" }),
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

    expect(await screen.findByText("Finance · 2")).toBeInTheDocument();
    expect(screen.getByText("Your own")).toBeInTheDocument();
    expect(screen.getByText("Customised")).toBeInTheDocument();
    expect(screen.getByText("Library updated")).toBeInTheDocument();
    expect(screen.getByText("Hidden")).toBeInTheDocument();
    expect(screen.getByText("4 templates · 1 customised · 1 your own · 1 hidden")).toBeInTheDocument();
  });

  it("hides a library template from the firm, and never offers to hide the fallback", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([row({}), generic()]);
    vi.mocked(templateApi.setTemplateHidden).mockResolvedValue({} as TemplateDetail);

    renderPage("workspace");
    await userEvent.click(await screen.findByRole("button", { name: "Hide Chief Executive Officer" }));

    expect(templateApi.setTemplateHidden).toHaveBeenCalledWith("chief-executive-officer", true);
    expect(screen.queryByRole("button", { name: "Hide Senior Executive (generic)" })).not.toBeInTheDocument();
  });

  it("says the list could not be read, rather than that there are no templates", async () => {
    vi.mocked(templateApi.listTemplates).mockRejectedValue(
      new ApiRequestError({ code: "FORBIDDEN", detail: "", status: 403, correlationId: "c" }),
    );

    renderPage("workspace");

    expect(await screen.findByRole("alert")).toHaveTextContent("could not be loaded");
    expect(screen.queryByText(/0 templates/)).not.toBeInTheDocument();
  });
});

describe("TemplateListPage — the library", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("archives rather than hides, and never offers to archive the fallback", async () => {
    vi.mocked(templateApi.listTemplates).mockResolvedValue([row({ origin: null }), generic({ origin: null })]);
    vi.mocked(templateApi.setTemplateActive).mockResolvedValue({} as TemplateDetail);

    renderPage("library");
    await userEvent.click(await screen.findByRole("button", { name: "Archive Chief Executive Officer" }));

    expect(templateApi.setTemplateActive).toHaveBeenCalledWith("chief-executive-officer", false);
    expect(screen.getByText("Fallback")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive Senior Executive (generic)" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Hide/ })).not.toBeInTheDocument();
  });
});
