import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import type { Project } from "../../projects/api/types";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import * as contactLookupApi from "../../contactlookup/api/contactLookupApi";
import * as peopleApi from "../api/peopleApi";
import * as strategyApi from "../api/strategyApi";
import type { PeopleFilter, PeopleSearchPage, PersonDetails, PersonResult, Strategy } from "../api/types";
import { NO_PEOPLE_FILTER } from "./PeopleStrategyEditor";
import { StrategyPage } from "./StrategyPage";

vi.mock("../../../lib/countries", () => import("../../../test/countries"));
vi.mock("../api/strategyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof strategyApi>()),
  getStrategy: vi.fn(),
  saveSearch: vi.fn(),
}));
vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatesApi>()),
  getCandidate: vi.fn(),
}));
vi.mock("../../contactlookup/api/contactLookupApi", async (importOriginal) => ({
  ...(await importOriginal<typeof contactLookupApi>()),
  getContactLookupConfig: vi.fn(),
}));
vi.mock("../api/peopleApi", async (importOriginal) => ({
  ...(await importOriginal<typeof peopleApi>()),
  putPeopleFilter: vi.fn(),
  getPeopleFacets: vi.fn(),
  getPeopleCount: vi.fn(),
  searchPeople: vi.fn(),
  addPeople: vi.fn(),
  getPeopleResults: vi.fn(),
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
  companyLogoUrl: null,
  photoUrl: null,
  profileUrl: `https://www.linkedin.com/in/${slug}/`,
  about: "Finance leader.",
  career: [
    { company: "Harbour Group", title: "Chief Financial Officer", period: "2021 – Present" },
    { company: "Farro Capital", title: "Finance Director", period: "2015 – 2021" },
  ],
  education: [{ school: "Sample University", degree: "MBA", period: "2005 – 2007" }],
  skills: ["Treasury"],
  languages: [],
  details: null,
  candidateId: null,
  held: false,
  ...overrides,
});

const DETAILS: PersonDetails = {
  headline: "Group CFO | Board Member",
  industry: null,
  jobFunction: "Finance",
  seniority: "CXO",
  workStatus: null,
  followers: 1200,
  updatedAt: "2026-08-24 16:07:22",
  links: [{ label: "GitHub", url: "https://github.com/samplecfo" }],
  certifications: [{ title: "Chartered Accountant", subtitle: "ICAEW", period: "2009", url: null, description: null }],
  publications: [],
  projects: [],
  volunteering: [],
  contactAvailability: { personalEmail: false, workEmail: true, phone: true },
  company: {
    website: "https://harbour.example",
    domain: "harbour.example",
    industry: "Maritime",
    size: "1001-5000",
    country: null,
    headquarter: "Dubai",
    foundedYear: 1998,
    revenue: null,
    overview: "Ports and logistics.",
    specialties: [],
  },
};

const heldCandidate = {
  id: "cand-1",
  fullName: "Person first",
  source: "people_search",
  linkedinUrl: "https://www.linkedin.com/in/first/",
  contacts: { emails: [], phones: [], emailsLookedUpAt: null, phonesLookedUpAt: null, source: null },
} as unknown as Candidate;

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
    localStorage.clear();
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
    vi.mocked(peopleApi.getPeopleResults).mockResolvedValue({ pages: [] });
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

    expect((await screen.findAllByText("Person first")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("In mandate").length).toBeGreaterThan(0);
    expect(screen.queryByRole("checkbox", { name: "Select Person held" })).not.toBeInTheDocument();
    expect(vi.mocked(peopleApi.searchPeople).mock.calls).toEqual([["p1", 1]]);

    await user.click(screen.getByRole("button", { name: /Load 25 more/ }));

    expect((await screen.findAllByText("Person third")).length).toBeGreaterThan(0);
    expect(vi.mocked(peopleApi.searchPeople).mock.calls.at(-1)).toEqual(["p1", 2]);
  });

  it("opens on the people already bought for the saved filter, without a search", async () => {
    vi.mocked(peopleApi.getPeopleResults).mockResolvedValue({
      pages: [pageOf(1, [personOf("first")], 0), pageOf(2, [personOf("second")], 0)],
    });
    renderPeopleMode();

    expect((await screen.findAllByText("Person first")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Person second").length).toBeGreaterThan(0);
    expect(screen.getByText("Answered from the cache — no credits spent")).toBeInTheDocument();
    expect(peopleApi.searchPeople).not.toHaveBeenCalled();

    vi.mocked(peopleApi.searchPeople).mockResolvedValueOnce(pageOf(3, [personOf("third")]));
    await userEvent.setup().click(screen.getByRole("button", { name: /Load 25 more/ }));
    expect((await screen.findAllByText("Person third")).length).toBeGreaterThan(0);
    expect(peopleApi.searchPeople).toHaveBeenCalledWith("p1", 3);
  });

  it("files the ticked people at the stage picked on the bar, as the Companies grid does", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople).mockResolvedValue(pageOf(1, [personOf("first"), personOf("second")]));
    vi.mocked(peopleApi.addPeople).mockResolvedValue({ added: 1, skipped: 0, unavailable: 0, elsewhere: 0, filed: [{ linkedinSlug: "first", candidateId: "cand-1" }] });
    renderPeopleMode();

    await user.click(await screen.findByRole("button", { name: "Search" }));
    await user.click((await screen.findAllByRole("checkbox", { name: "Select Person first" }))[0]);
    await user.click(screen.getByRole("button", { name: "Shortlisted" }));

    await waitFor(() => expect(peopleApi.addPeople).toHaveBeenCalledWith("p1", ["first"], "shortlisted"));
    expect(await screen.findByText("1 person added to Shortlisted")).toBeInTheDocument();
    expect(screen.getAllByText("In mandate").length).toBeGreaterThan(0);
  });

  it("reads a person's whole profile from the page already fetched, and adds them from there", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople).mockResolvedValue(pageOf(1, [personOf("first")]));
    vi.mocked(peopleApi.addPeople).mockResolvedValue({ added: 1, skipped: 0, unavailable: 0, elsewhere: 0, filed: [{ linkedinSlug: "first", candidateId: "cand-1" }] });
    renderPeopleMode();

    await user.click(await screen.findByRole("button", { name: "Search" }));
    await user.click((await screen.findAllByText("Person first"))[0]);

    const drawer = await screen.findByRole("dialog", { name: "Person first" });
    await user.click(within(drawer).getByRole("button", { name: "Expand all" }));
    expect(within(drawer).getByText("Farro Capital")).toBeInTheDocument();
    expect(within(drawer).getAllByText("Sample University").length).toBeGreaterThan(0);
    expect(within(drawer).getByText("Treasury")).toBeInTheDocument();
    expect(peopleApi.searchPeople).toHaveBeenCalledTimes(1);

    await user.click(within(drawer).getByRole("button", { name: "Declined" }));
    await waitFor(() => expect(peopleApi.addPeople).toHaveBeenCalledWith("p1", ["first"], "declined"));
  });

  it("shows every part ContactOut sent, and no fold for what it did not", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople).mockResolvedValue(
      pageOf(1, [personOf("first", { details: DETAILS }), personOf("sparse", { skills: [], about: null })]),
    );
    renderPeopleMode();

    await user.click(await screen.findByRole("button", { name: "Search" }));
    await user.click((await screen.findAllByText("Person first"))[0]);
    const drawer = await screen.findByRole("dialog", { name: "Person first" });
    await user.click(within(drawer).getByRole("button", { name: "Expand all" }));

    expect(within(drawer).getByText("Group CFO | Board Member")).toBeInTheDocument();
    expect(within(drawer).getByText("Chartered Accountant")).toBeInTheDocument();
    expect(within(drawer).getByText("Ports and logistics.")).toBeInTheDocument();
    expect(within(drawer).getByRole("link", { name: "Harbour Group on website" })).toHaveAttribute(
      "href",
      "https://harbour.example",
    );
    expect(within(drawer).getByRole("link", { name: "Person first on GitHub" })).toBeInTheDocument();
    expect(within(drawer).getByText(/ContactOut has a work email and a phone number/)).toBeInTheDocument();
    expect(within(drawer).queryByRole("button", { name: "Find email" })).not.toBeInTheDocument();

    await user.click(within(drawer).getByRole("button", { name: "Close" }));
    await user.click((await screen.findAllByText("Person sparse"))[0]);
    const sparse = await screen.findByRole("dialog", { name: "Person sparse" });
    expect(within(sparse).queryByText("Certifications")).not.toBeInTheDocument();
    expect(within(sparse).queryByText("Summary")).not.toBeInTheDocument();
    expect(within(sparse).queryByText("Contact")).not.toBeInTheDocument();
  });

  it("offers Find email on a person once they are in the mandate, and on one just added", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople).mockResolvedValue(pageOf(1, [personOf("first", { details: DETAILS })]));
    vi.mocked(peopleApi.addPeople).mockResolvedValue({
      added: 1,
      skipped: 0,
      unavailable: 0,
      elsewhere: 0,
      filed: [{ linkedinSlug: "first", candidateId: "cand-1" }],
    });
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(heldCandidate);
    vi.mocked(contactLookupApi.getContactLookupConfig).mockResolvedValue({ enabled: true });
    renderPeopleMode();

    await user.click(await screen.findByRole("button", { name: "Search" }));
    await user.click((await screen.findAllByText("Person first"))[0]);
    const drawer = await screen.findByRole("dialog", { name: "Person first" });
    await user.click(within(drawer).getByRole("button", { name: "In universe" }));

    await user.click(await within(drawer).findByRole("button", { name: "Expand all" }));
    expect(await within(drawer).findByRole("button", { name: "Find email" })).toBeInTheDocument();
    expect(candidatesApi.getCandidate).toHaveBeenCalledWith("p1", "cand-1", expect.anything());
  });

  it("reads the same page as cards, ticks and all, and keeps the choice", async () => {
    const user = userEvent.setup();
    vi.mocked(peopleApi.searchPeople).mockResolvedValue(
      pageOf(1, [personOf("first", { details: DETAILS }), personOf("held", { held: true, candidateId: "c9" })]),
    );
    vi.mocked(peopleApi.addPeople).mockResolvedValue({
      added: 1,
      skipped: 0,
      unavailable: 0,
      elsewhere: 0,
      filed: [{ linkedinSlug: "first", candidateId: "cand-1" }],
    });
    const { unmount } = renderPeopleMode();

    await user.click(await screen.findByRole("radio", { name: "Cards" }));
    await user.click(await screen.findByRole("button", { name: "Search" }));

    const cards = within(await screen.findByRole("list", { name: "People" }));
    expect(cards.getAllByText("Finance Director · Farro Capital")).toHaveLength(2);
    expect(cards.getAllByText(/In role since 2021/)).toHaveLength(2);
    expect(cards.getByRole("link", { name: "Harbour Group on website" })).toBeInTheDocument();
    expect(cards.queryByRole("checkbox", { name: "Select Person held" })).not.toBeInTheDocument();

    await user.click(cards.getByRole("checkbox", { name: "Select Person first" }));
    await user.click(screen.getByRole("button", { name: "Shortlisted" }));
    await waitFor(() => expect(peopleApi.addPeople).toHaveBeenCalledWith("p1", ["first"], "shortlisted"));

    await user.click(cards.getByRole("button", { name: "Person held" }));
    expect(await screen.findByRole("dialog", { name: "Person held" })).toBeInTheDocument();

    unmount();
    renderPeopleMode();
    expect(await screen.findByRole("radio", { name: "Cards" })).toBeChecked();
  });

  it("is not offered to a client seat, which stays on the company search", async () => {
    auth.roles = ["CLIENT"];
    renderPeopleMode();

    await waitFor(() => expect(strategyApi.getStrategy).toHaveBeenCalled());
    expect(screen.queryByRole("radiogroup", { name: "Search for" })).not.toBeInTheDocument();
    expect(peopleApi.getPeopleCount).not.toHaveBeenCalled();
  });
});
