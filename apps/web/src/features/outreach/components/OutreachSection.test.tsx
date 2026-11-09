import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as runApi from "../api/runApi";
import type { CandidateStatus } from "../../candidates/api/types";
import type { CandidateOutreach, OutreachRun } from "../api/runApi";
import { OutreachSection } from "./OutreachSection";

vi.mock("../api/runApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/runApi")>()),
  getCandidateOutreach: vi.fn(),
  stopRun: vi.fn(),
}));

const RUN: OutreachRun = {
  id: "r1",
  candidateId: "c1",
  personId: "person-1",
  fullName: "Fatima Al Mazrouei",
  title: "CFO",
  companyName: "Gulf Ports",
  candidateStatus: "contacted",
  sequenceId: "s1",
  sequenceName: "CFO — first approach",
  stepCount: 3,
  sentCount: 1,
  nextSendAt: "2026-10-08T05:00:00Z",
  lastSentAt: "2026-10-05T05:00:00Z",
  status: "ACTIVE",
  stopReason: null,
  endedAt: null,
  senderUserId: "u1",
  senderName: "Yara Haddad",
  bookedViaLink: false,
};

function renderSection(
  outreach: CandidateOutreach,
  onSetStatus = vi.fn(),
  candidateStatus: CandidateStatus = "contacted",
  path = "/projects/p1/universe",
  open = true,
) {
  const onToggle = vi.fn();
  vi.mocked(runApi.getCandidateOutreach).mockResolvedValue(outreach);
  render(
    <MemoryRouter initialEntries={[path]}>
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <OutreachSection
          projectId="p1"
          candidateId="c1"
          firstName="Fatima"
          candidateStatus={candidateStatus}
          open={open}
          onToggle={onToggle}
          isSettingStatus={false}
          onSetStatus={onSetStatus}
        />
      </ToastProvider>
    </QueryClientProvider>
    </MemoryRouter>,
  );
  return { onSetStatus, onToggle };
}

describe("OutreachSection", () => {
  beforeEach(() => vi.resetAllMocks());

  it("lists each step as sent, scheduled or waiting, and stops a running sequence", async () => {
    vi.mocked(runApi.stopRun).mockResolvedValue(undefined);
    renderSection({
      run: RUN,
      steps: [
        { number: 1, subject: "Confidential: CFO", state: "SENT", at: "2026-10-05T05:12:00Z", notSentBecause: null },
        { number: 2, subject: null, state: "SCHEDULED", at: "2026-10-08T05:00:00Z", notSentBecause: null },
        { number: 3, subject: null, state: "WAITING", at: null, notSentBecause: null },
      ],
    });

    const row = await screen.findByRole("button", { name: /CFO — first approach/ });
    expect(row).toHaveTextContent(/Yara Haddad · step 2 goes /);
    expect(row).toHaveTextContent("In sequence");
    expect(screen.getByRole("img", { name: "1 of 3 sent" })).toBeInTheDocument();
    expect(screen.getByText("Step 1 · sent")).toBeInTheDocument();
    expect(screen.getByText("Step 2 · scheduled")).toBeInTheDocument();
    expect(screen.getByText("Step 3 · waiting")).toBeInTheDocument();
    expect(screen.getByText("After step 2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open sequence" })).toHaveAttribute("href", "/projects/p1/outreach/sequences/s1");

    await userEvent.click(screen.getByRole("button", { name: "Stop sequence" }));
    const dialog = await screen.findByRole("dialog", { name: /^Stop .*'s sequence\?$/ });
    expect(dialog).toHaveTextContent("Remaining emails won't be sent. This can't be resumed.");
    expect(runApi.stopRun).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Stop sequence" }));
    await waitFor(() => expect(runApi.stopRun).toHaveBeenCalledWith("p1", "r1"));
    expect(await screen.findByText("Sequence stopped.")).toBeInTheDocument();
  });

  it("says once why the rest of a stopped sequence never went", async () => {
    renderSection({
      run: { ...RUN, status: "STOPPED", stopReason: "MAILBOX_INACTIVE", nextSendAt: null, endedAt: "2026-10-05T06:00:00Z" },
      steps: [
        { number: 1, subject: "Confidential: CFO", state: "SENT", at: "2026-10-05T05:12:00Z", notSentBecause: null },
        { number: 2, subject: null, state: "NOT_SENT", at: null, notSentBecause: "MAILBOX_INACTIVE" },
        { number: 3, subject: null, state: "NOT_SENT", at: null, notSentBecause: "MAILBOX_INACTIVE" },
      ],
    });

    const row = await screen.findByRole("button", { name: /CFO — first approach/ });
    expect(row).toHaveTextContent("stopped after step 1 — mailbox disconnected");
    expect(screen.getAllByText(/Mailbox disconnected/)).toHaveLength(1);
    expect(screen.getByText(/Steps 2 and 3 were not sent\./)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Stop sequence" })).not.toBeInTheDocument();
  });

  it("folds to its one row, which opens it", async () => {
    const onToggle = renderSection(
      {
        run: RUN,
        steps: [{ number: 1, subject: "Confidential: CFO", state: "SENT", at: "2026-10-05T05:12:00Z", notSentBecause: null }],
      },
      vi.fn(),
      "contacted",
      "/projects/p1/universe",
      false,
    ).onToggle;

    const row = await screen.findByRole("button", { name: /CFO — first approach/ });
    expect(row).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("link", { name: "Open sequence" })).not.toBeInTheDocument();
    await userEvent.click(row);
    expect(onToggle).toHaveBeenCalled();
  });

  it("asks where someone who replied stands, and marks the steps that never went", async () => {
    const { onSetStatus } = renderSection({
      run: { ...RUN, status: "REPLIED", nextSendAt: null, endedAt: "2026-10-06T10:20:00Z" },
      steps: [
        { number: 1, subject: "Confidential: CFO", state: "SENT", at: "2026-10-05T05:12:00Z", notSentBecause: null },
        { number: 2, subject: null, state: "NOT_SENT", at: null, notSentBecause: "REPLIED" },
        { number: 3, subject: null, state: "NOT_SENT", at: null, notSentBecause: "REPLIED" },
      ],
    });

    expect(await screen.findByText(/Fatima replied .*\./)).toBeInTheDocument();
    expect(screen.getByText("Step 2 · not sent")).toBeInTheDocument();
    expect(screen.getByText("Step 3 · not sent")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Stop sequence" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Interested" }));
    expect(onSetStatus).toHaveBeenCalledWith("interested");
  });

  it("offers no choice once the person has been moved on, so a reply never moves them back", async () => {
    renderSection(
      {
        run: { ...RUN, status: "REPLIED", nextSendAt: null, endedAt: "2026-10-06T10:20:00Z" },
        steps: [{ number: 1, subject: "Confidential: CFO", state: "SENT", at: null, notSentBecause: null }],
      },
      vi.fn(),
      "interested",
    );

    expect(await screen.findByText(/Fatima replied/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Engaged" })).not.toBeInTheDocument();
  });

  it("draws nothing for someone no sequence has reached for", async () => {
    renderSection({ run: null, steps: [] });

    await waitFor(() => expect(runApi.getCandidateOutreach).toHaveBeenCalled());
    expect(screen.queryByRole("region", { name: "Sequence" })).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Latest reply" })).not.toBeInTheDocument();
  });

  it("quotes their latest reply and links to the conversation, except from the conversation itself", async () => {
    const replied: CandidateOutreach = {
      run: { ...RUN, status: "REPLIED", endedAt: "2026-10-06T08:00:00Z" },
      steps: [],
      latestReply: { at: "2026-10-06T08:00:00Z", text: "Thursday works for me." },
    };
    renderSection(replied);

    expect(await screen.findByText("Thursday works for me.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open conversation" })).toHaveAttribute(
      "href",
      "/projects/p1/outreach?candidate=c1",
    );
  });

  it("offers no link back from the Outreach page, and sends to the inbox where the words are not kept", async () => {
    renderSection(
      { run: { ...RUN, status: "REPLIED", endedAt: "2026-10-06T08:00:00Z" }, steps: [], latestReply: { at: "2026-10-06T08:00:00Z", text: null } },
      vi.fn(),
      "contacted",
      "/projects/p1/outreach",
    );

    expect(await screen.findByText(/Read it in your inbox/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open conversation" })).not.toBeInTheDocument();
  });

  it("quotes a reply to an email sent by hand, with no sequence behind it", async () => {
    renderSection({ run: null, steps: [], latestReply: { at: "2026-10-06T08:00:00Z", text: "Happy to talk." } });

    expect(await screen.findByText("Happy to talk.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open conversation" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Stop sequence" })).not.toBeInTheDocument();
  });

  it("quotes a reply that came after the sequence ended", async () => {
    renderSection({
      run: { ...RUN, status: "COMPLETED", sentCount: 3, endedAt: "2026-10-02T08:00:00Z" },
      steps: [],
      latestReply: { at: "2026-10-09T08:00:00Z", text: "Sorry for the slow answer." },
    });

    expect(await screen.findByText("Sorry for the slow answer.")).toBeInTheDocument();
    expect(screen.getByText(/Fatima replied/)).toBeInTheDocument();
  });
});
