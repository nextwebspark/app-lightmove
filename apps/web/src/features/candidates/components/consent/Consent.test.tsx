import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import { formatInstantDate } from "../../../../lib/datetime";
import type { CandidatePrivacySettings } from "../../api/candidatePrivacyApi";
import * as poolApi from "../../api/poolApi";
import type { PersonConsent, PersonRecord } from "../../api/types";
import { PersonPrivacySection } from "../pool/PersonPrivacySection";
import { RecordConsentDialog } from "./RecordConsentDialog";
import { RequestConsentDialog } from "./RequestConsentDialog";

vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  requestConsent: vi.fn(),
  requestConsents: vi.fn(),
  recordConsent: vi.fn(),
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

const DAY = 86_400_000;
const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * DAY).toISOString();
const day = (instant: string) => formatInstantDate(instant) ?? "";

function consent(overrides: Partial<PersonConsent>): PersonConsent {
  return {
    id: "c-1",
    purpose: "keep_on_file",
    projectId: null,
    status: "requested",
    requestedAt: null,
    requestedByName: null,
    requestOpenUntil: null,
    answeredAt: null,
    answeredVia: null,
    validUntil: null,
    clientNamed: false,
    ...overrides,
  };
}

function personWith(consents: PersonConsent[], extra: Partial<PersonRecord["privacy"]> = {}): PersonRecord {
  return {
    personId: "person-1",
    fullName: "Noor Haddad",
    contacts: {
      emails: [{ address: "noor@example.com", kind: "work", verified: true, status: null, source: "manual", foundAt: "" }],
      phones: [],
      emailsLookedUpAt: null,
      phonesLookedUpAt: null,
      source: null,
    },
    doNotContact: null,
    processingRestriction: null,
    privacy: {
      lawfulBasis: "legitimate_interest",
      basisIsDefault: true,
      basisSetByName: null,
      basisSetAt: null,
      notifiedAt: null,
      noticeChannel: null,
      optedOutAt: null,
      consents,
      consentWarning: false,
      ...extra,
    },
    addedAt: "2026-07-01T10:00:00Z",
    source: "extension",
  } as unknown as PersonRecord;
}

function renderWith(ui: ReactNode) {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>{ui}</ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("the drawer's consent rows", () => {
  it("draws each purpose as it stands, and warns when a Consent basis has nothing given", () => {
    const asked = iso(-2);
    const person = personWith(
      [
        consent({ status: "requested", requestedAt: asked, requestedByName: "Yara Haddad", requestOpenUntil: iso(28) }),
        consent({ id: "c-2", purpose: "marketing", status: "declined", answeredAt: iso(-1), answeredVia: "page" }),
      ],
      { lawfulBasis: "consent", basisIsDefault: false, consentWarning: true },
    );
    renderWith(
      <PersonPrivacySection
        person={person}
        onChanged={vi.fn()}
        onSendNotice={vi.fn()}
        onMarkNotified={vi.fn()}
        consentFirm={FIRM}
        onAskConsent={vi.fn()}
        onRecordConsent={vi.fn()}
      />,
    );

    const rows = within(screen.getByRole("group", { name: "Consent" }));
    expect(rows.getByRole("note")).toHaveTextContent("No consent recorded.");
    expect(rows.getByText("Keep on file")).toBeInTheDocument();
    expect(rows.getByText("Asked")).toBeInTheDocument();
    expect(rows.getByText(`Asked on ${day(asked)} by Yara Haddad · answer by ${day(iso(28))}`)).toBeInTheDocument();
    expect(rows.getByText("Declined")).toBeInTheDocument();
    expect(rows.getByText(`On ${day(iso(-1))} · on their privacy page`)).toBeInTheDocument();
  });

  it("says a given consent's end, how it was given, and a renewal waiting", () => {
    const until = iso(20);
    const person = personWith([
      consent({
        status: "given",
        answeredAt: iso(-700),
        answeredVia: "by_hand",
        validUntil: until,
        requestedAt: iso(-1),
        requestOpenUntil: iso(29),
      }),
    ]);
    renderWith(
      <PersonPrivacySection
        person={person}
        onChanged={vi.fn()}
        onSendNotice={vi.fn()}
        onMarkNotified={vi.fn()}
        consentFirm={{ ...FIRM, marketing: false }}
      />,
    );

    expect(screen.getByText("Renewal asked")).toBeInTheDocument();
    expect(
      screen.getByText(`Until ${day(until)} · recorded by hand · renewal asked on ${day(iso(-1))}`),
    ).toBeInTheDocument();
    expect(screen.queryByText("Career and market updates")).not.toBeInTheDocument();
  });

  it("draws no consent rows outside consent mode", () => {
    renderWith(
      <PersonPrivacySection person={personWith([])} onChanged={vi.fn()} onSendNotice={vi.fn()} onMarkNotified={vi.fn()} />,
    );
    expect(screen.queryByRole("group", { name: "Consent" })).not.toBeInTheDocument();
  });
});

describe("Ask for consent", () => {
  beforeEach(() => vi.clearAllMocks());

  it("previews the email word for word and sends the purposes ticked", async () => {
    const sent = personWith([]);
    vi.mocked(poolApi.requestConsent).mockResolvedValue(sent);
    const onSent = vi.fn();
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWith(
      <RequestConsentDialog target={{ kind: "person", person: personWith([]) }} firm={FIRM} onClose={onClose} onSent={onSent} />,
    );

    const preview = within(screen.getByLabelText("Consent request preview"));
    expect(preview.getByText("Meridian Search Partners FZ-LLC would like your permission")).toBeInTheDocument();
    // EmailTemplates.buildConsentRequestEmail's paragraphs, with ConsentWording.askingOf's bullets.
    expect(
      preview.getByText(
        "Meridian Search Partners FZ-LLC holds some of your professional details, which came from your public professional profile. We'd like your permission to:",
      ),
    ).toBeInTheDocument();
    expect(preview.getByText("keep your details on file for future roles, for 24 months")).toBeInTheDocument();
    expect(preview.getByText("send you career and market updates")).toBeInTheDocument();
    expect(
      preview.getByText(
        "Each is a separate choice, and you can say no to any of them. If you don't answer, nothing changes.",
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /Career and market updates/ }));
    expect(preview.queryByText("send you career and market updates")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Send request" }));

    await waitFor(() => expect(poolApi.requestConsent).toHaveBeenCalledWith("person-1", ["keep_on_file"]));
    expect(onSent).toHaveBeenCalledWith(sent);
    expect(onClose).toHaveBeenCalled();
  });

  it("locks a purpose already given and one asked in the last 14 days", () => {
    const until = iso(300);
    const person = personWith([
      consent({ status: "given", answeredAt: iso(-5), validUntil: until, answeredVia: "page" }),
      consent({ id: "c-2", purpose: "marketing", requestedAt: iso(-3), requestOpenUntil: iso(27) }),
    ]);
    renderWith(<RequestConsentDialog target={{ kind: "person", person }} firm={FIRM} onClose={vi.fn()} onSent={vi.fn()} />);

    expect(screen.getByRole("checkbox", { name: /Keep their details on file/ })).toBeDisabled();
    expect(screen.getByText(`Already given, until ${day(until)}`)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /Career and market updates/ })).toBeDisabled();
    expect(screen.getByText(`Asked on ${day(iso(-3))}. You can ask again from ${day(iso(11))}`)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send request" })).toBeDisabled();
  });

  it("locks a purpose declined in the last 14 days, though its request has closed", () => {
    const person = personWith([
      consent({ purpose: "marketing", status: "declined", requestedAt: iso(-20), answeredAt: iso(-1), answeredVia: "page" }),
    ]);
    renderWith(<RequestConsentDialog target={{ kind: "person", person }} firm={FIRM} onClose={vi.fn()} onSent={vi.fn()} />);

    expect(screen.getByRole("checkbox", { name: /Career and market updates/ })).toBeDisabled();
    expect(screen.getByText(`Declined on ${day(iso(-1))}. You can ask again from ${day(iso(13))}`)).toBeInTheDocument();
  });

  it("says who a selection skipped and why", async () => {
    vi.mocked(poolApi.requestConsents).mockResolvedValue({
      sent: 1,
      skipped: [{ personId: "p-2", reason: "already_given" }],
    });
    const user = userEvent.setup();
    renderWith(
      <RequestConsentDialog
        target={{
          kind: "selection",
          people: [
            { personId: "p-1", fullName: "Noor Haddad" },
            { personId: "p-2", fullName: "Omar Farouk" },
          ],
        }}
        firm={FIRM}
        onClose={vi.fn()}
        onSent={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Ask 2 people" }));
    expect(await screen.findByText("1 asked · 1 skipped")).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "Skipped" })).getByText("Already agreed")).toBeInTheDocument();
    expect(poolApi.requestConsents).toHaveBeenCalledWith(["p-1", "p-2"], ["keep_on_file", "marketing"]);
  });
});

describe("Record an answer", () => {
  it("records the purpose, the answer and the day", async () => {
    const recorded = personWith([]);
    vi.mocked(poolApi.recordConsent).mockResolvedValue(recorded);
    const onRecorded = vi.fn();
    const user = userEvent.setup();
    renderWith(
      <RecordConsentDialog person={personWith([])} keepOnFileMonths={24} onClose={vi.fn()} onRecorded={onRecorded} />,
    );

    expect(screen.getByText(/Today or earlier\. Valid until/)).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Declined" }));
    expect(screen.getByText("Today or earlier.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Record answer" }));

    await waitFor(() =>
      expect(poolApi.recordConsent).toHaveBeenCalledWith("person-1", {
        purpose: "keep_on_file",
        answer: "declined",
        answeredOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      }),
    );
    expect(onRecorded).toHaveBeenCalledWith(recorded);
  });
});
