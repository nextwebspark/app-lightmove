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
  changeCandidateStatus: vi.fn(),
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
  customFields: {},
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
          customColumns={[]}
          canWrite
          onClose={() => {}}
          onSaved={() => {}}
          {...props}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );

/**
 * The profile panel and the form behind it. Three things are worth holding onto: a name opens a
 * profile rather than a form, status is the one control that stays live while reading, and an edit is
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

    await userEvent.click(screen.getByRole("button", { name: /^Edit$/i }));

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

  it("opens an existing executive as a profile, not as a form", async () => {
    renderDrawer({ candidate: yasmin, company: null });

    // A consultant clicking a name is reading. A form that opens on every click is one you dismiss
    // without looking at.
    expect(screen.getByRole("heading", { name: "Yasmin El-Sayed" })).toBeInTheDocument();
    expect(screen.getByText("Regional Foods Co.")).toBeInTheDocument();
    expect(screen.getByText("3 months")).toBeInTheDocument();
    expect(screen.getByText("English, Arabic")).toBeInTheDocument();
    // Twice: the Base tile and the section's total, which for a package with only a base are equal.
    expect(screen.getAllByText(/AED 420,000/)).toHaveLength(2);
    expect(screen.queryByLabelText(/Full name/i)).not.toBeInTheDocument();
  });

  it("will not render a stored profile URL a browser should not follow", async () => {
    // The write-side gate is covered by `aHostileProfileUrlIsDropped` on the server. This is the other
    // half: a value stored before that gate existed — or posted by the plugin — must not reach an href
    // just because the render side trusted the writer.
    renderDrawer({
      candidate: { ...yasmin, linkedinUrl: "javascript:alert(1)" },
      company: null,
    });

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByText("javascript:alert(1)")).not.toBeInTheDocument();
  });

  it("renders a real profile URL as a link", async () => {
    renderDrawer({
      candidate: { ...yasmin, linkedinUrl: "https://linkedin.com/in/yasmin" },
      company: null,
    });

    expect(screen.getByRole("link", { name: /linkedin.com\/in\/yasmin/i })).toHaveAttribute(
      "href",
      "https://linkedin.com/in/yasmin",
    );
  });

  it("keeps status live while reading, without replacing the profile", async () => {
    vi.mocked(candidatesApi.changeCandidateStatus).mockResolvedValue({
      ...yasmin,
      status: "contacted",
    });
    renderDrawer({ candidate: yasmin, company: null });

    await userEvent.selectOptions(screen.getByLabelText(/^Status$/i), "contacted");

    await waitFor(() =>
      expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "c1", "contacted"),
    );
    // The point of the separate write: a pill flicked while reading must not re-submit a profile that
    // has been on screen for a while.
    expect(candidatesApi.updateCandidate).not.toHaveBeenCalled();
  });

  it("gives a reader who cannot write the profile and neither control", async () => {
    renderDrawer({ candidate: yasmin, company: null, canWrite: false });

    expect(screen.getByRole("heading", { name: "Yasmin El-Sayed" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Edit$/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Status$/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Remove from mandate/i })).not.toBeInTheDocument();
  });

  it("returns to the profile when an edit is cancelled, rather than to the grid", async () => {
    const onClose = vi.fn();
    renderDrawer({ candidate: yasmin, company: null, onClose });

    await userEvent.click(screen.getByRole("button", { name: /^Edit$/i }));
    await userEvent.click(screen.getByRole("button", { name: /^Cancel$/i }));

    // The reader had not finished reading.
    expect(screen.getByRole("heading", { name: "Yasmin El-Sayed" })).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });
});
