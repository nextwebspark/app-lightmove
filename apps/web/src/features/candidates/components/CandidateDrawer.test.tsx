import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import { useState } from "react";
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
  education: [],
  skills: [],
  source: "manual",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-08-02T09:00:00Z",
  enrichedAt: null,
};

const renderDrawer = (
  props: Partial<Parameters<typeof CandidateDrawer>[0]> = {},
  Panel: typeof CandidateDrawer = CandidateDrawer,
) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <Panel
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
 * What the page does with a save's answer: shows it. The panel renders whatever it is handed, so a
 * test of "the profile now reads as saved" needs a caller that hands the answer back.
 */
function LiveDrawer(props: Parameters<typeof CandidateDrawer>[0]) {
  const [candidate, setCandidate] = useState(props.candidate);
  return (
    <CandidateDrawer
      {...props}
      candidate={candidate}
      onSaved={(saved) => {
        setCandidate(saved);
        props.onSaved(saved);
      }}
    />
  );
}

/**
 * The profile panel and the form behind it. Three things are worth holding onto: a name opens a
 * profile rather than a form, status is the one control that stays live while reading, and a section
 * is saved over the stored profile — the wire is a full replace, so what a section does not show is
 * said again exactly as it was.
 */
describe("CandidateDrawer", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    // The folds are remembered per viewer; one test's folding must not reach the next.
    localStorage.clear();
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

  it("corrects one section over the stored profile, and keeps the panel open", async () => {
    const onSaved = vi.fn();
    const onClose = vi.fn();
    vi.mocked(candidatesApi.updateCandidate).mockResolvedValue({
      ...yasmin,
      compensation: { ...yasmin.compensation, baseSalary: 500000 },
    });
    renderDrawer({ candidate: yasmin, company: null, onSaved, onClose }, LiveDrawer);

    await userEvent.click(screen.getByRole("button", { name: /Edit compensation/i }));

    // The section's fields, as stored, with the figures in the shape they are read in.
    expect(screen.getByLabelText(/^Currency$/i)).toHaveValue("AED");
    expect(screen.getByLabelText(/^Base$/i)).toHaveValue("420,000");
    // The rest of the profile stays readable around it — this is not the old whole-record form.
    expect(screen.getByText("Regional Foods Co.")).toBeInTheDocument();
    expect(screen.queryByLabelText(/Full name/i)).not.toBeInTheDocument();
    // Every other pencil waits its turn.
    expect(screen.getByRole("button", { name: /Edit contact/i })).toBeDisabled();

    await userEvent.clear(screen.getByLabelText(/^Base$/i));
    await userEvent.type(screen.getByLabelText(/^Base$/i), "500,000");
    // The total follows the typing, so a figure can be checked against what was said on the phone.
    expect(screen.getByTestId("package-total")).toHaveTextContent("AED 500,000");
    await userEvent.click(screen.getByRole("button", { name: /^Save$/i }));

    await waitFor(() => expect(candidatesApi.updateCandidate).toHaveBeenCalled());
    const [, candidateId, payload] = vi.mocked(candidatesApi.updateCandidate).mock.calls[0];
    expect(candidateId).toBe("c1");
    expect(payload.compensation?.baseSalary).toBe(500000);
    // The wire is still a full replace, so what the section did not show is said again unchanged.
    expect(payload.fullName).toBe("Yasmin El-Sayed");
    expect(payload.languages).toEqual(["English", "Arabic"]);
    expect(payload.triageCompanyId).toBe("co1");
    // And the columns are left to their own section: omitted, the server leaves every one alone.
    expect(payload.customFields).toBeUndefined();

    // The reader had not finished: the panel shows what the server now holds, and stays.
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ id: "c1" }));
    expect(onClose).not.toHaveBeenCalled();
    // Twice: the Base tile and the total, which for a package with only a base are equal.
    expect(await screen.findAllByText("AED 500,000")).toHaveLength(2);
    expect(screen.queryByLabelText(/^Base$/i)).not.toBeInTheDocument();
  });

  it("clears a figure that was blanked — a replace, not a merge", async () => {
    vi.mocked(candidatesApi.updateCandidate).mockResolvedValue(yasmin);
    renderDrawer({ candidate: yasmin, company: null });

    await userEvent.click(screen.getByRole("button", { name: /Edit compensation/i }));
    await userEvent.clear(screen.getByLabelText(/^Base$/i));
    await userEvent.click(screen.getByRole("button", { name: /^Save$/i }));

    await waitFor(() => expect(candidatesApi.updateCandidate).toHaveBeenCalled());
    expect(vi.mocked(candidatesApi.updateCandidate).mock.calls[0][2].compensation?.baseSalary).toBeNull();
  });

  it("offers the currency as a pick, and keeps a stored code the list does not carry", async () => {
    renderDrawer({
      candidate: { ...yasmin, compensation: { ...yasmin.compensation, currency: "INR" } },
      company: null,
    });

    await userEvent.click(screen.getByRole("button", { name: /Edit compensation/i }));

    // A select whose value matched no option would post blank and clear a fact nobody touched.
    const currency = screen.getByLabelText(/^Currency$/i);
    expect(currency).toHaveValue("INR");
    expect(within(currency).getByRole("option", { name: "AED" })).toBeInTheDocument();
    expect(within(currency).getByRole("option", { name: "Not set" })).toBeInTheDocument();
  });

  it("saves the note on its own, the moment it differs from what is stored", async () => {
    const onSaved = vi.fn();
    vi.mocked(candidatesApi.updateCandidate).mockResolvedValue({ ...yasmin, note: "Call in May" });
    renderDrawer({ candidate: yasmin, company: null, onSaved });

    // No pencil: the note is always a textarea, and Save appears with the first keystroke.
    expect(screen.queryByRole("button", { name: /Save note/i })).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Note on this executive/i), "Call in May");
    await userEvent.click(screen.getByRole("button", { name: /Save note/i }));

    await waitFor(() => expect(candidatesApi.updateCandidate).toHaveBeenCalled());
    const payload = vi.mocked(candidatesApi.updateCandidate).mock.calls[0][2];
    expect(payload.note).toBe("Call in May");
    expect(payload.fullName).toBe("Yasmin El-Sayed");
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ note: "Call in May" }));
    // Stored now, so there is nothing left to save.
    expect(screen.queryByRole("button", { name: /Save note/i })).not.toBeInTheDocument();
  });

  it("moves on to the profile it added rather than back to the grid", async () => {
    const onSaved = vi.fn();
    const onClose = vi.fn();
    vi.mocked(candidatesApi.createCandidate).mockResolvedValue(yasmin);
    renderDrawer({ onSaved, onClose });

    await userEvent.type(screen.getByLabelText(/Full name/i), "Yasmin El-Sayed");
    await userEvent.click(screen.getByRole("button", { name: /^Add executive$/i }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(yasmin));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("opens an existing executive as a profile, not as a form", async () => {
    renderDrawer({ candidate: yasmin, company: null });

    // A consultant clicking a name is reading. A form that opens on every click is one you dismiss
    // without looking at.
    expect(screen.getByRole("heading", { name: "Yasmin El-Sayed" })).toBeInTheDocument();
    expect(screen.getByText("Regional Foods Co.")).toBeInTheDocument();
    expect(screen.getByText("3 months")).toBeInTheDocument();
    expect(screen.getByText("English")).toBeInTheDocument();
    expect(screen.getByText("Arabic")).toBeInTheDocument();
    // Twice: the Base tile and the section's total, which for a package with only a base are equal.
    expect(screen.getAllByText(/AED 420,000/)).toHaveLength(2);
    expect(screen.queryByLabelText(/Full name/i)).not.toBeInTheDocument();
  });

  it("reads a career as a timeline: one employer for consecutive posts, the open one flagged", async () => {
    renderDrawer({
      candidate: {
        ...yasmin,
        career: [
          { company: "Almarai", title: "CFO", period: "Jan 2021 – Present" },
          { company: "Almarai", title: "Finance Director", period: "2017 – 2021" },
          { company: "Regional Foods Co.", title: "Controller", period: "2012 – 2017" },
        ],
      },
      company: null,
    });

    // Two posts, one employer heading — the way a profile shows a promotion.
    expect(screen.getAllByText("Almarai")).toHaveLength(1);
    expect(screen.getByText("CFO")).toBeInTheDocument();
    expect(screen.getByText("Finance Director")).toBeInTheDocument();
    expect(screen.getByText("Current")).toBeInTheDocument();
    // The raw period is what the source said; the tenure is worked out beside it, never instead.
    expect(screen.getByText("2017 – 2021")).toBeInTheDocument();
    expect(screen.getByText("· 4 yrs")).toBeInTheDocument();
  });

  it("shows four posts of a long history and offers the rest", async () => {
    const career = Array.from({ length: 6 }, (_, index) => ({
      company: `Employer ${index + 1}`,
      title: `Post ${index + 1}`,
      period: null,
    }));
    renderDrawer({ candidate: { ...yasmin, career }, company: null });

    expect(screen.getByText("Post 4")).toBeInTheDocument();
    expect(screen.queryByText("Post 5")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Show all 6 posts/i }));

    expect(screen.getByText("Post 6")).toBeInTheDocument();
  });

  it("folds a section on its header, and keeps that fold for the next profile", async () => {
    const { unmount } = renderDrawer({ candidate: yasmin, company: null });

    const summary = screen.getByRole("button", { name: /^Summary/ });
    expect(summary).toHaveAttribute("aria-expanded", "true");
    await userEvent.click(summary);
    expect(summary).toHaveAttribute("aria-expanded", "false");

    // A fold is the reader's preference, not a fact about one candidate.
    unmount();
    renderDrawer({ candidate: { ...yasmin, id: "c2", fullName: "Omar Haddad" }, company: null });
    expect(screen.getByRole("button", { name: /^Summary/ })).toHaveAttribute(
      "aria-expanded",
      "false",
    );

    await userEvent.click(screen.getByRole("button", { name: /Expand all/i }));
    expect(screen.getByRole("button", { name: /^Summary/ })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
  });

  it("shows education and skills only when research produced them", async () => {
    const { unmount } = renderDrawer({ candidate: yasmin, company: null });
    // Nothing edits them, so an empty section would nag about something nobody here can supply.
    expect(screen.queryByRole("button", { name: /^Education/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/^Skills$/)).not.toBeInTheDocument();
    unmount();

    renderDrawer({
      candidate: {
        ...yasmin,
        education: [{ school: "AUC", degree: "MBA, Finance", period: "2010 – 2012" }],
        skills: ["Financial Planning"],
      },
      company: null,
    });

    expect(screen.getByRole("button", { name: /^Education/ })).toBeInTheDocument();
    expect(screen.getByText("MBA, Finance")).toBeInTheDocument();
    expect(screen.getByText("Financial Planning")).toBeInTheDocument();
  });

  it("shows the mandate's own columns on the profile, read-only", async () => {
    const { unmount } = renderDrawer({ candidate: yasmin, company: null });
    expect(screen.queryByRole("button", { name: /Your columns/ })).not.toBeInTheDocument();
    unmount();

    renderDrawer({
      candidate: { ...yasmin, customFields: { board_seats: "3", chartered: "true" } },
      company: null,
      customColumns: [
        {
          id: "col1",
          target: "candidate",
          fieldKey: "board_seats",
          label: "Board seats",
          dataType: "number",
          displayOrder: 0,
          hidden: false,
        },
        {
          id: "col2",
          target: "candidate",
          fieldKey: "chartered",
          label: "Chartered",
          dataType: "boolean",
          displayOrder: 1,
          hidden: false,
        },
      ],
    });

    expect(screen.getByText("Board seats")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("Yes")).toBeInTheDocument();
    // Values are edited through Edit like every other field, never in the profile.
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("will not render a stored profile URL a browser should not follow", async () => {
    // The write-side gate is covered by `aHostileProfileUrlIsDropped` on the server. This is the other
    // half: a value stored before that gate existed — or posted by the plugin — must not reach an href
    // just because the render side trusted the writer.
    renderDrawer({
      candidate: { ...yasmin, linkedinUrl: "javascript:alert(1)" },
      company: null,
    });

    // Folded sections are hidden from the accessibility tree; `hidden` looks inside them too.
    expect(screen.queryByRole("link", { hidden: true })).not.toBeInTheDocument();
    expect(screen.queryByText("javascript:alert(1)")).not.toBeInTheDocument();
  });

  it("renders a real profile URL as a link, beside the name and under Contact", async () => {
    renderDrawer({
      candidate: { ...yasmin, linkedinUrl: "https://linkedin.com/in/yasmin" },
      company: null,
    });

    expect(screen.getByRole("link", { name: /LinkedIn profile/i })).toHaveAttribute(
      "href",
      "https://linkedin.com/in/yasmin",
    );

    await userEvent.click(screen.getByRole("button", { name: /^Contact/ }));
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
    expect(screen.queryByRole("button", { name: /^Edit / })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Status$/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Note on this executive/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Remove from mandate/i })).not.toBeInTheDocument();
  });

  it("cancels a section on Escape and leaves the panel open", async () => {
    const onClose = vi.fn();
    renderDrawer({ candidate: yasmin, company: null, onClose });

    await userEvent.click(screen.getByRole("button", { name: /Edit summary/i }));
    expect(screen.getByLabelText(/Profile summary/i)).toBeInTheDocument();
    await userEvent.keyboard("{Escape}");

    // Escape means "not this section", not "not this person": the reader had not finished.
    expect(screen.queryByLabelText(/Profile summary/i)).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Yasmin El-Sayed" })).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();

    // Cancel does the same.
    await userEvent.click(screen.getByRole("button", { name: /Edit summary/i }));
    await userEvent.click(screen.getByRole("button", { name: /^Cancel$/i }));
    expect(screen.queryByLabelText(/Profile summary/i)).not.toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });
});
