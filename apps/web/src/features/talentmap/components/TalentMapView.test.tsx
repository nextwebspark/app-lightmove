import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../../triage/api/types";
import type { TalentMapPage } from "../api/types";
import { TalentMapView } from "./TalentMapView";

// jsdom has no WebGL and mapbox-gl breaks at import; the globe is a stub that draws each pin as a
// button, which is all the view needs from it to prove the two halves stay in step.
vi.mock("./TalentMapGlobe", () => ({
  default: ({
    features,
    selectedId,
    onSelect,
    renderPopup,
  }: {
    features: { features: { id: string; properties: { label: string } }[] };
    selectedId: string | null;
    onSelect: (id: string | null) => void;
    renderPopup: (id: string) => React.ReactNode;
  }) => (
    <div data-testid="globe-stub">
      {features.features.map((feature) => (
        <button key={feature.id} type="button" onClick={() => onSelect(feature.id)}>
          pin: {feature.properties.label}
        </button>
      ))}
      {selectedId && <div data-testid="popup">{renderPopup(selectedId)}</div>}
    </div>
  ),
}));
vi.mock("../../candidates/lib/useCandidatePhoto", () => ({ useCandidatePhoto: () => null }));

const company = (overrides: Partial<TriageCompany>): TriageCompany => ({
  id: "u1",
  apolloAccountId: "a1",
  source: "strategy",
  status: "inUniverse",
  note: null,
  companyName: "ACWA Power",
  industry: "oil & energy",
  companyCountry: "Saudi Arabia",
  companyCity: "Riyadh",
  numEmployees: 3000,
  annualRevenue: null,
  website: null,
  companyLinkedinUrl: null,
  foundedYear: null,
  shortDescription: null,
  sourceUrl: null,
  logoUrl: null,
  customFields: {},
  addedAt: "2026-08-01T09:00:00Z",
  ...overrides,
});

const person = (overrides: Partial<Candidate>): Candidate => ({
  id: "c1",
  triageCompanyId: "u1",
  companyName: "ACWA Power",
  fullName: "Yasmin El-Sayed",
  title: "VP Finance",
  seniority: "N-1",
  status: "engaged",
  email: null,
  phone: null,
  linkedinUrl: null,
  locationCountry: null,
  locationCity: null,
  nationality: null,
  yearsExperience: null,
  summary: null,
  note: null,
  compensation: {
    currency: null, baseSalary: null, bonus: null, allowances: null,
    longTermIncentive: null, noticePeriod: null,
  },
  career: [],
  education: [],
  skills: [],
  languages: [],
  source: "manual",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-08-02T09:00:00Z",
  enrichedAt: null,
  ...overrides,
});

const page: TalentMapPage = {
  companies: [
    company({ id: "u1", companyName: "ACWA Power" }),
    company({ id: "u3", companyName: "Emaar", companyCountry: "United Arab Emirates", companyCity: "Dubai" }),
  ],
  totalCompanies: 2,
  candidates: [
    person({ id: "c1", fullName: "Yasmin El-Sayed" }),
    person({ id: "c4", triageCompanyId: null, companyName: "Untriaged Co", fullName: "Lina Said", locationCountry: "Oman" }),
  ],
  totalCandidates: 2,
  locations: {
    u1: { latitude: 24.7, longitude: 46.7, precision: "CITY", placeLabel: "Riyadh, Saudi Arabia" },
    u3: { latitude: 25.3, longitude: 55.3, precision: "CITY", placeLabel: "Dubai, United Arab Emirates" },
    c4: { latitude: 21, longitude: 57, precision: "COUNTRY", placeLabel: "Oman" },
  },
  geocodingPending: 0,
};

const preferences = { view: "map" as const, panelCollapsed: false, showExecutives: true };

function renderView(overrides: Partial<Parameters<typeof TalentMapView>[0]> = {}) {
  const handlers = {
    onPreferences: vi.fn(),
    onOpenCompany: vi.fn(),
    onOpenCandidate: vi.fn(),
    onAddExecutive: vi.fn(),
  };
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TalentMapView
        projectId="p1"
        page={page}
        query=""
        accessToken="pk.test"
        canWrite
        loading={false}
        error={false}
        preferences={preferences}
        {...handlers}
        {...overrides}
      />
    </QueryClientProvider>,
  );
  return handlers;
}

describe("TalentMapView", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists country → company → executives with the counts before anything is opened", async () => {
    renderView();

    const tree = screen.getByRole("tree", { name: "Mapping" });
    expect(within(tree).getByRole("treeitem", { name: /Saudi Arabia, 1 company/ })).toBeInTheDocument();
    expect(within(tree).getByRole("treeitem", { name: /Oman, 0 companies/ })).toBeInTheDocument();
    expect(within(tree).getByText("No company in this mandate")).toBeInTheDocument();
    expect(within(tree).getByText("Lina Said")).toBeInTheDocument();

    // Companies start folded behind their count; the chevron opens them.
    expect(within(tree).queryByText("Yasmin El-Sayed")).not.toBeInTheDocument();
    await userEvent.click(within(tree).getByRole("button", { name: "Expand ACWA Power" }));
    expect(within(tree).getByText("Yasmin El-Sayed")).toBeInTheDocument();
  });

  it("opens the company's panel from a row, and the executive's from a pin", async () => {
    const handlers = renderView();

    await userEvent.click(screen.getByRole("button", { name: "Open ACWA Power" }));
    expect(handlers.onOpenCompany).toHaveBeenCalledWith(expect.objectContaining({ id: "u1" }));

    // Selecting a pin selects the row and opens the branches above it. The popup's own way in is the
    // name it is already showing — there is no second Open button beside it.
    await userEvent.click(screen.getByRole("button", { name: "pin: Yasmin El-Sayed" }));
    expect(screen.getByRole("treeitem", { name: "Yasmin El-Sayed" })).toHaveAttribute("aria-selected", "true");
    const popup = screen.getByTestId("popup");
    expect(within(popup).getByText("Executive")).toBeInTheDocument();
    expect(within(popup).queryByRole("button", { name: /^Open$/ })).not.toBeInTheDocument();
    await userEvent.click(within(popup).getByRole("button", { name: "Yasmin El-Sayed" }));
    expect(handlers.onOpenCandidate).toHaveBeenCalledWith(expect.objectContaining({ id: "c1" }));
  });

  it("offers Add executive on a company's popup only to someone who may write", async () => {
    const handlers = renderView();
    await userEvent.click(screen.getByRole("button", { name: "pin: ACWA Power" }));
    await userEvent.click(within(screen.getByTestId("popup")).getByRole("button", { name: /Add executive/ }));
    expect(handlers.onAddExecutive).toHaveBeenCalledWith(expect.objectContaining({ id: "u1" }));
  });

  it("narrows the panel and the pins together", () => {
    renderView({ query: "emaar" });

    const tree = screen.getByRole("tree", { name: "Mapping" });
    expect(within(tree).getByText("Emaar")).toBeInTheDocument();
    expect(within(tree).queryByText("ACWA Power")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "pin: ACWA Power" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "pin: Emaar" })).toBeInTheDocument();
  });

  it("says when the server is still placing rows, and when a read was refused", () => {
    renderView({ page: { ...page, geocodingPending: 4 } });
    expect(screen.getByRole("status")).toHaveTextContent("Locating 4 places");

    renderView({ error: true, page: undefined });
    expect(screen.getByText("We couldn't load the map")).toBeInTheDocument();
  });
});
