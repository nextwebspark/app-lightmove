import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as runApi from "../api/runApi";
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
};

function renderSection(outreach: CandidateOutreach, onSetStatus = vi.fn()) {
  vi.mocked(runApi.getCandidateOutreach).mockResolvedValue(outreach);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <OutreachSection
          projectId="p1"
          candidateId="c1"
          firstName="Fatima"
          open
          onToggle={() => {}}
          isSettingStatus={false}
          onSetStatus={onSetStatus}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return onSetStatus;
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

    expect(await screen.findByText("Step 1 · Confidential: CFO")).toBeInTheDocument();
    expect(screen.getByText(/^Sent /)).toBeInTheDocument();
    expect(screen.getByText(/^Scheduled /)).toBeInTheDocument();
    expect(screen.getByText("Waiting")).toBeInTheDocument();
    expect(screen.getByText("CFO — first approach · from Yara Haddad")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Stop sequence" }));
    await waitFor(() => expect(runApi.stopRun).toHaveBeenCalledWith("p1", "r1"));
    expect(await screen.findByText("Sequence stopped.")).toBeInTheDocument();
  });

  it("asks where someone who replied stands, and says why the rest never went", async () => {
    const onSetStatus = renderSection({
      run: { ...RUN, status: "REPLIED", nextSendAt: null, endedAt: "2026-10-06T10:20:00Z" },
      steps: [
        { number: 1, subject: "Confidential: CFO", state: "SENT", at: "2026-10-05T05:12:00Z", notSentBecause: null },
        { number: 2, subject: null, state: "NOT_SENT", at: null, notSentBecause: "REPLIED" },
        { number: 3, subject: null, state: "NOT_SENT", at: null, notSentBecause: "REPLIED" },
      ],
    });

    expect(await screen.findByText(/Fatima replied/)).toBeInTheDocument();
    expect(screen.getAllByText("Not sent: they replied")).toHaveLength(2);
    expect(screen.queryByRole("button", { name: "Stop sequence" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Interested" }));
    expect(onSetStatus).toHaveBeenCalledWith("interested");
  });

  it("draws nothing for someone no sequence has reached for", async () => {
    renderSection({ run: null, steps: [] });

    await waitFor(() => expect(runApi.getCandidateOutreach).toHaveBeenCalled());
    expect(screen.queryByText("Outreach")).not.toBeInTheDocument();
  });
});
