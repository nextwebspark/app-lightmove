import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import * as contactLookupApi from "../../contactlookup/api/contactLookupApi";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, NationalityReading } from "../api/types";
import { CandidateDrawer } from "./CandidateDrawer";

vi.mock("../api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatesApi>()),
  getCandidate: vi.fn(),
  getNationalityReading: vi.fn(),
  suggestBackground: vi.fn(),
  updateCandidate: vi.fn(),
}));

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({
    user: { id: "u1", fullName: "Yara Haddad", avatarUrl: null, workspace: { roles: ["ADMIN"], mode: "COMPANY" } },
  }),
}));

vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getCandidateTasks: async () => ({ open: [], done: [], doneCount: 0 }),
}));

vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: async () => [],
}));

vi.mock("../../contactlookup/api/contactLookupApi", async (importOriginal) => ({
  ...(await importOriginal<typeof contactLookupApi>()),
  getContactLookupConfig: vi.fn(),
}));

const yasmin: Candidate = {
  id: "c1",
  triageCompanyId: "co1",
  companyName: "Al Rawabi Dairy",
  fullName: "Yasmin El-Sayed",
  title: "VP Finance",
  seniority: "N-1",
  status: "interested",
  linkedinUrl: null,
  locationCountry: "UAE",
  locationCity: "Dubai",
  nationality: "Egyptian",
  gender: "female",
  yearsExperience: 18,
  aiInferredFields: [],
  summary: null,
  personId: "person-1",
  linkedinUrlLocked: false,
  compensation: {
    currency: "AED",
    baseSalary: 420000,
    bonus: null,
    allowances: null,
    longTermIncentive: null,
    noticePeriod: "3 months",
    allowanceLines: [],
    longTermIncentiveTypes: [],
  },
  career: [{ company: "Regional Foods Co.", title: "Finance Director", period: "2017–2021" }],
  languages: ["English", "Arabic"],
  education: [],
  skills: [],
  source: "manual",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-08-02T09:00:00Z",
  enrichedAt: null,
  contacts: {
    emails: [
      {
        address: "yasmin@example.com",
        kind: null,
        verified: false,
        status: null,
        source: "manual",
        foundAt: "2026-08-02T09:00:00Z",
      },
    ],
    phones: [],
    emailsLookedUpAt: null,
    phonesLookedUpAt: null,
    source: null,
  },
};

const unrecorded: Candidate = { ...yasmin, nationality: null };

const reading = (overrides: Partial<NationalityReading>): NationalityReading => ({
  category: "Arab expat, non-GCC",
  confidence: "medium",
  evidenceFor: [],
  evidenceAgainst: [],
  rule: "B",
  readAt: "2026-09-27T10:00:00Z",
  ...overrides,
});

let client: QueryClient;

/** The reading's query settled, so an absence asserted next is the answer and not the first render. */
const readingSettled = () =>
  waitFor(() =>
    expect(client.getQueryState(candidatesApi.NATIONALITY_READING_KEY("p1", "c1"))?.status).toMatch(/success|error/),
  );

const renderDrawer = (canWrite = true, candidate: Candidate = yasmin, onSaved = () => {}) =>
  render(
    <MemoryRouter>
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CandidateDrawer
          open
          projectId="p1"
          candidate={candidate}
          company={null}
          customColumns={[]}
          canWrite={canWrite}
          onClose={() => {}}
          onSaved={onSaved}
        />
      </ToastProvider>
    </QueryClientProvider>
    </MemoryRouter>,
  );

/**
 * The Background section's AI: Suggest with AI in its header while something is still missing that the AI has not
 * read, and the nationality classifier's reading offered as a suggestion. A client seat is offered neither.
 */
describe("Background AI", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(yasmin);
    vi.mocked(candidatesApi.getNationalityReading).mockResolvedValue(null);
    vi.mocked(contactLookupApi.getContactLookupConfig).mockResolvedValue({ enabled: false });
  });

  it("offers Suggest with AI while background is missing, and hands back what it filled", async () => {
    const user = userEvent.setup();
    const missing: Candidate = { ...yasmin, gender: null, yearsExperience: null };
    const filled: Candidate = { ...missing, gender: "female", yearsExperience: 18, aiInferredFields: ["gender"] };
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(missing);
    vi.mocked(candidatesApi.suggestBackground).mockResolvedValue(filled);
    const onSaved = vi.fn();
    renderDrawer(true, missing, onSaved);

    await user.click(await screen.findByRole("button", { name: "Suggest with AI" }));

    await waitFor(() => expect(candidatesApi.suggestBackground).toHaveBeenCalledWith("p1", "c1"));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(filled));
  });

  it("offers nothing once the background is complete", async () => {
    renderDrawer();

    await readingSettled();
    expect(screen.queryByRole("button", { name: "Suggest with AI" })).not.toBeInTheDocument();
  });

  it("still offers Suggest with AI for a missing gender when the nationality reading cannot be read", async () => {
    const missing: Candidate = { ...yasmin, gender: null };
    vi.mocked(candidatesApi.getNationalityReading).mockRejectedValue(new Error("offline"));
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(missing);
    renderDrawer(true, missing);

    await readingSettled();
    expect(screen.getByRole("button", { name: "Suggest with AI" })).toBeEnabled();
  });

  it("holds the Background pencil while the AI writes, so its form cannot save over the answer", async () => {
    const missing: Candidate = { ...yasmin, gender: null };
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(missing);
    vi.mocked(candidatesApi.suggestBackground).mockReturnValue(new Promise(() => {}));
    renderDrawer(true, missing);

    await userEvent.click(await screen.findByRole("button", { name: "Suggest with AI" }));

    expect(await screen.findByRole("button", { name: "Edit background" })).toBeDisabled();
  });

  it("offers a medium nationality reading as a suggestion, and Accept records it", async () => {
    const user = userEvent.setup();
    vi.mocked(candidatesApi.getNationalityReading).mockResolvedValue(
      reading({ evidenceFor: ["BCom, Cairo University"], evidenceAgainst: ["Career entirely in Dubai"] }),
    );
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(unrecorded);
    vi.mocked(candidatesApi.updateCandidate).mockResolvedValue({ ...unrecorded, nationality: "Arab expat, non-GCC" });
    renderDrawer(true, unrecorded);

    // The fold starts shut, so its one-line summary is where the suggestion is first seen.
    await user.click(await screen.findByRole("button", { name: /Background.*AI suggests Arab expat, non-GCC/ }));
    expect(await screen.findByText("Arab expat, non-GCC")).toBeInTheDocument();
    expect(screen.getByText("(medium confidence)")).toBeInTheDocument();
    expect(screen.getByText("BCom, Cairo University")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Suggest with AI" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() => expect(candidatesApi.updateCandidate).toHaveBeenCalled());
    const payload = vi.mocked(candidatesApi.updateCandidate).mock.calls[0][2];
    expect(payload.nationality).toBe("Arab expat, non-GCC");
    expect(payload.confirmBackground).toBeUndefined();
  });

  it("says when the classifier could not tell, and offers nothing to accept", async () => {
    vi.mocked(candidatesApi.getNationalityReading).mockResolvedValue(
      reading({ category: "Unknown", confidence: "low", rule: "E" }),
    );
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(unrecorded);
    renderDrawer(true, unrecorded);

    await userEvent.click(await screen.findByRole("button", { name: /^Background/ }));
    expect(await screen.findByText("AI couldn't tell this executive's nationality.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
  });

  it("shows no suggestion once a nationality is on the row", async () => {
    vi.mocked(candidatesApi.getNationalityReading).mockResolvedValue(reading({ category: "Western expat" }));
    renderDrawer();

    await readingSettled();
    await userEvent.click(await screen.findByRole("button", { name: /^Background/ }));
    expect(screen.queryByText(/AI suggests nationality/)).not.toBeInTheDocument();
  });

  it("offers a client seat no AI and never asks for the reading", async () => {
    renderDrawer(false, { ...yasmin, gender: null });

    expect(await screen.findByText("Yasmin El-Sayed", { selector: "h2" })).toBeInTheDocument();
    await waitFor(() => expect(candidatesApi.getCandidate).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Suggest with AI" })).not.toBeInTheDocument();
    expect(candidatesApi.getNationalityReading).not.toHaveBeenCalled();
  });
});
