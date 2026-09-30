import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import type { Project } from "../../projects/api/types";
import * as peopleApi from "../api/peopleApi";
import * as strategyApi from "../api/strategyApi";
import type { PeopleFilter, PeopleSearchPage, PersonResult, Strategy } from "../api/types";
import { NO_PEOPLE_FILTER } from "./PeopleStrategyEditor";
import { StrategyPage } from "./StrategyPage";

vi.mock("../../../lib/countries", () => import("../../../test/countries"));
vi.mock("../api/strategyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof strategyApi>()),
  getStrategy: vi.fn(),
  saveSearch: vi.fn(),
}));
vi.mock("../api/peopleApi", async (importOriginal) => ({
  ...(await importOriginal<typeof peopleApi>()),
  putPeopleFilter: vi.fn(),
  getPeopleFacets: vi.fn(),
  getPeopleCount: vi.fn(),
  searchPeople: vi.fn(),
  addPeople: vi.fn(),
  suggestLocations: vi.fn(),
}));

const auth = vi.hoisted(() => ({ roles: ["ADMIN"] as string[] }));
vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", fullName: "Nadia Haddad", workspace: { roles: auth.roles } } }),
}));

const project = { id: "p1", positionTitle: "CFO", team: [] } as unknown as Project;

const CFO_FILTER: PeopleFilter = { ...NO_PEOPLE_FILTER, jobTitles: ["CFO"], seniorities: ["CXO"] };

const strategyOf = (peopleFilter: PeopleFilter = CFO_FILTER): Strategy => ({
  filter: {
    industries: [],
    keywords: [],
    marketSegments: [],
    countries: [],
    employeeBands: [],
    revenueBands: [],
    employeeRange: null,
    revenueRange: null,
  },
  peopleFilter,
  offLimits: [],
  searches: [],
});

const personOf = (slug: string, overrides: Partial<PersonResult> = {}): PersonResult => ({
  linkedinSlug: slug,
  fullName: `Person ${slug}`,
  title: "Chief Financial Officer",
  companyName: "Harbour Group",
  companyLinkedinUrl: "https://www.linkedin.com/company/harbour-group/",
  location: "Dubai, United Arab Emirates",
  countryCode: "AE",
  photoUrl: null,
  profileUrl: `https://www.linkedin.com/in/${slug}/`,
  held: false,
  ...overrides,
});

const pageOf = (page: number, people: PersonResult[], billed = people.length): PeopleSearchPage => ({
  people,
  page,
  pageSize: 25,
  total: 30,
  billed,
  cached: people.length - billed,
});

const renderPeopleMode = () =>
  render(
    <MemoryRouter initialEntries={["/?mode=people"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route path="/" element={<StrategyPage />} />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("StrategyPage — People mode", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auth.roles = ["ADMIN"];
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(strategyOf());
    vi.mocked(peopleApi.getPeopleFacets).mockResolvedValue({
      seniorities: [{ value: "CXO", label: "C-suite" }],
      jobFunctions: [{ value: "Finance", label: "Finance" }],
      companySizes: [],
      yearsOfExperience: [],
      yearsInCurrentRole: [],
      languageProficiencies: [],
      industries: ["Software Development"],
    });
    vi.mocked(peopleApi.getPeopleCount).mockResolvedValue({
      offered: true,
      total: 1240,
      estimatedPersonalEmails: 300,
      estimatedWorkEmails: 800,
      estimatedPhones: 150,
    });
    vi.mocked(peopleApi.putPeopleFilter).mockImplementation(async (_id, filter) => strategyOf(filter));
  });

  it("shows the free count and searches nothing until Search is pressed", async () => {
    renderPeopleMode();

    expect(await screen.findByText("1,240")).toBeInTheDocument();
    expect(peopleApi.searchPeople).not.toHaveBeenCalled();
  });

  it("brings the top 25 on Search and the next page only on Load more", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople)
      .mockResolvedValueOnce(pageOf(1, [personOf("first"), personOf("held", { held: true })]))
      .mockResolvedValueOnce(pageOf(2, [personOf("third")], 0));
    renderPeopleMode();

    await user.click(await screen.findByRole("button", { name: "Search" }));

    expect(await screen.findByText("Person first")).toBeInTheDocument();
    expect(screen.getByText("In universe")).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Select Person held" })).not.toBeInTheDocument();
    expect(vi.mocked(peopleApi.searchPeople).mock.calls).toEqual([["p1", 1]]);

    await user.click(screen.getByRole("button", { name: /Load 25 more/ }));

    expect(await screen.findByText("Person third")).toBeInTheDocument();
    expect(vi.mocked(peopleApi.searchPeople).mock.calls.at(-1)).toEqual(["p1", 2]);
  });

  it("adds the ticked people and marks them as in the universe", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople).mockResolvedValue(pageOf(1, [personOf("first"), personOf("second")]));
    vi.mocked(peopleApi.addPeople).mockResolvedValue({ added: 1, skipped: 0, unavailable: 0 });
    renderPeopleMode();

    await user.click(await screen.findByRole("button", { name: "Search" }));
    await user.click(await screen.findByRole("checkbox", { name: "Select Person first" }));
    await user.click(screen.getByRole("button", { name: "Add to universe" }));

    await waitFor(() => expect(peopleApi.addPeople).toHaveBeenCalledWith("p1", ["first"]));
    expect(await screen.findByText("1 person added to the universe")).toBeInTheDocument();
    expect(screen.getByText("In universe")).toBeInTheDocument();
  });

  it("is not offered to a client seat, which stays on the company search", async () => {
    auth.roles = ["CLIENT"];
    renderPeopleMode();

    await waitFor(() => expect(strategyApi.getStrategy).toHaveBeenCalled());
    expect(screen.queryByRole("radiogroup", { name: "Search for" })).not.toBeInTheDocument();
    expect(peopleApi.getPeopleCount).not.toHaveBeenCalled();
  });
});
