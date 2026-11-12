import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import type { PersonResult } from "../../strategy/api/types";
import * as sourcingApi from "../api/sourcingApi";
import type {
  CompanyProgress,
  CompanySuggestions,
  SavedCriteria,
  SourcingRun,
  SuggestedPerson,
} from "../api/sourcingApi";
import type { SourcingRunState } from "../lib/useSourcingRun";
import { FindExecutivesPanel } from "./FindExecutivesPanel";

vi.mock("../api/sourcingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof sourcingApi>()),
  getCriteria: vi.fn(),
  suggestCriteria: vi.fn(),
  saveCriteria: vi.fn(),
  countSearch: vi.fn(),
  getSuggestions: vi.fn(),
  addSuggested: vi.fn(),
}));
vi.mock("../../../lib/countries", () => import("../../../test/countries"));

const SAVED: SavedCriteria = {
  criteria: {
    levels: ["C_LEVEL", "SENIOR_VP", "VP_HEAD_DIRECTOR"],
    functionTerms: ["Finance", "Financial"],
    standaloneTitles: ["CFO"],
    localLevelWords: [],
    excludedWords: ["Assistant"],
    suggestions: ["Treasury"],
    roleSummary: null,
    countryCodes: ["AE"],
    perCompany: 3,
  },
  source: "AI",
  briefChanged: false,
  updatedAt: "2026-11-03T10:00:00Z",
};

const profileOf = (slug: string, fullName: string, title: string | null): PersonResult => ({
  linkedinSlug: slug,
  fullName,
  title,
  companyName: "Harbour Group",
  companyLinkedinUrl: null,
  companyLogoUrl: null,
  location: "Dubai",
  countryCode: "AE",
  photoUrl: null,
  profileUrl: `https://www.linkedin.com/in/${slug}`,
  about: null,
  career: [],
  education: [],
  skills: [],
  languages: [],
  details: null,
  candidateId: null,
  held: false,
});

const fits = (rank: number, slug: string, name: string, title: string, reasons: string[]): SuggestedPerson => ({
  linkedinSlug: slug,
  verdict: "FITS",
  score: 90 - rank * 5,
  band: rank <= 2 ? "STRONG" : "GOOD",
  rank,
  reasons,
  foundBy: "LIVE",
  candidateId: null,
  person: profileOf(slug, name, title),
});

const harbour = (people: SuggestedPerson[], fitCount: number): CompanySuggestions => ({
  triageCompanyId: "c1",
  companyName: "Harbour Group",
  runId: "run1",
  searchedAt: "2026-11-03T10:05:00Z",
  result: {
    triageCompanyId: "c1",
    companyName: "Harbour Group",
    outcome: fitCount > 0 ? "FOUND" : "NO_HITS",
    found: people.length,
    fits: fitCount,
    noTitle: 0,
    notFit: 0,
    bought: 3,
    cached: 0,
    matched: 6,
    indexFound: 4,
    titleChecks: 2,
  },
  expired: false,
  people,
});

const run = (overrides: Partial<SourcingRun> = {}): SourcingRun => ({
  id: "run1",
  status: "COMPLETED",
  companies: [{ triageCompanyId: "c1", companyName: "Harbour Group", searchable: true }],
  companiesTotal: 1,
  companiesDone: 1,
  suggested: 0,
  profilesBought: 3,
  profilesCached: 0,
  stopRequested: false,
  searched: null,
  outcomes: [],
  requestedAt: "2026-11-03T10:00:00Z",
  startedAt: "2026-11-03T10:00:01Z",
  finishedAt: "2026-11-03T10:05:00Z",
  error: null,
  ...overrides,
});

const step = (
  triageCompanyId: string,
  stage: CompanyProgress["stage"],
  done = 0,
  total = 0,
  times: Partial<Pick<CompanyProgress, "startedAt" | "finishedAt">> = {},
): CompanyProgress => ({
  triageCompanyId,
  stage,
  considered: 20,
  done,
  total,
  startedAt: "2026-11-03T10:00:02Z",
  finishedAt: null,
  ...times,
});

const sourcingWith = (overrides: Partial<SourcingRunState> = {}): SourcingRunState => ({
  offered: true,
  config: { enabled: true, maxCompaniesPerRun: 10, perCompanyChoices: [3, 5, 10], defaultPerCompany: 3, lostAfterSeconds: 240, parallelism: 4 },
  run: null,
  isRunning: false,
  button: { kind: "idle" },
  markOpened: vi.fn(),
  start: vi.fn(),
  isStarting: false,
  stop: vi.fn(),
  isStopping: false,
  ...overrides,
});

function countedAt(...companies: [string, string][]) {
  vi.mocked(sourcingApi.countSearch).mockResolvedValue({
    counted: true,
    companies: companies.map(([triageCompanyId, companyName]) => ({
      triageCompanyId,
      companyName,
      searchable: true,
      match: 6,
      onFile: 0,
      toBuy: 3,
    })),
    levels: [],
    words: [],
    match: 6 * companies.length,
    onFile: 0,
    toBuy: 3 * companies.length,
  });
}

function renderPanel(sourcing: SourcingRunState, autoSearch = false, scope: readonly string[] = ["c1"]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <FindExecutivesPanel
          open
          onClose={vi.fn()}
          projectId="p1"
          subtitle="Group CFO"
          scope={scope}
          autoSearch={autoSearch}
          sourcing={sourcing}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("FindExecutivesPanel", () => {
  beforeEach(() => {
    vi.mocked(sourcingApi.getCriteria).mockResolvedValue(SAVED);
    vi.mocked(sourcingApi.saveCriteria).mockImplementation(async (_, criteria) => ({ ...SAVED, criteria }));
    vi.mocked(sourcingApi.countSearch).mockResolvedValue({
      counted: true,
      companies: [{ triageCompanyId: "c1", companyName: "Harbour Group", searchable: true, match: 6, onFile: 0, toBuy: 3 }],
      levels: [],
      words: [{ word: "Treasury", count: 2 }],
      match: 6,
      onFile: 0,
      toBuy: 3,
    });
    vi.mocked(sourcingApi.addSuggested).mockResolvedValue({
      added: 3,
      alreadyOnPosition: 0,
      expired: 0,
      notFound: 0,
      withheld: 0,
      filed: [],
    });
  });

  it("lists who fits best first with their reasons, pre-ticks the top three, folds the rest, and adds the ticked", async () => {
    const onPosition = { ...fits(5, "on-position", "Rami Khoury", "Group CFO", []), candidateId: "cand-1" };
    const noTitle: SuggestedPerson = { ...fits(6, "no-title", "Sanjay Menon", "", []), verdict: "NO_TITLE", score: null, band: null, person: profileOf("no-title", "Sanjay Menon", null) };
    const notFit: SuggestedPerson = { ...fits(7, "assistant", "Hiba Kanaan", "Executive Assistant to the CFO", ["LEAVE_OUT:Assistant"]), verdict: "NOT_FIT", score: null, band: null };
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({
      companies: [
        harbour(
          [
            fits(1, "the-cfo", "Fatima Al Mazrouei", "Group CFO", ["ROLE_WORD:CFO", "LEVEL:TOP", "LIVES_IN_ROLE_COUNTRY:AE"]),
            fits(2, "finance-director", "Karim Aziz", "Group Finance Director", ["ROLE_WORD:Finance", "LEVEL:HEAD"]),
            fits(3, "treasury", "Priya Raman", "Head of Group Treasury", ["SEARCH_WORD:Treasury", "FOUND_BY_BOTH"]),
            fits(4, "controller", "Omar Siddiqui", "Head of Finance", ["ROLE_WORD:Finance"]),
            onPosition,
            noTitle,
            notFit,
          ],
          5,
        ),
      ],
    });
    const sourcing = sourcingWith({ run: run({ suggested: 5 }) });
    renderPanel(sourcing);

    const company = await screen.findByRole("region", { name: "Harbour Group" });
    expect(within(company).getByText("CFO in title")).toBeInTheDocument();
    expect(within(company).getByText("Lives in United Arab Emirates")).toBeInTheDocument();
    expect(within(company).getByText("Found by two sources")).toBeInTheDocument();
    expect(within(company).getAllByText("Strong")).toHaveLength(2);
    expect(within(company).getByRole("checkbox", { name: "Add Fatima Al Mazrouei" })).toBeChecked();
    expect(within(company).getByRole("checkbox", { name: "Add Priya Raman" })).toBeChecked();
    expect(within(company).getByRole("checkbox", { name: "Add Omar Siddiqui" })).not.toBeChecked();
    expect(within(company).getByRole("checkbox", { name: "Rami Khoury is on this position" })).toBeDisabled();
    expect(within(company).queryByText("Sanjay Menon")).not.toBeInTheDocument();

    await userEvent.click(within(company).getByRole("button", { name: "Doesn't fit the role (1)" }));
    expect(within(company).getByText("Leave-out word: Assistant")).toBeInTheDocument();
    await userEvent.click(within(company).getByRole("button", { name: "Title not on record (1)" }));
    expect(within(company).getByText("Sanjay Menon")).toBeInTheDocument();

    expect(sourcing.markOpened).toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Add 3 to In universe" }));
    await waitFor(() =>
      expect(sourcingApi.addSuggested).toHaveBeenCalledWith("p1", [
        { triageCompanyId: "c1", linkedinSlug: "the-cfo" },
        { triageCompanyId: "c1", linkedinSlug: "finance-director" },
        { triageCompanyId: "c1", linkedinSlug: "treasury" },
      ]),
    );
  });

  it("counts and adds only the ticked people on screen, never those pre-ticked at a company this panel does not show", async () => {
    const elsewhere: CompanySuggestions = {
      ...harbour(
        [
          fits(1, "other-cfo", "Layla Haddad", "Group CFO", []),
          fits(2, "other-fd", "Yousef Nasser", "Finance Director", []),
        ],
        2,
      ),
      triageCompanyId: "c9",
      companyName: "Cedar Holdings",
    };
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({
      companies: [harbour([fits(1, "the-cfo", "Fatima Al Mazrouei", "Group CFO", [])], 1), elsewhere],
    });
    renderPanel(sourcingWith({ run: run({ suggested: 3 }) }));

    expect(await screen.findByRole("checkbox", { name: "Add Fatima Al Mazrouei" })).toBeChecked();
    expect(screen.queryByText("Layla Haddad")).not.toBeInTheDocument();
    expect(screen.getByText("1 selected")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Add 1 to In universe" }));
    await waitFor(() =>
      expect(sourcingApi.addSuggested).toHaveBeenCalledWith("p1", [{ triageCompanyId: "c1", linkedinSlug: "the-cfo" }]),
    );
  });

  it("opened to search, searches a company not searched yet at once, and only once", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    const sourcing = sourcingWith();

    renderPanel(sourcing, true);

    await waitFor(() => expect(sourcing.start).toHaveBeenCalledWith(["c1"]));
    expect(sourcing.start).toHaveBeenCalledTimes(1);
  });

  it("opened to search, shows a company searched before rather than searching it again", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [harbour([], 0)] });
    const sourcing = sourcingWith();

    renderPanel(sourcing, true);

    expect((await screen.findAllByRole("button", { name: /search again/i })).length).toBeGreaterThan(0);
    await waitFor(() => expect(sourcingApi.countSearch).toHaveBeenCalled());
    expect(sourcing.start).not.toHaveBeenCalled();
  });

  it("opened only to look, waits for Search", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    const sourcing = sourcingWith();

    renderPanel(sourcing);

    expect(await screen.findByRole("button", { name: /search 1 company/i })).toBeInTheDocument();
    expect(sourcing.start).not.toHaveBeenCalled();
  });

  it("offers the suggested words with their counts where nobody fits, and retries those companies with the words picked", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [harbour([], 0)] });
    const sourcing = sourcingWith({ run: run() });
    renderPanel(sourcing);

    expect(await screen.findByText("1 company found nobody who fits.")).toBeInTheDocument();
    const retryBox = screen.getByText("1 company found nobody who fits.").parentElement as HTMLElement;
    await userEvent.click(within(retryBox).getByRole("button", { name: /Treasury\s*2/ }));
    await userEvent.click(within(retryBox).getByRole("button", { name: "Retry 1 company" }));

    await waitFor(() => expect(sourcing.start).toHaveBeenCalledWith(["c1"]));
    expect(vi.mocked(sourcingApi.saveCriteria).mock.calls.at(-1)?.[1].functionTerms).toEqual([
      "Finance",
      "Financial",
      "Treasury",
    ]);
  });

  it("shows the search in progress company by company, with Stop", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    const sourcing = sourcingWith({
      isRunning: true,
      run: run({
        status: "RUNNING",
        companies: [
          { triageCompanyId: "c1", companyName: "Harbour Group", searchable: true },
          { triageCompanyId: "c2", companyName: "Cedar Holdings", searchable: true },
        ],
        companiesTotal: 2,
        companiesDone: 1,
        outcomes: [{ ...harbour([], 2).result, outcome: "FOUND" }],
      }),
    });
    countedAt(["c1", "Harbour Group"], ["c2", "Cedar Holdings"]);
    renderPanel(sourcing, false, ["c1", "c2"]);

    expect(await screen.findByText("Searching · 1 of 2 done")).toBeInTheDocument();
    expect(await screen.findByRole("region", { name: "Cedar Holdings" })).toHaveTextContent("Next in line");
    await userEvent.click(screen.getByRole("button", { name: "Stop" }));
    expect(sourcing.stop).toHaveBeenCalled();
  });

  it("shows every company being searched side by side on its own step, and how long is left once one has finished", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    countedAt(["c1", "Harbour Group"], ["c2", "Cedar Holdings"], ["c3", "Dune Partners"], ["c4", "Eastgate"]);
    renderPanel(
      sourcingWith({
        isRunning: true,
        run: run({
          status: "RUNNING",
          companies: [
            { triageCompanyId: "c1", companyName: "Harbour Group", searchable: true },
            { triageCompanyId: "c2", companyName: "Cedar Holdings", searchable: true },
            { triageCompanyId: "c3", companyName: "Dune Partners", searchable: true },
            { triageCompanyId: "c4", companyName: "Eastgate", searchable: true },
          ],
          companiesTotal: 4,
          companiesDone: 1,
          outcomes: [{ ...harbour([], 2).result, outcome: "FOUND" }],
          progress: [
            step("c1", "RANKING", 0, 0, { startedAt: "2026-11-03T10:00:02Z", finishedAt: "2026-11-03T10:00:32Z" }),
            step("c2", "CHECKING", 3, 10),
            step("c3", "LOOKING"),
          ],
        }),
      }),
      false,
      ["c1", "c2", "c3", "c4"],
    );

    const status = (await screen.findByText("Searching · 1 of 4 done")).closest('[role="status"]') as HTMLElement;
    expect(status).toHaveTextContent("60 looked at");
    expect(status).toHaveTextContent("About 30 s left");
    expect(await screen.findByRole("region", { name: "Cedar Holdings" })).toHaveTextContent("Checking titles · 3 of 10");
    expect(screen.getByRole("region", { name: "Dune Partners" })).toHaveTextContent("Looking for people in the role…");
    expect(screen.getByRole("region", { name: "Eastgate" })).toHaveTextContent("Next in line");
  });

  it("says on the status line that it is counting, free, while the companies' frames wait in place", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    vi.mocked(sourcingApi.countSearch).mockReturnValue(new Promise(() => {}));
    renderPanel(sourcingWith());

    const status = (await screen.findByText("Counting who matches at each company")).closest(
      '[role="status"]',
    ) as HTMLElement;
    expect(status).toHaveTextContent("free, nothing is bought");
    expect(screen.getByRole("button", { name: /^Search$/ })).toBeDisabled();
  });

  it("keeps a company being searched in view under a level tab", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({
      companies: [harbour([fits(1, "the-cfo", "Fatima Al Mazrouei", "Group CFO", ["LEVEL:TOP"])], 1)],
    });
    countedAt(["c1", "Harbour Group"], ["c2", "Cedar Holdings"]);
    renderPanel(
      sourcingWith({
        isRunning: true,
        run: run({
          id: "run2",
          status: "RUNNING",
          companies: [{ triageCompanyId: "c2", companyName: "Cedar Holdings", searchable: true }],
          companiesTotal: 1,
          companiesDone: 0,
          progress: [step("c2", "CHECKING", 1, 4)],
        }),
      }),
      false,
      ["c1", "c2"],
    );

    await userEvent.click(await screen.findByRole("button", { name: /^C-level\s*\d+$/ }));

    expect(screen.getByRole("region", { name: "Cedar Holdings" })).toHaveTextContent("Checking titles · 1 of 4");
    expect(screen.getByRole("region", { name: "Harbour Group" })).toHaveTextContent("Fatima Al Mazrouei");
  });

  it("says when the AI could not draft the search, and Try again asks once more", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    vi.mocked(sourcingApi.getCriteria).mockResolvedValue(null);
    vi.mocked(sourcingApi.suggestCriteria).mockRejectedValueOnce(new Error("model down")).mockResolvedValue(SAVED);
    renderPanel(sourcingWith());

    expect(await screen.findByText("Couldn't draft the search from the brief")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    await waitFor(() => expect(sourcingApi.suggestCriteria).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/Finance, Financial, CFO/)).toBeInTheDocument();
  });

  it("folds the search to one line, and Edit search opens each field as a dropdown", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({ companies: [] });
    renderPanel(sourcingWith());

    expect(await screen.findByText(/Finance, Financial, CFO · United Arab Emirates/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Title words" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Edit search" }));
    const titles = screen.getByRole("button", { name: "Title words" });
    expect(titles).toHaveTextContent("Finance, Financial +1");
    await userEvent.click(titles);
    await userEvent.click(await screen.findByRole("button", { name: /Treasury\s*2/ }));
    expect(titles).toHaveTextContent("Finance, Financial +2");
    await userEvent.type(screen.getByRole("textbox", { name: "Add a title word" }), "Audit{Enter}");
    expect(titles).toHaveTextContent("Finance, Financial +3");
    expect(screen.getByRole("button", { name: "Hide search" })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows no older results at a company the search running now has not read back yet", async () => {
    vi.mocked(sourcingApi.getSuggestions).mockResolvedValue({
      companies: [harbour([fits(1, "the-cfo", "Fatima Al Mazrouei", "Group CFO", ["ROLE_WORD:CFO"])], 1)],
    });
    const searching = sourcingWith({
      isRunning: true,
      run: run({
        id: "run2",
        status: "RUNNING",
        companiesDone: 0,
        progress: [step("c1", "CHECKING", 12, 40)],
      }),
    });
    const { unmount } = renderPanel(searching);

    expect(await screen.findByRole("region", { name: "Harbour Group" })).toHaveTextContent(
      "Checking titles · 12 of 40",
    );
    expect(screen.queryByText("Fatima Al Mazrouei")).not.toBeInTheDocument();
    unmount();
    const readsBefore = vi.mocked(sourcingApi.getSuggestions).mock.calls.length;

    const finished = sourcingWith({
      run: run({ id: "run2", outcomes: [{ ...harbour([], 1).result, outcome: "FOUND" }] }),
    });
    renderPanel(finished);
    await waitFor(() =>
      expect(vi.mocked(sourcingApi.getSuggestions).mock.calls.length).toBeGreaterThanOrEqual(readsBefore + 2),
    );
    expect(screen.queryByText("Fatima Al Mazrouei")).not.toBeInTheDocument();
  });
});
