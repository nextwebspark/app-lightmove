import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import { CandidateDetailsPane } from "../../../outreach/components/CandidateDetailsPane";
import type { Conversation } from "../../../outreach/api/conversationApi";
import * as pipelineApi from "../../../pipeline/api/pipelineApi";
import { CandidatePrivacyCard } from "../../../settings/components/CandidatePrivacyCard";
import * as candidatePrivacyApi from "../../api/candidatePrivacyApi";
import type { CandidatePrivacySettings } from "../../api/candidatePrivacyApi";
import * as poolApi from "../../api/poolApi";
import * as shareConsentApi from "../../api/shareConsentApi";
import type { PersonRecord } from "../../api/types";
import { AskShareConsentDialog } from "./AskShareConsentDialog";
import { RequestConsentDialog } from "./RequestConsentDialog";
import { ShareConsentBadge } from "./ShareConsentBadge";

const share = { on: true, given: new Set<string>() };
// An agency workspace, so the client is called a client (useWorkspaceVocabulary).
vi.mock("../../../auth/AuthProvider", () => ({ useAuth: () => ({ user: { workspace: { mode: "AGENCY" } } }) }));
vi.mock("../../lib/useShareConsents", () => ({
  useShareConsents: () => ({
    on: share.on,
    clientNamable: true,
    rowOf: () => undefined,
    badgeOf: (candidateId: string) => (share.given.has(candidateId) ? "given" : "none"),
    needsPrompt: (candidateId: string) => share.on && !share.given.has(candidateId),
  }),
}));
vi.mock("../../../workspace/lib/vocabulary", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../workspace/lib/vocabulary")>()),
  useWorkspaceVocabulary: () => ({ unitLower: "client" }),
}));
vi.mock("../../api/shareConsentApi", async (importOriginal) => ({
  ...(await importOriginal<typeof shareConsentApi>()),
  getShareConsents: vi.fn(),
  requestShareConsent: vi.fn(),
  saveConsentRegister: vi.fn(),
}));
vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  requestConsent: vi.fn(),
}));
vi.mock("../../api/candidatePrivacyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatePrivacyApi>()),
  getCandidatePrivacy: vi.fn(),
}));
vi.mock("../../../pipeline/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof pipelineApi>()),
  setClientVisibility: vi.fn(),
}));

const FIRM: CandidatePrivacySettings = {
  controllerName: "Meridian Search Partners FZ-LLC",
  privacyContactEmail: "privacy@meridian.example",
  noticeUrl: "https://meridian.example/privacy",
  defaultLawfulBasis: "legitimate_interest",
  readyForNotices: true,
  privacyMode: "consent",
  keepOnFile: true,
  keepOnFileMonths: 24,
  shareWithClient: true,
  marketing: true,
  renewalDaysBefore: 30,
  readyForConsent: true,
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

const fatima = {
  personId: "person-1",
  fullName: "Fatima Al Mansoori",
  contacts: {
    emails: [{ address: "fatima@example.com", kind: "work", verified: true, status: null, source: "manual", foundAt: "" }],
    phones: [],
  },
  doNotContact: null,
  processingRestriction: null,
  privacy: { consents: [], consentWarning: false },
  addedAt: "2026-07-01T10:00:00Z",
  source: "extension",
  positions: [
    { candidateId: "cand-1", projectId: "p1", positionTitle: "Chief Financial Officer", workable: true },
    { candidateId: "cand-2", projectId: "p2", positionTitle: "Group Treasurer", workable: false },
  ],
} as unknown as PersonRecord;

beforeEach(() => {
  vi.resetAllMocks();
  share.on = true;
  share.given = new Set();
});

describe("the share badge", () => {
  it("says whether consent to share was never asked, asked or given", () => {
    renderWith(
      <>
        <ShareConsentBadge state="none" row={undefined} clientName="Meridian Energy Group" />
        <ShareConsentBadge state="asked" row={undefined} clientName="Meridian Energy Group" />
        <ShareConsentBadge state="given" row={undefined} clientName="Meridian Energy Group" />
      </>,
    );
    expect(screen.getByText("No consent to share").parentElement).toHaveAttribute(
      "title",
      "No consent to share with Meridian Energy Group",
    );
    expect(screen.getByText("Consent asked")).toBeInTheDocument();
    expect(screen.getByText("Consent given")).toBeInTheDocument();
  });
});

describe("asking to share with the client", () => {
  it("says a confidential client by default, and names the client only when ticked", async () => {
    vi.mocked(shareConsentApi.requestShareConsent).mockResolvedValue({
      candidateId: "cand-1",
      status: "requested",
      requestedAt: null,
      requestOpenUntil: null,
      answeredAt: null,
      clientNamed: true,
    });
    const onClose = vi.fn();
    renderWith(
      <AskShareConsentDialog
        projectId="p1"
        candidateId="cand-1"
        fullName="Fatima Al Mansoori"
        roleTitle="Chief Financial Officer"
        clientName="Meridian Energy Group"
        clientNamable
        row={undefined}
        onClose={onClose}
      />,
    );
    const dialog = screen.getByRole("dialog", { name: "Ask to share Fatima Al Mansoori's profile" });
    expect(dialog).toHaveTextContent('The email says "a confidential client".');

    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Name the client/ }));
    expect(dialog).toHaveTextContent("The email names Meridian Energy Group.");
    await userEvent.click(within(dialog).getByRole("button", { name: "Send request" }));
    await waitFor(() => expect(shareConsentApi.requestShareConsent).toHaveBeenCalledWith("p1", "cand-1", true));
    expect(onClose).toHaveBeenCalled();
  });

  it("never names the client of a confidential position", () => {
    renderWith(
      <AskShareConsentDialog
        projectId="p1"
        candidateId="cand-1"
        fullName="Fatima Al Mansoori"
        roleTitle="Chief Financial Officer"
        clientName="Meridian Energy Group"
        clientNamable={false}
        row={undefined}
        onClose={vi.fn()}
      />,
    );
    const name = screen.getByRole("checkbox", { name: /Name the client/ });
    expect(name).toBeDisabled();
    expect(name).toHaveTextContent("This position is confidential, so the email never names the client.");
  });

  it("asks one person about a share from the drawer, previewing the share email on its own", async () => {
    vi.mocked(shareConsentApi.getShareConsents).mockResolvedValue({
      asksToShare: true,
      clientNamable: true,
      clientName: "Meridian Energy Group",
      rows: [],
    });
    vi.mocked(poolApi.requestConsent).mockResolvedValue(fatima);
    renderWith(
      <RequestConsentDialog target={{ kind: "person", person: fatima }} firm={FIRM} onClose={vi.fn()} onSent={vi.fn()} />,
    );
    const dialog = screen.getByRole("dialog", { name: "Ask Fatima Al Mansoori for consent" });
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Keep their details on file/ }));
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Career and market updates/ }));
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Share their profile with a client/ }));

    const preview = within(dialog).getByLabelText("Consent request preview");
    expect(preview).toHaveTextContent("May we share your profile for a Chief Financial Officer role?");
    expect(preview).toHaveTextContent("is searching for a Chief Financial Officer for a confidential client");

    await userEvent.click(await within(dialog).findByRole("checkbox", { name: /Name the client \(Meridian Energy Group\)/ }));
    expect(preview).toHaveTextContent("for Meridian Energy Group");
    await userEvent.click(within(dialog).getByRole("button", { name: "Send request" }));
    await waitFor(() =>
      expect(poolApi.requestConsent).toHaveBeenCalledWith("person-1", ["share_with_client"], {
        projectId: "p1",
        nameClient: true,
      }),
    );
  });

  it("offers only positions the viewer works, and never offers to name the client before the position is read", async () => {
    vi.mocked(shareConsentApi.getShareConsents).mockRejectedValue(new Error("refused"));
    renderWith(
      <RequestConsentDialog target={{ kind: "person", person: fatima }} firm={FIRM} onClose={vi.fn()} onSent={vi.fn()} />,
    );
    const dialog = screen.getByRole("dialog", { name: "Ask Fatima Al Mansoori for consent" });
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Share their profile with a client/ }));

    const picker = within(dialog).getByRole("combobox", { name: "Position" });
    expect(within(picker).getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Chief Financial Officer",
    ]);
    await waitFor(() => expect(shareConsentApi.getShareConsents).toHaveBeenCalled());
    expect(within(dialog).getByRole("checkbox", { name: /Name the client/ })).toBeDisabled();
  });
});

describe("the Outreach details pane's Client visible switch", () => {
  const conversation = {
    executive: {
      candidateId: "cand-1",
      personId: "person-1",
      fullName: "Fatima Al Mansoori",
      title: "Group CFO",
      companyName: "Aurora Holdings",
      statusLabel: "Identified",
      statusColour: "slate",
      clientVisible: false,
      enrichedAt: null,
    },
    contact: { emails: [], phones: [], locationCity: null, locationCountry: null, doNotContact: false },
    keyDates: { addedAt: null, firstContactedAt: null, repliedAt: null, lastActivityAt: null },
    currentRun: { run: null },
  } as unknown as Conversation;

  it("asks first when turned on without consent, and Ask first sends the request straight away", async () => {
    vi.mocked(shareConsentApi.requestShareConsent).mockResolvedValue({
      candidateId: "cand-1",
      status: "requested",
      requestedAt: null,
      requestOpenUntil: null,
      answeredAt: null,
      clientNamed: false,
    });
    renderWith(<CandidateDetailsPane projectId="p1" conversation={conversation} onOpenRecord={vi.fn()} />);

    await userEvent.click(screen.getByRole("switch", { name: "Client visible" }));
    const prompt = await screen.findByRole("dialog", { name: /No consent to share with/ });
    expect(pipelineApi.setClientVisibility).not.toHaveBeenCalled();
    await userEvent.click(within(prompt).getByRole("button", { name: "Ask first" }));
    await waitFor(() => expect(shareConsentApi.requestShareConsent).toHaveBeenCalledWith("p1", "cand-1", false));
    expect(pipelineApi.setClientVisibility).not.toHaveBeenCalled();
  });

  it("shows them at once when the workspace does not ask", async () => {
    share.on = false;
    vi.mocked(pipelineApi.setClientVisibility).mockResolvedValue({ candidateId: "cand-1", clientVisible: true } as never);
    renderWith(<CandidateDetailsPane projectId="p1" conversation={conversation} onOpenRecord={vi.fn()} />);

    await userEvent.click(screen.getByRole("switch", { name: "Client visible" }));
    await waitFor(() => expect(pipelineApi.setClientVisibility).toHaveBeenCalledWith("p1", "cand-1", true));
    expect(screen.queryByRole("dialog", { name: /No consent to share/ })).not.toBeInTheDocument();
  });
});

describe("downloading the consent register", () => {
  it("is offered on the Settings card in consent mode", async () => {
    vi.mocked(candidatePrivacyApi.getCandidatePrivacy).mockResolvedValue(FIRM);
    vi.mocked(shareConsentApi.saveConsentRegister).mockResolvedValue();
    renderWith(<CandidatePrivacyCard saved={FIRM} />);

    await userEvent.click(await screen.findByRole("button", { name: "Download consent register" }));
    await waitFor(() => expect(shareConsentApi.saveConsentRegister).toHaveBeenCalledWith());
  });
});
