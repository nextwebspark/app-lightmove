import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as mailboxApi from "../api/mailboxApi";
import * as sequenceApi from "../api/sequenceApi";
import type { EnrollmentCandidate, Sequence } from "../api/sequenceApi";
import { EnrolDialog } from "./EnrolDialog";

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));

vi.mock("../api/sequenceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sequenceApi")>()),
  getSequences: vi.fn(),
  getEnrollmentCandidates: vi.fn(),
  draftOpeners: vi.fn(),
  startSequence: vi.fn(),
}));

const SEQUENCE: Sequence = {
  id: "s1",
  name: "CFO — first approach",
  steps: [
    { delayWorkingDays: 0, subject: "Confidential: {{positionTitle}}", body: "Hi {{firstName}},\n\n{{opener}}\n\nBye" },
    { delayWorkingDays: 3, subject: null, body: "Following up", sendTime: "09:30:00" },
  ],
  schedule: { days: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"], windowStart: "08:00:00", windowEnd: "18:00:00" },
  createdByName: "Yara Haddad",
  enrolledCount: 0,
  sentCount: 0,
  repliedCount: 0,
  updatedAt: "2026-10-01T09:00:00Z",
};

function person(candidateId: string, fullName: string, overrides: Partial<EnrollmentCandidate> = {}): EnrollmentCandidate {
  return {
    candidateId,
    personId: `person-${candidateId}`,
    triageCompanyId: "c1",
    fullName,
    title: "CFO",
    companyName: "Target Group",
    emails: [{ address: `${candidateId}@target.example`, kind: "work", verified: true }],
    skipReason: null,
    inSequence: null,
    tokens: {
      firstName: fullName.split(" ")[0],
      currentTitle: "CFO",
      currentCompany: "Target Group",
      positionTitle: "Group CFO",
      location: "Dubai",
      senderFirstName: "Yara",
    },
    ...overrides,
  };
}

function renderDialog() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <EnrolDialog projectId="p1" scope={{ triageCompanyIds: ["c1"] }} source="From 1 company" onClose={() => {}} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("EnrolDialog", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      offered: true,
      providers: ["google"],
      bookingLinkOffered: false,
      connection: {
        address: "yara@firm.example",
        provider: "google",
        status: "ACTIVE",
        dailyCap: 50,
        timeZone: "Asia/Dubai",
        connectedAt: "2026-10-01T09:00:00Z",
        movesOffNylas: false,
        runsStoppedByMove: 0,
      },
    });
    vi.mocked(sequenceApi.getSequences).mockResolvedValue([SEQUENCE]);
    vi.mocked(sequenceApi.draftOpeners).mockImplementation(async (_projectId, candidateIds) =>
      candidateIds.map((candidateId) => ({ candidateId, opener: `Drafted for ${candidateId}.` })),
    );
    vi.mocked(sequenceApi.startSequence).mockResolvedValue({
      enrolled: 2,
      firstSendAt: "2026-10-05T06:00:00Z",
      lastFirstSendAt: "2026-10-05T06:02:00Z",
    });
  });

  it("shows each skipped person with their reason and never ticks them", async () => {
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue([
      person("a", "Priya Raman"),
      person("b", "Omar Said", { emails: [], skipReason: "NO_EMAIL" }),
      person("c", "Lina Haddad", { skipReason: "DO_NOT_CONTACT" }),
      person("d", "Sara Nasser", { skipReason: "ALREADY_IN_SEQUENCE", inSequence: "Treasury bench" }),
      person("e", "Karim Aziz", { skipReason: "LEFT_THE_RUNNING" }),
    ]);
    renderDialog();

    expect(await screen.findByText("No email · Find email in the drawer")).toBeInTheDocument();
    expect(screen.getByText("Do not contact")).toBeInTheDocument();
    expect(screen.getByText("Already in Treasury bench")).toBeInTheDocument();
    expect(screen.getByText("Out of the running on this position")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Priya Raman" })).toBeChecked();
    for (const name of ["Omar Said", "Lina Haddad", "Sara Nasser", "Karim Aziz"]) {
      expect(screen.getByRole("checkbox", { name })).not.toBeChecked();
      expect(screen.getByRole("checkbox", { name })).toBeDisabled();
    }
    expect(screen.getByText("1 to add · 4 skipped")).toBeInTheDocument();
  });

  it("keeps an edit to one person's opener on that person alone, and starts each with their own", async () => {
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue([
      person("a", "Priya Raman"),
      person("b", "Rajesh Menon"),
    ]);
    renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Review 2 emails" }));
    const opener = await screen.findByRole("textbox", { name: "Opener" });
    await waitFor(() => expect(opener).toHaveValue("Drafted for a."));
    await userEvent.clear(opener);
    await userEvent.type(opener, "Your move into treasury stood out.");
    expect(screen.getByText("Edited")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByRole("textbox", { name: "Opener" })).toHaveValue("Drafted for b.");
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(await screen.findByRole("button", { name: "Start sequence" }));

    await waitFor(() =>
      expect(sequenceApi.startSequence).toHaveBeenCalledWith("p1", "s1", [
        { candidateId: "a", toAddress: "a@target.example", opener: "Your move into treasury stood out.", openerEdited: true },
        { candidateId: "b", toAddress: "b@target.example", opener: "Drafted for b.", openerEdited: false },
      ], { startMode: "NOW", startAt: null }),
    );
    expect(sequenceApi.draftOpeners).toHaveBeenCalledTimes(1);
    expect(sequenceApi.draftOpeners).toHaveBeenCalledWith("p1", ["a", "b"]);
  });

  it("starts at a chosen day and time, read in the sender's own zone", async () => {
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue([person("a", "Priya Raman")]);
    renderDialog();
    await userEvent.click(await screen.findByRole("button", { name: "Review 1 email" }));
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Opener" })).toHaveValue("Drafted for a."));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByRole("radio", { name: /^Now/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /Next sending window.*Mon–Fri, 08:00–18:00 Dubai time/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: /Pick a date and time/ }));
    const day = new Date(Date.now() + 10 * 86_400_000).toLocaleDateString("en-CA", { timeZone: "Asia/Dubai" });
    fireEvent.change(screen.getByLabelText("Start date"), { target: { value: day } });
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Start time" }), "19:30");
    expect(screen.getByText(/outside this sequence's sending schedule/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Start sequence" }));

    await waitFor(() =>
      expect(sequenceApi.startSequence).toHaveBeenCalledWith("p1", "s1", expect.any(Array), {
        startMode: "AT",
        startAt: new Date(`${day}T19:30:00+04:00`).toISOString(),
      }),
    );
  });

  it("will not start at a time already past", async () => {
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue([person("a", "Priya Raman")]);
    renderDialog();
    await userEvent.click(await screen.findByRole("button", { name: "Review 1 email" }));
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Opener" })).toHaveValue("Drafted for a."));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(await screen.findByRole("radio", { name: /Pick a date and time/ }));
    fireEvent.change(screen.getByLabelText("Start date"), { target: { value: "2020-01-06" } });

    expect(screen.getByText("Choose a time that is still ahead.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start sequence" })).toBeDisabled();
  });

  it("drafts nothing while more people are ticked than one Start may enroll", async () => {
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue(
      Array.from({ length: 51 }, (_, index) => person(`p${index}`, `Person ${index}`)),
    );
    renderDialog();

    expect(await screen.findByText("Add at most 50 people at a time — untick 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Review 51 emails" })).toBeDisabled();

    await userEvent.click(screen.getByRole("checkbox", { name: "Person 0" }));
    expect(screen.getByRole("button", { name: "Review 50 emails" })).toBeEnabled();
    expect(sequenceApi.draftOpeners).not.toHaveBeenCalled();
  });

  it("stops drafting after the first press fails, and keeps typed text through a failed redraft", async () => {
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue(
      Array.from({ length: 12 }, (_, index) => person(`p${index}`, `Person ${index}`)),
    );
    vi.mocked(sequenceApi.draftOpeners).mockRejectedValue(new Error("budget"));
    renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Review 12 emails" }));
    const opener = await screen.findByRole("textbox", { name: "Opener" });
    await waitFor(() => expect(opener).toHaveAttribute("placeholder", expect.stringContaining("couldn't be drafted")));
    expect(sequenceApi.draftOpeners).toHaveBeenCalledTimes(1);

    await userEvent.type(opener, "My own line.");
    await userEvent.click(screen.getByRole("button", { name: /Redraft/ }));
    await waitFor(() => expect(sequenceApi.draftOpeners).toHaveBeenCalledTimes(2));
    expect(opener).toHaveValue("My own line.");
    expect(screen.getByText("Edited")).toBeInTheDocument();
    expect(screen.queryByText("Opener redrafted.")).not.toBeInTheDocument();
  });

  it("will not review anyone until a mailbox is connected", async () => {
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ offered: true, providers: ["google"], connection: null, bookingLinkOffered: false });
    vi.mocked(sequenceApi.getEnrollmentCandidates).mockResolvedValue([person("a", "Priya Raman")]);
    renderDialog();

    expect(await screen.findByRole("link", { name: "Connect your mailbox" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Review 1 email" })).toBeDisabled();
  });
});
