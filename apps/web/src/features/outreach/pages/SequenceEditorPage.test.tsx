import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { CandidatesPage } from "../../candidates/api/types";
import * as emailTemplatesApi from "../../emailtemplates/api/emailTemplatesApi";
import type { EmailTemplate } from "../../emailtemplates/api/emailTemplatesApi";
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

vi.mock("../../emailtemplates/api/emailTemplatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../emailtemplates/api/emailTemplatesApi")>()),
  listEmailTemplates: vi.fn(),
  createEmailTemplate: vi.fn(),
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
  fields: { name: null, title: null, phone: null, linkedinUrl: null, company: null, website: null, address: null, mobile: null },
  showPhoto: false,
  photoShape: "CIRCLE",
  showLogo: false,
  showBookingLink: false,
  customBody: null,
  footerPictures: [],
  disclaimer: null,
  isDefault: true,
  updatedAt: "2026-11-01T10:00:00Z",
  html: "<div>Yara Haddad</div>",
};

function aTemplate(overrides: Partial<EmailTemplate>): EmailTemplate {
  return {
    id: "t1",
    name: "Warm intro",
    subject: "A thought for {{firstName}}",
    body: "Hi {{firstName|there}},\n\nA **short** note.",
    plainBody: "Hi {{firstName|there}},\n\nA short note.",
    shared: true,
    mine: false,
    canEdit: false,
    owner: { userId: "u2", fullName: "Omar Saleh", avatarUrl: null },
    updatedAt: "2026-11-01T10:00:00Z",
    version: 0,
    ...overrides,
  };
}

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

  it("previews the sender's default signature under each step, added at Start", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue({
      signatures: [DEFAULT_SIGNATURE],
      sources: SIGNATURE_SOURCES,
      limit: 10,
    });
    renderEditor();

    expect(await screen.findByTitle("Signature")).toBeInTheDocument();
    expect(screen.getByText(/as Add to sequence adds it\. You can choose another when you start/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Step 2" }));

    expect(screen.getByTitle("Signature")).toBeInTheDocument();
  });

  it("refuses a {{signature}} written into a step, holding Save: Include signature adds it at Start", async () => {
    const user = userEvent.setup();
    renderEditor();

    const [first] = await screen.findAllByRole("textbox", { name: "Email body" });
    await user.click(first);
    await user.paste("\n\n{{ signature }}");

    expect(screen.getByText(/Leave \{\{signature\}\} out: Include signature adds yours/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save sequence" })).toBeDisabled();
  });

  it("offers no signature variable, and its default steps hold none while the sender keeps no signature", async () => {
    renderEditor();

    const bodies = await screen.findAllByRole("textbox", { name: "Email body" });
    expect(bodies).toHaveLength(3);
    for (const body of bodies) {
      expect((body as HTMLTextAreaElement).value).not.toContain("{{signature}}");
      expect((body as HTMLTextAreaElement).value).not.toContain("{{senderFirstName}}");
    }
    const bar = screen.getByRole("toolbar", { name: "Insert a variable" });
    expect(within(bar).queryByRole("button", { name: "{{signature}}" })).not.toBeInTheDocument();
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

  it("copies a shared template into the first email as plain text, its subject too, once asked", async () => {
    const user = userEvent.setup();
    vi.mocked(emailTemplatesApi.listEmailTemplates).mockResolvedValue({
      templates: [aTemplate({}), aTemplate({ id: "t2", name: "Too long", plainBody: "x".repeat(5001), mine: true })],
      limit: 100,
      bookingLinkOffered: false,
      recentlyUsed: [],
    });
    renderEditor();

    const [fromTemplate] = await screen.findAllByRole("button", { name: "From template" });
    await user.click(fromTemplate);
    const picker = await screen.findByRole("dialog", { name: "From template" });
    expect(within(picker).getByText(/bold, lists and links come in as plain words/)).toBeInTheDocument();
    expect(within(picker).getByText(/subject comes into the first email too/)).toBeInTheDocument();
    expect(within(picker).getByRole("button", { name: /Too long/ })).toBeDisabled();
    expect(within(picker).getByText(/5,001 characters as plain text: a sequence email holds 5,000/)).toBeInTheDocument();

    await user.click(within(picker).getByRole("button", { name: /Warm intro/ }));
    const ask = screen.getByRole("alertdialog", { name: "Replace this step's text?" });
    await user.click(within(ask).getByRole("button", { name: "Replace" }));

    const [firstBody] = screen.getAllByRole("textbox", { name: "Email body" });
    expect(firstBody).toHaveValue("Hi {{firstName|there}},\n\nA short note.");
    expect(screen.getByRole("textbox", { name: "Subject" })).toHaveValue("A thought for {{firstName}}");
    expect(await screen.findByText(/Step 1 now reads “Warm intro”/)).toBeInTheDocument();
  });

  it("copies a template into a cleared follow-up without its subject or a question", async () => {
    const user = userEvent.setup();
    vi.mocked(emailTemplatesApi.listEmailTemplates).mockResolvedValue({
      templates: [aTemplate({})],
      limit: 100,
      bookingLinkOffered: false,
      recentlyUsed: [],
    });
    renderEditor();

    const bodies = await screen.findAllByRole("textbox", { name: "Email body" });
    await user.clear(bodies[1]);
    await user.click(screen.getAllByRole("button", { name: "From template" })[1]);
    const picker = await screen.findByRole("dialog", { name: "From template" });
    expect(within(picker).getByText(/a template's subject is left out/)).toBeInTheDocument();
    await user.click(within(picker).getByRole("button", { name: /Warm intro/ }));

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.getAllByRole("textbox", { name: "Email body" })[1]).toHaveValue(
      "Hi {{firstName|there}},\n\nA short note.",
    );
    expect(screen.getByRole("textbox", { name: "Subject" })).toHaveValue("Confidential: {{positionTitle}}");
  });

  it("saves a step as a template, its variables kept, and refuses one using the opener", async () => {
    const user = userEvent.setup();
    vi.mocked(emailTemplatesApi.createEmailTemplate).mockResolvedValue(aTemplate({ name: "Nudge", mine: true }));
    renderEditor();

    const saveButtons = await screen.findAllByRole("button", { name: "Save as template" });
    await user.click(saveButtons[0]);
    const refused = screen.getByRole("dialog", { name: "Save step as template" });
    expect(within(refused).getByRole("alert")).toHaveTextContent(/This step uses \{\{opener\}\}/);
    expect(within(refused).queryByRole("button", { name: "Save template" })).not.toBeInTheDocument();
    await user.click(within(refused).getByRole("button", { name: "Close" }));

    await user.click(saveButtons[2]);
    const cancelled = screen.getByRole("dialog", { name: "Save step as template" });
    await user.type(within(cancelled).getByRole("textbox", { name: "Name" }), "Not this one");
    await user.click(within(cancelled).getByRole("switch", { name: "Share with workspace" }));
    await user.click(within(cancelled).getByRole("button", { name: "Cancel" }));

    await user.click(saveButtons[1]);
    const dialog = screen.getByRole("dialog", { name: "Save step as template" });
    expect(within(dialog).getByRole("textbox", { name: "Name" })).toHaveValue("");
    expect(within(dialog).getByRole("switch", { name: "Share with workspace" })).not.toBeChecked();
    await user.type(within(dialog).getByRole("textbox", { name: "Name" }), "Nudge");
    await user.click(within(dialog).getByRole("button", { name: "Save template" }));

    await waitFor(() =>
      expect(emailTemplatesApi.createEmailTemplate).toHaveBeenCalledWith({
        name: "Nudge",
        subject: null,
        body: "Hi {{firstName}},\n\nFollowing up on my note below, in case it slipped past. Happy to work around your diary.",
        shared: false,
      }),
    );
  });

  it("marks in the preview a variable this person has nothing on file for, and a fallback standing in", async () => {
    vi.mocked(candidatesApi.getCandidates).mockResolvedValue({
      candidates: [
        {
          id: "c1",
          fullName: "Priya Raman",
          title: null,
          companyName: "Target Group",
          locationCity: null,
          locationCountry: null,
          contacts: { emails: [], phones: [] },
        },
      ],
      totalCount: 1,
      page: 0,
    } as unknown as CandidatesPage);
    renderEditor();

    const [firstBody] = await screen.findAllByRole("textbox", { name: "Email body" });
    await userEvent.clear(firstBody);
    await userEvent.type(firstBody, "In {{{{location}} as {{{{currentTitle|a leader}}");

    const blank = await screen.findByTitle("Nothing on file for Priya Raman: it goes out blank");
    expect(blank).toHaveTextContent("{{location}}");
    expect(screen.getByTitle(/the fallback stands in for \{\{currentTitle\}\}/)).toHaveTextContent("a leader");
  });
});
