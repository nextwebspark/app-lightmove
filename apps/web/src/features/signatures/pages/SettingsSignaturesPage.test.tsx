import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser } from "../../../test/fixtures/user";
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
    text: "-- \nAlok Kumar",
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
    vi.mocked(signaturesApi.previewSignature).mockResolvedValue({ html: "<div>preview</div>", text: "-- " });
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

    expect(screen.getByLabelText(/^Title/)).toHaveAttribute("placeholder", "Head of Talent");
    expect(screen.getAllByText("From your profile").length).toBeGreaterThan(0);
    await user.type(screen.getByLabelText(/^Address/), "Festival Tower");
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
    await user.type(screen.getByLabelText(/^Website/), "not a site");
    await user.click(screen.getByRole("button", { name: "Save signature" }));

    expect(await screen.findByText("Enter a website address, like yourcompany.com")).toBeInTheDocument();
  });
});
