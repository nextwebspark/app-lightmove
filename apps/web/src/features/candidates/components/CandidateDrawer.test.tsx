import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate } from "../api/types";
import { CandidateDrawer } from "./CandidateDrawer";

vi.mock("../api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatesApi>()),
  createCandidate: vi.fn(),
  updateCandidate: vi.fn(),
}));

const yasmin: Candidate = {
  id: "c1",
  triageCompanyId: "co1",
  companyName: "Al Rawabi Dairy",
  fullName: "Yasmin El-Sayed",
  title: "VP Finance",
  seniority: "N-1",
  status: "interested",
  email: "yasmin@example.com",
  phone: null,
  linkedinUrl: null,
  locationCountry: "UAE",
  locationCity: "Dubai",
  nationality: "Egyptian",
  yearsExperience: 18,
  summary: null,
  note: null,
  compensation: {
    currency: "AED",
    baseSalary: 420000,
    bonus: null,
    allowances: null,
    longTermIncentive: null,
    noticePeriod: "3 months",
  },
  career: [{ company: "Regional Foods Co.", title: "Finance Director", period: "2017–2021" }],
  languages: ["English", "Arabic"],
  source: "manual",
  sourceUrl: null,
  addedAt: "2026-08-02T09:00:00Z",
};

const renderDrawer = (props: Partial<Parameters<typeof CandidateDrawer>[0]> = {}) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <CandidateDrawer
          open
          projectId="p1"
          candidate={null}
          company={{ triageCompanyId: "co1", companyName: "Al Rawabi Dairy" }}
          onClose={() => {}}
          onSaved={() => {}}
          {...props}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );

/**
 * The profile form. Two things are worth holding onto here: only the name is required, and an edit is
 * a full replace — every field the drawer shows is a field the next save decides the value of.
 */
describe("CandidateDrawer", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("refuses a nameless executive without posting", async () => {
    renderDrawer();

    await userEvent.click(screen.getByRole("button", { name: /^Add executive$/i }));

    expect(await screen.findByText(/A name is required/i)).toBeInTheDocument();
    expect(candidatesApi.createCandidate).not.toHaveBeenCalled();
  });

  it("takes a name alone — research arrives in pieces", async () => {
    vi.mocked(candidatesApi.createCandidate).mockResolvedValue(yasmin);
    renderDrawer();

    await userEvent.type(screen.getByLabelText(/Full name/i), "Omar Haddad");
    await userEvent.click(screen.getByRole("button", { name: /^Add executive$/i }));

    await waitFor(() => expect(candidatesApi.createCandidate).toHaveBeenCalled());
    const payload = vi.mocked(candidatesApi.createCandidate).mock.calls[0][1];
    expect(payload.fullName).toBe("Omar Haddad");
    expect(payload.triageCompanyId).toBe("co1");
    // A blank field is an omission, not an empty string the network log would misreport as typed.
    expect(payload.title).toBeUndefined();
  });

  it("splits the language field and drops the blanks a trailing comma leaves", async () => {
    vi.mocked(candidatesApi.createCandidate).mockResolvedValue(yasmin);
    renderDrawer();

    await userEvent.type(screen.getByLabelText(/Full name/i), "Omar Haddad");
    await userEvent.type(screen.getByLabelText(/Languages/i), "English, Arabic, ");
    await userEvent.click(screen.getByRole("button", { name: /^Add executive$/i }));

    await waitFor(() => expect(candidatesApi.createCandidate).toHaveBeenCalled());
    expect(vi.mocked(candidatesApi.createCandidate).mock.calls[0][1].languages).toEqual([
      "English",
      "Arabic",
    ]);
  });

  it("adds and removes career rows, and posts only the ones filled in", async () => {
    vi.mocked(candidatesApi.createCandidate).mockResolvedValue(yasmin);
    renderDrawer();

    await userEvent.type(screen.getByLabelText(/Full name/i), "Omar Haddad");
    await userEvent.click(screen.getByRole("button", { name: /Add a post/i }));
    await userEvent.click(screen.getByRole("button", { name: /Add a post/i }));

    await userEvent.type(screen.getByLabelText(/Career 1 company/i), "Almarai");
    await userEvent.type(screen.getByLabelText(/Career 1 title/i), "CFO");
    // The second row is left blank — the empty trailing row every repeatable list grows.
    await userEvent.click(screen.getByRole("button", { name: /^Add executive$/i }));

    await waitFor(() => expect(candidatesApi.createCandidate).toHaveBeenCalled());
    expect(vi.mocked(candidatesApi.createCandidate).mock.calls[0][1].career).toEqual([
      { company: "Almarai", title: "CFO", period: null },
    ]);
  });

  it("refuses a LinkedIn address the server would silently drop", async () => {
    renderDrawer();

    await userEvent.type(screen.getByLabelText(/Full name/i), "Omar Haddad");
    await userEvent.type(screen.getByLabelText(/LinkedIn/i), "javascript:alert(1)");
    await userEvent.click(screen.getByRole("button", { name: /^Add executive$/i }));

    // The server drops it rather than refusing the write, which for a form means a typo posts,
    // toasts success and vanishes. Caught while the field is still on screen instead.
    expect(await screen.findByText(/does not look like a web address/i)).toBeInTheDocument();
    expect(candidatesApi.createCandidate).not.toHaveBeenCalled();
  });

  it("loads an existing profile whole, and saves it as a replace", async () => {
    vi.mocked(candidatesApi.updateCandidate).mockResolvedValue(yasmin);
    renderDrawer({ candidate: yasmin, company: null });

    expect(screen.getByLabelText(/Full name/i)).toHaveValue("Yasmin El-Sayed");
    expect(screen.getByLabelText(/Languages/i)).toHaveValue("English, Arabic");
    expect(screen.getByLabelText(/Career 1 company/i)).toHaveValue("Regional Foods Co.");
    expect(screen.getByLabelText(/^Base$/i)).toHaveValue("420000");

    await userEvent.clear(screen.getByLabelText(/^Base$/i));
    await userEvent.click(screen.getByRole("button", { name: /Save changes/i }));

    await waitFor(() => expect(candidatesApi.updateCandidate).toHaveBeenCalled());
    const [, candidateId, payload] = vi.mocked(candidatesApi.updateCandidate).mock.calls[0];
    expect(candidateId).toBe("c1");
    // A cleared figure is a cleared figure: the whole point of a replace over a merge.
    expect(payload.compensation?.baseSalary).toBeNull();
    // Editing someone from their own row must not quietly unmap them.
    expect(payload.triageCompanyId).toBe("co1");
  });
});
