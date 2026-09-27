import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import * as contactLookupApi from "../../contactlookup/api/contactLookupApi";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, CandidateAiAssessment } from "../api/types";
import { CandidateDrawer } from "./CandidateDrawer";

vi.mock("../api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatesApi>()),
  getCandidate: vi.fn(),
  getAiAssessment: vi.fn(),
  requestAiEnrich: vi.fn(),
  updateCandidate: vi.fn(),
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
  note: null,
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

const assessed: CandidateAiAssessment = {
  summary: "A proven GCC finance leader.",
  technical: { score: 8, positives: ["Led a dairy IPO"], negatives: ["No energy exposure"] },
  behavioural: { score: null, positives: [], negatives: [] },
  assessedAt: "2026-09-20T10:00:00Z",
  nationalityReading: null,
  failedAt: null,
};

const unrecorded: Candidate = { ...yasmin, nationality: null };

const renderDrawer = (canWrite = true, candidate: Candidate = yasmin) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <CandidateDrawer
          open
          projectId="p1"
          candidate={candidate}
          company={null}
          customColumns={[]}
          canWrite={canWrite}
          onClose={() => {}}
          onSaved={() => {}}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );

/**
 * The profile's AI assessment: staff read two scores with their reasons, and a client seat is never
 * shown the fold or the button — the server would refuse the read anyway.
 */
describe("AI assessment", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(yasmin);
    vi.mocked(candidatesApi.getAiAssessment).mockResolvedValue(assessed);
    vi.mocked(candidatesApi.requestAiEnrich).mockResolvedValue(undefined);
    vi.mocked(contactLookupApi.getContactLookupConfig).mockResolvedValue({ enabled: false });
  });

  it("shows the summary, both panels and their reasons", async () => {
    renderDrawer();

    expect(await screen.findByText("A proven GCC finance leader.")).toBeInTheDocument();
    expect(screen.getByText("Led a dairy IPO")).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "Negatives" })).getByText("No energy exposure"))
      .toBeInTheDocument();
    expect(screen.getByText("Not enough in the brief or the evidence to judge.")).toBeInTheDocument();
  });

  it("queues a deep enrichment from the header button", async () => {
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText("A proven GCC finance leader.");

    await user.click(screen.getByRole("button", { name: "AI deep enrich" }));

    await waitFor(() => expect(candidatesApi.requestAiEnrich).toHaveBeenCalledWith("p1", "c1"));
    expect(await screen.findByRole("button", { name: /Enriching/ })).toBeDisabled();
  });

  it("says at once when the run fails, instead of waiting out the timeout", async () => {
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText("A proven GCC finance leader.");
    vi.mocked(candidatesApi.getAiAssessment).mockResolvedValue({
      ...assessed,
      failedAt: "2026-09-25T10:00:00Z",
    });

    await user.click(screen.getByRole("button", { name: "AI deep enrich" }));

    expect(await screen.findByText("The AI enrichment failed — try again", {}, { timeout: 5000 }))
      .toBeInTheDocument();
    expect(screen.getByRole("button", { name: "AI deep enrich" })).toBeEnabled();
    expect(screen.getByText(/Last AI enrichment failed/)).toBeInTheDocument();
  });

  it("offers a medium nationality reading as a suggestion, and Accept records it", async () => {
    const user = userEvent.setup();
    vi.mocked(candidatesApi.getAiAssessment).mockResolvedValue({
      ...assessed,
      nationalityReading: {
        category: "Arab expat, non-GCC",
        confidence: "medium",
        evidenceFor: ["BCom, Cairo University"],
        evidenceAgainst: ["Career entirely in Dubai"],
        rule: "B",
        readAt: "2026-09-27T10:00:00Z",
      },
    });
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(unrecorded);
    vi.mocked(candidatesApi.updateCandidate).mockResolvedValue({ ...unrecorded, nationality: "Arab expat, non-GCC" });
    renderDrawer(true, unrecorded);

    // The fold starts shut, so its one-line summary is where the suggestion is first seen.
    await user.click(await screen.findByRole("button", { name: /Background.*AI suggests Arab expat, non-GCC/ }));
    expect(await screen.findByText("Arab expat, non-GCC")).toBeInTheDocument();
    expect(screen.getByText("(medium confidence)")).toBeInTheDocument();
    expect(screen.getByText("BCom, Cairo University")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() => expect(candidatesApi.updateCandidate).toHaveBeenCalled());
    const payload = vi.mocked(candidatesApi.updateCandidate).mock.calls[0][2];
    expect(payload.nationality).toBe("Arab expat, non-GCC");
    expect(payload.confirmBackground).toBeUndefined();
  });

  it("says when the classifier could not tell, and offers nothing to accept", async () => {
    vi.mocked(candidatesApi.getAiAssessment).mockResolvedValue({
      ...assessed,
      nationalityReading: {
        category: "Unknown",
        confidence: "low",
        evidenceFor: [],
        evidenceAgainst: [],
        rule: "E",
        readAt: "2026-09-27T10:00:00Z",
      },
    });
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(unrecorded);
    renderDrawer(true, unrecorded);

    await userEvent.click(await screen.findByRole("button", { name: /^Background/ }));
    expect(await screen.findByText("AI couldn't tell this executive's nationality.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
  });

  it("shows no suggestion once a nationality is on the row", async () => {
    vi.mocked(candidatesApi.getAiAssessment).mockResolvedValue({
      ...assessed,
      nationalityReading: {
        category: "Western expat",
        confidence: "medium",
        evidenceFor: [],
        evidenceAgainst: [],
        rule: "none",
        readAt: "2026-09-27T10:00:00Z",
      },
    });
    renderDrawer();

    expect(await screen.findByText("A proven GCC finance leader.")).toBeInTheDocument();
    expect(screen.queryByText(/AI suggests nationality/)).not.toBeInTheDocument();
  });

  it("offers a client seat neither the fold nor the button, and never asks for the assessment", async () => {
    renderDrawer(false);

    expect(await screen.findByText("Yasmin El-Sayed", { selector: "h2" })).toBeInTheDocument();
    await waitFor(() => expect(candidatesApi.getCandidate).toHaveBeenCalled());
    expect(screen.queryByText("AI assessment")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "AI deep enrich" })).not.toBeInTheDocument();
    expect(candidatesApi.getAiAssessment).not.toHaveBeenCalled();
  });
});
