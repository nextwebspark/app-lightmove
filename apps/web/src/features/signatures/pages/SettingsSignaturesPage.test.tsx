import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser } from "../../../test/fixtures/user";
import * as mailboxApi from "../../outreach/api/mailboxApi";
import type { Mailbox } from "../../outreach/api/mailboxApi";
import * as signaturesApi from "../api/signaturesApi";
import type { EmailSignature, EmailSignatures } from "../api/signaturesApi";
import { SettingsSignaturesPage } from "./SettingsSignaturesPage";

vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: aUser({ title: "Head of Talent" }) }) }));
vi.mock("../api/signaturesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/signaturesApi")>()),
  listSignatures: vi.fn(),
  createSignature: vi.fn(),
  updateSignature: vi.fn(),
  makeDefaultSignature: vi.fn(),
  deleteSignature: vi.fn(),
  previewSignature: vi.fn(),
}));

vi.mock("../../outreach/api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../outreach/api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));

const NO_MAILBOX: Mailbox = { offered: true, providers: ["google", "microsoft"], bookingLinkOffered: false, connection: null };

function aConnectedMailbox(status: "ACTIVE" | "ERROR"): Mailbox {
  return {
    ...NO_MAILBOX,
    bookingLinkOffered: true,
    connection: {
      address: "alok@nextwebspark.com",
      provider: "google",
      status,
      dailyCap: 50,
      timeZone: "Asia/Dubai",
      connectedAt: "2026-10-01T09:00:00Z",
      movesOffNylas: false,
      runsStoppedByMove: 0,
      liveSequences: 0,
      livePeople: 0,
      readsReplies: true,
      reconnectToReadReplies: false,
      sentToday: 0,
      scheduledEmails: 0,
    },
  };
}

const SOURCES: EmailSignatures["sources"] = {
  name: "Alok Kumar",
  title: "Head of Talent",
  phone: "+971 50 123 4567",
  linkedinUrl: null,
  email: "alok@nextwebspark.com",
  company: "NextWebSpark Search",
  website: "nextwebspark.com",
  photoUrl: null,
  logoAvailable: true,
  bookingLinkOffered: false,
};

function aSignature(overrides: Partial<EmailSignature>): EmailSignature {
  return {
    id: "s1",
    name: "Formal",
    layout: "PHOTO_LOGO",
    fields: { name: null, title: null, phone: null, linkedinUrl: null, company: null, website: null, address: null },
    showPhoto: true,
    showLogo: true,
    showBookingLink: false,
    customBody: null,
    disclaimer: null,
    isDefault: false,
    updatedAt: "2026-11-01T10:00:00Z",
    html: "<div>Alok Kumar</div>",
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsSignaturesPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** Settings → Email signature: the consultant's own signatures, a layout to start from, and the server's preview. */
describe("SettingsSignaturesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(signaturesApi.previewSignature).mockResolvedValue({ html: "<div>preview</div>" });
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(NO_MAILBOX);
  });

  it("names the mailbox the signatures go from, or offers to connect one", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [aSignature({ isDefault: true })],
      sources: SOURCES,
      limit: 10,
    });
    const { unmount } = renderPage();
    expect(await screen.findByText("Connect your mailbox to send with a signature")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect Gmail" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Connect Outlook" })).toBeEnabled();
    unmount();

    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(aConnectedMailbox("ACTIVE"));
    const connected = renderPage();
    expect(await screen.findByText("alok@nextwebspark.com")).toBeInTheDocument();
    expect(screen.getByText(/Sending from/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Connect/ })).not.toBeInTheDocument();
    connected.unmount();

    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(aConnectedMailbox("ERROR"));
    renderPage();
    expect(await screen.findByText("Reconnect alok@nextwebspark.com to send")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reconnect Gmail" })).toBeEnabled();
  });

  it("lists the default first, and makes another the default in one click", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [aSignature({ id: "s2", name: "Short reply", layout: "MINIMAL" }), aSignature({ isDefault: true })],
      sources: SOURCES,
      limit: 10,
    });
    vi.mocked(signaturesApi.makeDefaultSignature).mockResolvedValue(aSignature({ id: "s2", isDefault: true }));
    const user = userEvent.setup();
    renderPage();

    const items = await screen.findAllByRole("listitem");
    expect(within(items[0]).getByText("Formal")).toBeInTheDocument();
    expect(within(items[0]).getByText("Default")).toBeInTheDocument();
    expect(within(items[0]).queryByRole("button", { name: "Make default" })).not.toBeInTheDocument();

    await user.click(within(items[1]).getByRole("button", { name: "Make default" }));
    await waitFor(() => expect(signaturesApi.makeDefaultSignature).toHaveBeenCalledWith("s2"));
    expect(await screen.findByText("Default signature changed")).toBeInTheDocument();
  });

  it("starts from a layout, fills nothing the profile already says, and saves the first one as the default", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({ signatures: [], sources: SOURCES, limit: 10 });
    vi.mocked(signaturesApi.createSignature).mockResolvedValue(aSignature({ isDefault: true }));
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("No signature yet")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "New signature" }));
    await user.click(screen.getByRole("button", { name: /Classic/ }));

    expect(screen.getByText("Head of Talent")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Title" })).not.toBeInTheDocument();
    expect(screen.getByText("Not in your profile")).toBeInTheDocument();
    expect(screen.getByText("alok@nextwebspark.com")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Email" })).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Address" }), "Festival Tower");
    await user.click(screen.getByRole("button", { name: "Save signature" }));

    await waitFor(() =>
      expect(signaturesApi.createSignature).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Classic",
          layout: "CLASSIC",
          isDefault: true,
          fields: expect.objectContaining({ title: null, address: "Festival Tower" }),
        }),
      ),
    );
    expect(await screen.findByText("Signature saved")).toBeInTheDocument();
  });

  it("offers the photo only once one is uploaded, and the booking link only with a mailbox", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({ signatures: [aSignature({})], sources: SOURCES, limit: 10 });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Edit" }));

    expect(screen.getByRole("switch", { name: "Show my photo" })).toBeDisabled();
    expect(screen.getByText("Upload a photo in Profile to show it here.")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: 'Add "Book a call with me"' })).toBeDisabled();
    expect(screen.getByRole("switch", { name: "Show the company logo" })).toBeEnabled();
    expect(await screen.findByTitle("Signature preview")).toBeInTheDocument();
  });

  it("shows the server's refusal of a bad address under its field", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({ signatures: [aSignature({})], sources: SOURCES, limit: 10 });
    const { ApiRequestError } = await import("../../../lib/apiClient");
    vi.mocked(signaturesApi.updateSignature).mockRejectedValue(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "One or more fields are invalid",
        status: 400,
        correlationId: "c1",
        fieldErrors: { "fields.website": "Enter a website address, like yourcompany.com" },
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Change website" }));
    await user.type(screen.getByRole("textbox", { name: "Website" }), "not a site");
    await user.click(screen.getByRole("button", { name: "Save signature" }));

    expect(await screen.findByText("Enter a website address, like yourcompany.com")).toBeInTheDocument();
  });

  it("makes a profile value this signature's own on Change, and follows the profile again on Use profile's", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({ signatures: [aSignature({})], sources: SOURCES, limit: 10 });
    vi.mocked(signaturesApi.updateSignature).mockResolvedValue(aSignature({}));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Change title" }));
    const title = screen.getByRole("textbox", { name: "Title" });
    expect(title).toHaveAttribute("placeholder", "Head of Talent");
    await user.type(title, "Partner");
    expect(screen.getByText("Only on this signature")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Use profile's" }));
    expect(screen.queryByRole("textbox", { name: "Title" })).not.toBeInTheDocument();
    expect(screen.getByText("Head of Talent")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Add linkedin" }));
    await user.type(screen.getByRole("textbox", { name: "LinkedIn" }), "linkedin.com/in/alok");
    await user.click(screen.getByRole("button", { name: "Save signature" }));
    await waitFor(() =>
      expect(signaturesApi.updateSignature).toHaveBeenCalledWith(
        "s1",
        expect.objectContaining({ fields: expect.objectContaining({ title: null, linkedinUrl: "linkedin.com/in/alok" }) }),
      ),
    );
  });

  it("holds a Custom signature to 2,000 characters, and says why the server refused one", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [
        aSignature({ id: "long", name: "Long", layout: "CUSTOM", customBody: "x".repeat(2001) }),
        aSignature({ id: "short", name: "Short", layout: "CUSTOM", customBody: "Alok", isDefault: true }),
      ],
      sources: SOURCES,
      limit: 10,
    });
    const { ApiRequestError } = await import("../../../lib/apiClient");
    vi.mocked(signaturesApi.updateSignature).mockRejectedValue(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "One or more fields are invalid",
        status: 400,
        correlationId: "c1",
        fieldErrors: { customBody: "That signature is too long" },
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const items = await screen.findAllByRole("listitem");
    await user.click(within(items.find((item) => within(item).queryByText("Long"))!).getByRole("button", { name: "Edit" }));
    expect(screen.getByText("Keep it to 2,000 characters (2,001 now).")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save signature" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    const again = await screen.findAllByRole("listitem");
    await user.click(within(again.find((item) => within(item).queryByText("Short"))!).getByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Save signature" }));
    expect(await screen.findByText("That signature is too long (4 now).")).toBeInTheDocument();
  });

  it("holds every Make default while one is on its way", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [
        aSignature({ isDefault: true }),
        aSignature({ id: "s2", name: "Short reply" }),
        aSignature({ id: "s3", name: "Minimal" }),
      ],
      sources: SOURCES,
      limit: 10,
    });
    vi.mocked(signaturesApi.makeDefaultSignature).mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderPage();

    const [first, second] = await screen.findAllByRole("button", { name: "Make default" });
    await user.click(first);
    await waitFor(() => expect(second).toBeDisabled());
  });
});
