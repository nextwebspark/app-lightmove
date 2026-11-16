import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { copyText } from "../../../lib/clipboard";
import * as personCrmApi from "../../candidates/api/personCrmApi";
import type { CandidateContacts, CandidatePhone, PersonNote, RecentApproach } from "../../candidates/api/types";
import { ContactChannels } from "../../contactlookup/components/ContactPanel";
import type { DialTarget, PhoneReach } from "../../contactlookup/lib/phoneReach";
import { PendingContacts } from "./PendingContacts";
import { PendingContactTray } from "./PendingContactTray";
import type { PendingContact } from "./pendingContactStore";

vi.mock("../../../lib/clipboard", () => ({ copyText: vi.fn() }));
vi.mock("../../candidates/api/personCrmApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/personCrmApi")>()),
  writePersonNote: vi.fn(),
}));
vi.mock("../components/NoteComposerModal", () => ({
  NoteComposerModal: (props: { kind: string | null; fullName: string; presetOutcome: string | null; presetOccurredAt: string | null; onSaved: () => void; onClose: () => void }) => (
    <div role="dialog" aria-label={`Log a ${props.kind} with ${props.fullName}`}>
      <span>Opened on {props.presetOutcome ?? "no outcome"} at {props.presetOccurredAt}</span>
      <button type="button" onClick={props.onSaved}>Save stub</button>
      <button type="button" onClick={props.onClose}>Cancel stub</button>
    </div>
  ),
}));

const PRESSED_AT = "2026-11-16T09:30:00.000Z";

const mobile: CandidatePhone = {
  number: "050 441 8836",
  kind: null,
  verified: false,
  status: null,
  source: "manual",
  foundAt: "2026-09-16T17:27:24Z",
  international: "+971 50 441 8836",
  e164: "+971504418836",
  callingCode: "+971",
  countrySource: "location",
  lineType: "mobile",
};

const contacts: CandidateContacts = {
  emails: [],
  phones: [mobile],
  emailsLookedUpAt: null,
  phonesLookedUpAt: null,
  source: null,
};

const target: DialTarget = {
  projectId: "p1",
  candidateId: "c1",
  personName: "Fatima Al Mazrouei",
  positionTitle: "Group CFO",
};

const reachOf = (more: Partial<DialTarget> = {}): PhoneReach => ({
  personName: "Fatima Al Mazrouei",
  locationCountry: "United Arab Emirates",
  mayReachOut: true,
  doNotContact: false,
  target: { ...target, ...more },
});

const caution: RecentApproach = {
  tier: "CAUTION",
  windowDays: 90,
  touches: [
    {
      kind: "EMAILED",
      byUserId: "u2",
      byName: "Sara Al-Mansour",
      at: new Date(Date.now() - 2 * 86_400_000).toISOString(),
      projectId: "p2",
      positionTitle: "Head of Credit Risk",
      samePosition: false,
      byYou: false,
      positionHidden: false,
    },
  ],
  elsewhere: [],
  lastApproach: null,
};

const card = (id: string, patch: Partial<PendingContact> = {}): PendingContact => ({
  id,
  workspaceId: "w1",
  channel: "call",
  projectId: "p1",
  candidateId: "c1",
  personName: "Fatima Al Mazrouei",
  positionTitle: "Group CFO",
  number: "050 441 8836",
  whatsappHref: "https://wa.me/971504418836",
  at: PRESSED_AT,
  ...patch,
});

function seed(cards: PendingContact[]) {
  window.sessionStorage.setItem("lightmove.pendingContacts", JSON.stringify(cards));
}

function renderTray(reach: PhoneReach | null = null) {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <PendingContacts workspaceId="w1">
            {reach && <ContactChannels contacts={contacts} reach={reach} />}
            <PendingContactTray />
          </PendingContacts>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const tray = () => screen.getByRole("region", { name: "Calls and messages to log" });

describe("the log-it tray", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"], shouldAdvanceTime: true });
    vi.setSystemTime(new Date("2026-11-16T09:32:00Z"));
    window.sessionStorage.clear();
    vi.mocked(copyText).mockResolvedValue(true);
    vi.mocked(personCrmApi.writePersonNote).mockImplementation(
      async (_p, _c, note) => ({ id: "n1", kind: note.kind, outcome: note.outcome }) as PersonNote,
    );
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("raises a card when a number is copied, worded as a call", async () => {
    renderTray(reachOf());
    expect(screen.queryByRole("region", { name: "Calls and messages to log" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Copy phone" }));

    expect(await screen.findByText("Call with Fatima Al Mazrouei")).toBeInTheDocument();
    expect(within(tray()).getByText(/050 441 8836 · copied just now · Group CFO/)).toBeInTheDocument();
    expect(copyText).toHaveBeenCalledWith("050 441 8836");
  });

  it("logs No answer in one tap, timed by the press and filed on the position it came from", async () => {
    seed([card("k1")]);
    renderTray();

    await userEvent.click(within(tray()).getByRole("button", { name: "No answer" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "call",
        body: "",
        format: "markdown",
        occurredAt: PRESSED_AT,
        outcome: "no_answer",
      }),
    );
    expect(await screen.findByText("Call logged")).toBeInTheDocument();
    expect(screen.queryByText("Call with Fatima Al Mazrouei")).not.toBeInTheDocument();
  });

  it("logs nothing on Not now", async () => {
    seed([card("k1")]);
    renderTray();

    await userEvent.click(
      within(tray()).getByRole("button", { name: "Not now — don't log the call with Fatima Al Mazrouei" }),
    );

    expect(screen.queryByText("Call with Fatima Al Mazrouei")).not.toBeInTheDocument();
    expect(personCrmApi.writePersonNote).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem("lightmove.pendingContacts")).toBe("[]");
  });

  it("opens the call's composer on Connected, and keeps the card when it is cancelled", async () => {
    seed([card("k1")]);
    renderTray();

    await userEvent.click(within(tray()).getByRole("button", { name: "Connected" }));

    const dialog = screen.getByRole("dialog", { name: "Log a call with Fatima Al Mazrouei" });
    expect(within(dialog).getByText(`Opened on connected at ${PRESSED_AT}`)).toBeInTheDocument();
    expect(personCrmApi.writePersonNote).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel stub" }));
    expect(screen.getByText("Call with Fatima Al Mazrouei")).toBeInTheDocument();

    await userEvent.click(within(tray()).getByRole("button", { name: "Connected" }));
    await userEvent.click(screen.getByRole("button", { name: "Save stub" }));
    expect(screen.queryByText("Call with Fatima Al Mazrouei")).not.toBeInTheDocument();
  });

  it("logs Wrong number, then offers Fix number", async () => {
    seed([card("k1")]);
    renderTray();

    await userEvent.click(within(tray()).getByRole("button", { name: "Wrong number" }));

    expect(await within(tray()).findByText("Logged as Wrong number.")).toBeInTheDocument();
    expect(within(tray()).getByRole("button", { name: "Fix number" })).toBeInTheDocument();
    expect(within(tray()).getByRole("button", { name: "Done with the call to Fatima Al Mazrouei" })).toBeInTheDocument();
    expect(personCrmApi.writePersonNote).toHaveBeenCalledWith(
      "p1",
      "c1",
      expect.objectContaining({ kind: "call", outcome: "wrong_number" }),
    );
  });

  it("asks a WhatsApp card what happened, logs They replied, and offers a note", async () => {
    seed([card("k1", { channel: "whatsapp" })]);
    renderTray();

    expect(within(tray()).getByText("WhatsApp to Fatima Al Mazrouei")).toBeInTheDocument();
    expect(within(tray()).getByRole("link", { name: "Open the chat with Fatima Al Mazrouei in WhatsApp" })).toHaveAttribute(
      "href",
      "https://wa.me/971504418836",
    );
    expect(within(tray()).getByRole("button", { name: "Add note" })).toBeInTheDocument();
    await userEvent.click(within(tray()).getByRole("button", { name: "They replied" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith(
        "p1",
        "c1",
        expect.objectContaining({ kind: "whatsapp", outcome: "replied" }),
      ),
    );
    expect(await screen.findByText("WhatsApp logged")).toBeInTheDocument();
  });

  it("dismisses on Esc without the Escape reaching the drawer under it", async () => {
    seed([card("k1")]);
    renderTray();
    const drawerEscape = vi.fn();
    document.addEventListener("keydown", drawerEscape);

    within(tray()).getByRole("button", { name: "No answer" }).focus();
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByText("Call with Fatima Al Mazrouei")).not.toBeInTheDocument();
    expect(drawerEscape).not.toHaveBeenCalled();
    document.removeEventListener("keydown", drawerEscape);
  });

  it("announces a new card once, outside the cards, and rises above the toasts", async () => {
    seed([card("k1")]);
    renderTray();

    expect(screen.getByRole("status")).toHaveTextContent("Call with Fatima Al Mazrouei to log");
    expect(tray()).not.toHaveAttribute("aria-live");
    await userEvent.click(within(tray()).getByRole("button", { name: "Wrong number" }));
    await screen.findByText("Call logged · Wrong number");
    expect(document.documentElement.style.getPropertyValue("--toast-stack-height")).not.toBe("0px");
  });

  it("never shows another workspace's cards", () => {
    seed([card("k1", { workspaceId: "w2" })]);
    renderTray();

    expect(screen.queryByRole("region", { name: "Calls and messages to log" })).not.toBeInTheDocument();
  });

  it("shows only the newest of three or more, the rest a press away", async () => {
    seed([
      card("k3", { personName: "Omar Khalil", candidateId: "c3" }),
      card("k2", { personName: "Priya Raman", candidateId: "c2" }),
      card("k1"),
    ]);
    renderTray();

    expect(within(tray()).getByText("Call with Omar Khalil")).toBeInTheDocument();
    expect(within(tray()).queryByText("Call with Priya Raman")).not.toBeInTheDocument();
    await userEvent.click(within(tray()).getByRole("button", { name: "2 more to log" }));
    expect(within(tray()).getByText("Call with Priya Raman")).toBeInTheDocument();
    expect(within(tray()).getByText("Call with Fatima Al Mazrouei")).toBeInTheDocument();
  });

  it("asks first when a teammate recently approached them, and leaves no card until confirmed", async () => {
    renderTray(reachOf({ approach: caution }));

    await userEvent.click(screen.getByRole("button", { name: "Copy phone" }));

    expect(await screen.findByText("Fatima Al Mazrouei was recently approached")).toBeInTheDocument();
    expect(copyText).not.toHaveBeenCalled();
    expect(screen.queryByText("Call with Fatima Al Mazrouei")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("checkbox", { name: /I have read this/ }));
    await userEvent.click(screen.getByRole("button", { name: "Copy anyway" }));

    expect(copyText).toHaveBeenCalledWith("050 441 8836");
    expect(await screen.findByText("Call with Fatima Al Mazrouei")).toBeInTheDocument();
  });
});
