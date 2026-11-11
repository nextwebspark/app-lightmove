import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import * as signaturesApi from "../../signatures/api/signaturesApi";
import * as mailboxApi from "../api/mailboxApi";
import { SequenceEditorPage } from "./SequenceEditorPage";

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", fullName: "Yara Haddad", workspace: { id: "w1", mode: "COMPANY", roles: ["MEMBER"] } } }),
}));

vi.mock("../api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/mailboxApi")>()),
  getMailbox: vi.fn(),
}));

vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/candidatesApi")>()),
  getCandidates: vi.fn(),
}));

vi.mock("../../signatures/api/signaturesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../signatures/api/signaturesApi")>()),
  listSignatures: vi.fn(),
}));

const PROJECT = { id: "p1", positionTitle: "Group CFO" };

const SIGNATURE_SOURCES = {
  name: "Yara Haddad",
  title: null,
  phone: null,
  linkedinUrl: null,
  email: "yara@firm.example",
  company: "Firm",
  website: null,
  photoUrl: null,
  logoAvailable: false,
  bookingLinkOffered: false,
};

const DEFAULT_SIGNATURE: signaturesApi.EmailSignature = {
  id: "sig",
  name: "Formal",
  layout: "MINIMAL",
  fields: { name: null, title: null, phone: null, linkedinUrl: null, company: null, website: null, address: null },
  showPhoto: false,
  showLogo: false,
  showBookingLink: false,
  customBody: null,
  disclaimer: null,
  isDefault: true,
  updatedAt: "2026-11-01T10:00:00Z",
  html: "<div>Yara Haddad</div>",
};

function Where() {
  return <output aria-label="Location">{useLocation().pathname}</output>;
}

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={["/projects/p1/outreach/sequences/new"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project: PROJECT }} />}>
              <Route path="/projects/:projectId/outreach/sequences/:sequenceId" element={<SequenceEditorPage />} />
              <Route path="*" element={null} />
            </Route>
          </Routes>
          <Where />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SequenceEditorPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(candidatesApi.getCandidates).mockReturnValue(new Promise(() => {}));
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      offered: true,
      providers: ["google"],
      connection: null,
      bookingLinkOffered: false,
    });
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [],
      sources: SIGNATURE_SOURCES,
      limit: 10,
    });
  });

  it("writes a new sequence under the Outreach header, a click from the conversations", async () => {
    renderEditor();

    const views = await screen.findByRole("radiogroup", { name: "Outreach view" });
    expect(within(views).getByRole("radio", { name: "Sequences" })).toBeChecked();
    expect(screen.getByRole("textbox", { name: "Sequence name" })).toHaveValue("New sequence");
    expect(await screen.findByRole("button", { name: "Connect mailbox" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "change it in your profile" })).toHaveAttribute("href", "/settings/profile");

    await userEvent.click(within(views).getByRole("radio", { name: "Conversations" }));

    await waitFor(() => expect(screen.getByLabelText("Location")).toHaveTextContent(/^\/projects\/p1\/outreach$/));
  });

  it("previews the sender's default signature under each step", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [DEFAULT_SIGNATURE],
      sources: SIGNATURE_SOURCES,
      limit: 10,
    });
    renderEditor();

    expect(await screen.findByTitle("Signature")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Step 2" }));

    expect(screen.getByTitle("Signature")).toBeInTheDocument();
  });

  it("previews no signature once a step's token is taken out", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [DEFAULT_SIGNATURE],
      sources: SIGNATURE_SOURCES,
      limit: 10,
    });
    renderEditor();
    expect(await screen.findByTitle("Signature")).toBeInTheDocument();

    const [first] = screen.getAllByRole("textbox", { name: "Email body" });
    await userEvent.clear(first);
    await userEvent.type(first, "Hi there");

    expect(screen.queryByTitle("Signature")).not.toBeInTheDocument();
  });

  it("signs the defaults off with the signature, which is the sender's first name while they keep none", async () => {
    renderEditor();

    const bodies = await screen.findAllByRole("textbox", { name: "Email body" });
    expect(bodies).toHaveLength(3);
    for (const body of bodies) {
      expect((body as HTMLTextAreaElement).value).toMatch(/\{\{signature\}\}$/);
      expect((body as HTMLTextAreaElement).value).not.toContain("{{senderFirstName}}");
    }
    expect(await screen.findByText("Yara")).toBeInTheDocument();
    expect(screen.queryByTitle("Signature")).not.toBeInTheDocument();
  });

  it("goes back to the list from the header's Sequences", async () => {
    renderEditor();

    const views = await screen.findByRole("radiogroup", { name: "Outreach view" });
    await userEvent.click(within(views).getByRole("radio", { name: "Sequences" }));

    await waitFor(() =>
      expect(screen.getByLabelText("Location")).toHaveTextContent(/^\/projects\/p1\/outreach\/sequences$/),
    );
  });
});
