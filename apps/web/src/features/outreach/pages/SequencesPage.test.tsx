import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as mailboxApi from "../api/mailboxApi";
import * as runApi from "../api/runApi";
import type { OutreachOverview, OutreachRun } from "../api/runApi";
import * as sequenceApi from "../api/sequenceApi";
import { SequencesPage } from "./SequencesPage";

vi.mock("../api/sequenceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sequenceApi")>()),
  getSequences: vi.fn(),
}));

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));

vi.mock("../api/runApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/runApi")>()),
  getOutreachPeople: vi.fn(),
  stopRun: vi.fn(),
}));

vi.mock("../../candidates/components/CandidateDrawerById", () => ({
  CandidateDrawerById: ({ candidateId }: { candidateId: string | null }) =>
    candidateId ? <div role="dialog" aria-label={`Drawer for ${candidateId}`} /> : null,
}));

const PROJECT = { id: "p1", positionTitle: "Group CFO" };

const NOBODY: OutreachOverview = {
  counts: { enrolled: 0, emailsSent: 0, reached: 0, replied: 0, inFlight: 0, bounced: 0, stopped: 0, booked: 0 },
  nextSendAt: null,
  people: [],
};

function run(overrides: Partial<OutreachRun>): OutreachRun {
  return {
    id: "r1",
    candidateId: "c1",
    personId: "person-1",
    fullName: "Omar Farouk",
    title: "CFO",
    companyName: "Gulf Ports",
    candidateStatus: "contacted",
    sequenceId: "s1",
    sequenceName: "CFO — first approach",
    stepCount: 3,
    sentCount: 2,
    nextSendAt: "2026-10-09T05:00:00Z",
    lastSentAt: "2026-10-05T05:00:00Z",
    status: "ACTIVE",
    stopReason: null,
    endedAt: null,
    senderUserId: "u1",
    senderName: "Yara Haddad",
    bookedViaLink: false,
    ...overrides,
  };
}

const WITH_PEOPLE: OutreachOverview = {
  counts: { enrolled: 3, emailsSent: 4, reached: 3, replied: 1, inFlight: 1, bounced: 0, stopped: 1, booked: 0 },
  nextSendAt: "2026-10-09T05:00:00Z",
  people: [
    run({}),
    run({ id: "r2", candidateId: "c2", fullName: "Fatima Al Mazrouei", status: "REPLIED", sentCount: 1,
      nextSendAt: null, endedAt: "2026-10-06T10:20:00Z" }),
    run({ id: "r3", candidateId: "c3", fullName: "James Whitfield", status: "STOPPED", stopReason: "DO_NOT_CONTACT",
      sentCount: 1, nextSendAt: null }),
  ],
};

function Where() {
  return <output aria-label="Location">{useLocation().pathname}</output>;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/projects/p1/outreach/sequences"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project: PROJECT }} />}>
              <Route
                path="*"
                element={
                  <>
                    <SequencesPage />
                    <Where />
                  </>
                }
              />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SequencesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(sequenceApi.getSequences).mockResolvedValue([]);
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(NOBODY);
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      offered: true,
      providers: ["google"],
      connection: null,
      bookingLinkOffered: false,
    });
  });

  it("is the header's Sequences view, a click from the conversations and from writing a new one", async () => {
    renderPage();

    const views = await screen.findByRole("radiogroup", { name: "Outreach view" });
    expect(within(views).getByRole("radio", { name: "Sequences" })).toBeChecked();
    expect(screen.getByRole("link", { name: "New sequence" })).toHaveAttribute("href", "/projects/p1/outreach/sequences/new");
    expect(await screen.findByRole("button", { name: "Connect mailbox" })).toBeInTheDocument();

    await userEvent.click(within(views).getByRole("radio", { name: "Conversations" }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent(/^\/projects\/p1\/outreach$/));
  });

  it("counts the position's outreach and lists each run, narrowed by the filter chips", async () => {
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(WITH_PEOPLE);
    renderPage();

    const table = await screen.findByRole("table", { name: "People in outreach" });
    expect(screen.getByText("Emails sent").nextSibling).toHaveTextContent("4");
    expect(screen.getByText("33% of people reached")).toBeInTheDocument();
    expect(table).toHaveTextContent("Omar Farouk");
    expect(table).toHaveTextContent("In sequence");
    expect(table).toHaveTextContent("Marked do not contact");
    expect(screen.getByTitle("2 of 3 sent")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /^Replied/ }));
    expect(table).toHaveTextContent("Fatima Al Mazrouei");
    expect(table).not.toHaveTextContent("Omar Farouk");
  });

  it("stops a live run from its row, and offers Set status on a reply nobody has recorded", async () => {
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(WITH_PEOPLE);
    vi.mocked(runApi.stopRun).mockResolvedValue(undefined);
    renderPage();

    const stops = await screen.findAllByRole("button", { name: "Stop" });
    expect(stops).toHaveLength(1);
    await userEvent.click(stops[0]);
    const dialog = await screen.findByRole("dialog", { name: "Stop Omar Farouk's sequence?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Stop sequence" }));
    await waitFor(() => expect(runApi.stopRun).toHaveBeenCalledWith("p1", "r1"));
    expect(await screen.findByText("Stopped. Nothing more goes to Omar Farouk.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Set status" }));
    expect(screen.getByRole("dialog", { name: "Drawer for c2" })).toBeInTheDocument();
  });

  it("reaches a run's row with Tab and opens it with Enter", async () => {
    const user = userEvent.setup();
    vi.mocked(runApi.getOutreachPeople).mockResolvedValue(WITH_PEOPLE);
    renderPage();

    const table = await screen.findByRole("table", { name: "People in outreach" });
    const row = within(table).getByText("Omar Farouk").closest<HTMLElement>('[role="row"]')!;
    row.focus();
    expect(row).toHaveFocus();

    await user.keyboard("{Enter}");

    expect(screen.getByRole("dialog", { name: "Drawer for c1" })).toBeInTheDocument();
  });

  it("shows no counts or table before anyone is on a sequence, and offers the first one", async () => {
    renderPage();

    await waitFor(() => expect(runApi.getOutreachPeople).toHaveBeenCalled());
    expect(await screen.findByText("No sequences on this position yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Write your first sequence" })).toHaveAttribute(
      "href",
      "/projects/p1/outreach/sequences/new",
    );
    expect(screen.queryByRole("table", { name: "People in outreach" })).not.toBeInTheDocument();
  });
});
