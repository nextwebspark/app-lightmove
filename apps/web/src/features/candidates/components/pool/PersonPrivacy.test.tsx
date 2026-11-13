import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import * as candidatePrivacyApi from "../../api/candidatePrivacyApi";
import type { CandidatePrivacySettings } from "../../api/candidatePrivacyApi";
import * as poolApi from "../../api/poolApi";
import type { PersonRecord } from "../../api/types";
import { todayIso } from "../../lib/candidatePrivacy";
import { MarkNotifiedDialog } from "./MarkNotifiedDialog";
import { PersonPrivacySection } from "./PersonPrivacySection";
import { SendPrivacyNoticeDialog } from "./SendPrivacyNoticeDialog";

vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  setLawfulBasis: vi.fn(),
  sendPrivacyNotice: vi.fn(),
  sendPrivacyNotices: vi.fn(),
  markNotified: vi.fn(),
}));
vi.mock("../../api/candidatePrivacyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatePrivacyApi>()),
  getCandidatePrivacy: vi.fn(),
}));
const admin = vi.hoisted(() => ({ value: true }));
vi.mock("../../../billing/lib/useBilling", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../billing/lib/useBilling")>()),
  useIsWorkspaceAdmin: () => admin.value,
}));

const READY: CandidatePrivacySettings = {
  controllerName: "Meridian Search Partners FZ-LLC",
  privacyContactEmail: "privacy@meridian.example",
  noticeUrl: "https://meridian.example/privacy",
  defaultLawfulBasis: "legitimate_interest",
  readyForNotices: true,
};

const PERSON = {
  personId: "person-1",
  fullName: "Noor Haddad",
  title: "Group CFO",
  contacts: {
    emails: [{ address: "noor@example.com", kind: "work", verified: true, status: null, source: "manual", foundAt: "" }],
    phones: [],
    emailsLookedUpAt: null,
    phonesLookedUpAt: null,
    source: null,
  },
  privacy: {
    lawfulBasis: "legitimate_interest",
    basisIsDefault: true,
    basisSetByName: null,
    basisSetAt: null,
    notifiedAt: null,
    noticeChannel: null,
  },
  addedAt: "2026-07-01T10:00:00Z",
} as unknown as PersonRecord;

const NOTIFIED: PersonRecord = {
  ...PERSON,
  privacy: { ...PERSON.privacy, notifiedAt: "2026-09-10T09:05:00Z", noticeChannel: "privacy_email" },
};

function renderWith(ui: ReactNode) {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>{ui}</ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("the drawer's privacy record", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    admin.value = true;
    vi.mocked(candidatePrivacyApi.getCandidatePrivacy).mockResolvedValue(READY);
  });

  it("says who was never told, offers both ways to record it, and records a basis on the person", async () => {
    const onSend = vi.fn();
    const onMark = vi.fn();
    const onChanged = vi.fn();
    vi.mocked(poolApi.setLawfulBasis).mockResolvedValue({
      ...PERSON,
      privacy: { ...PERSON.privacy, lawfulBasis: "consent", basisIsDefault: false, basisSetByName: "Yara Haddad" },
    });
    const user = userEvent.setup();
    renderWith(
      <PersonPrivacySection person={PERSON} onChanged={onChanged} onSendNotice={onSend} onMarkNotified={onMark} />,
    );

    expect(screen.getByText("Not notified")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Legitimate interest (workspace default)" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Send privacy notice/ }));
    await user.click(screen.getByRole("button", { name: "Mark as notified" }));
    expect(onSend).toHaveBeenCalled();
    expect(onMark).toHaveBeenCalled();

    await user.selectOptions(screen.getByRole("combobox", { name: "Lawful basis" }), "consent");
    await waitFor(() => expect(poolApi.setLawfulBasis).toHaveBeenCalledWith("person-1", "consent"));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
  });

  it("names how and when someone notified was told, and offers no notice again", () => {
    renderWith(
      <PersonPrivacySection person={NOTIFIED} onChanged={vi.fn()} onSendNotice={vi.fn()} onMarkNotified={vi.fn()} />,
    );
    expect(screen.getByText("Notified")).toBeInTheDocument();
    expect(screen.getByText(/privacy email/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Send privacy notice/ })).not.toBeInTheDocument();
  });

  it("previews the firm's notice, sends it, and hands back the person marked notified", async () => {
    vi.mocked(poolApi.sendPrivacyNotice).mockResolvedValue(NOTIFIED);
    const onSent = vi.fn();
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWith(<SendPrivacyNoticeDialog target={{ kind: "person", person: PERSON }} onClose={onClose} onSent={onSent} />);

    const preview = await screen.findByLabelText("Notice preview");
    expect(within(preview).getByText(/Meridian Search Partners FZ-LLC via Uncava/)).toBeInTheDocument();
    expect(screen.getByText(/at noor@example.com/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Send notice" }));

    await waitFor(() => expect(onSent).toHaveBeenCalledWith(NOTIFIED));
    expect(poolApi.sendPrivacyNotice).toHaveBeenCalledWith("person-1");
    expect(onClose).toHaveBeenCalled();
  });

  it("asks a non-admin to have an admin set the firm's details up, and offers no send", async () => {
    admin.value = false;
    vi.mocked(candidatePrivacyApi.getCandidatePrivacy).mockResolvedValue({
      ...READY,
      controllerName: null,
      readyForNotices: false,
    });
    renderWith(<SendPrivacyNoticeDialog target={{ kind: "person", person: PERSON }} onClose={vi.fn()} onSent={vi.fn()} />);

    expect(await screen.findByText(/an admin needs to fill in your firm's privacy details/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Send notice" })).not.toBeInTheDocument();
  });

  it("answers a bulk send with who was skipped and why before it closes", async () => {
    vi.mocked(poolApi.sendPrivacyNotices).mockResolvedValue({
      sent: 1,
      skipped: [{ personId: "person-2", reason: "no_email" }],
    });
    const onSent = vi.fn();
    const user = userEvent.setup();
    renderWith(
      <SendPrivacyNoticeDialog
        target={{
          kind: "selection",
          people: [
            { personId: "person-1", fullName: "Noor Haddad" },
            { personId: "person-2", fullName: "Omar Farouk" },
          ],
        }}
        onClose={vi.fn()}
        onSent={onSent}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Send notices" }));
    const skipped = await screen.findByRole("list", { name: "Skipped" });
    expect(within(skipped).getByText("Omar Farouk")).toBeInTheDocument();
    expect(within(skipped).getByText("No email on file")).toBeInTheDocument();
    expect(screen.getByText("1 sent · 1 skipped")).toBeInTheDocument();
    expect(onSent).toHaveBeenCalledWith(null);
  });

  it("marks someone told by hand on the day chosen, today by default", async () => {
    vi.mocked(poolApi.markNotified).mockResolvedValue(NOTIFIED);
    const onMarked = vi.fn();
    const user = userEvent.setup();
    renderWith(<MarkNotifiedDialog person={PERSON} onClose={vi.fn()} onMarked={onMarked} />);

    await user.click(screen.getByRole("button", { name: "Mark as notified" }));
    await waitFor(() => expect(poolApi.markNotified).toHaveBeenCalledWith("person-1", todayIso()));
    expect(onMarked).toHaveBeenCalledWith(NOTIFIED);
  });
});
