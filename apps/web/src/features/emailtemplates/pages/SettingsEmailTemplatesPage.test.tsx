import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { typeInto } from "../../../test/noteEditor";
import * as signaturesApi from "../../signatures/api/signaturesApi";
import * as emailTemplatesApi from "../api/emailTemplatesApi";
import type { EmailTemplate, EmailTemplates } from "../api/emailTemplatesApi";
import { SettingsEmailTemplatesPage } from "./SettingsEmailTemplatesPage";

vi.mock("../api/emailTemplatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/emailTemplatesApi")>()),
  listEmailTemplates: vi.fn(),
  createEmailTemplate: vi.fn(),
  updateEmailTemplate: vi.fn(),
  shareEmailTemplate: vi.fn(),
  duplicateEmailTemplate: vi.fn(),
  deleteEmailTemplate: vi.fn(),
}));

vi.mock("../../signatures/api/signaturesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../signatures/api/signaturesApi")>()),
  listSignatures: vi.fn(),
}));

function aTemplate(overrides: Partial<EmailTemplate>): EmailTemplate {
  return {
    id: "t1",
    name: "Thanks — let's find a time",
    subject: null,
    body: "Thank you, {{firstName}}.",
    plainBody: "Thank you, {{firstName}}.",
    shared: false,
    mine: true,
    canEdit: true,
    owner: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
    updatedAt: "2026-11-01T10:00:00Z",
    version: 0,
    ...overrides,
  };
}

const MINE = aTemplate({});
const OMARS = aTemplate({
  id: "t2",
  name: "Standard decline",
  body: "Thanks {{firstName|there}}",
  plainBody: "Thanks {{firstName|there}}",
  shared: true,
  mine: false,
  canEdit: false,
  owner: { userId: "u2", fullName: "Omar Saleh", avatarUrl: null },
});

function listing(templates: EmailTemplate[]): EmailTemplates {
  return { templates, limit: 100, bookingLinkOffered: false, recentlyUsed: [] };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsEmailTemplatesPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("Settings → Email templates", () => {
  beforeEach(() => {
    vi.mocked(emailTemplatesApi.listEmailTemplates).mockReset().mockResolvedValue(listing([MINE, OMARS]));
    vi.mocked(emailTemplatesApi.createEmailTemplate).mockReset().mockResolvedValue(MINE);
    vi.mocked(emailTemplatesApi.updateEmailTemplate).mockReset();
    vi.mocked(emailTemplatesApi.shareEmailTemplate).mockReset();
    vi.mocked(emailTemplatesApi.deleteEmailTemplate).mockReset().mockResolvedValue(undefined);
    vi.mocked(signaturesApi.listSignatures).mockReset().mockResolvedValue({
      signatures: [],
      sources: {
        name: "Yara Haddad",
        title: null,
        phone: null,
        linkedinUrl: null,
        email: "yara@example.com",
        company: "Al-Futtaim",
        website: null,
        photoUrl: null,
        logoAvailable: false,
        bookingLinkOffered: false,
      },
      limit: 10,
    });
  });

  it("lists mine and the shared ones, and splits them by tab", async () => {
    const user = userEvent.setup();
    renderPage();
    const list = await screen.findByRole("list", { name: "Email templates" });
    expect(within(list).getByText(MINE.name)).toBeInTheDocument();
    expect(within(list).getByText("Standard decline")).toBeInTheDocument();
    expect(within(list).getByText("Shared")).toBeInTheDocument();
    expect(within(list).getByText("You")).toBeInTheDocument();
    expect(screen.getByText("1 of your 100")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Shared 1" }));
    expect(within(list).queryByText(MINE.name)).not.toBeInTheDocument();
    expect(within(list).getByText("Standard decline")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "All 2" }));
    await user.type(screen.getByRole("textbox", { name: "Search templates" }), "decline");
    expect(within(list).queryByText(MINE.name)).not.toBeInTheDocument();
  });

  it("offers a colleague's shared template to duplicate, never to edit or delete", async () => {
    const user = userEvent.setup();
    vi.mocked(emailTemplatesApi.duplicateEmailTemplate).mockResolvedValue(aTemplate({ id: "t3", name: "Copy of x" }));
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Actions for Standard decline" }));
    const menu = screen.getByRole("menu", { name: "Actions for Standard decline" });
    expect(within(menu).queryByRole("menuitem", { name: /Edit/ })).not.toBeInTheDocument();
    expect(within(menu).queryByRole("menuitem", { name: /Delete/ })).not.toBeInTheDocument();
    await user.click(within(menu).getByRole("menuitem", { name: /Duplicate/ }));
    await waitFor(() => expect(emailTemplatesApi.duplicateEmailTemplate).toHaveBeenCalledWith("t2"));
  });

  it("inserts variables from the sequence's Insert bar, as typed, and saves them", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "New template" }));

    const bar = screen.getByRole("toolbar", { name: "Insert a variable" });
    expect(within(bar).getAllByRole("button").map((button) => button.textContent)).toEqual([
      "{{firstName}}",
      "{{currentTitle}}",
      "{{currentCompany}}",
      "{{positionTitle}}",
      "{{location}}",
      "{{senderFirstName}}",
    ]);

    await user.type(screen.getByRole("textbox", { name: /Name/ }), "Intro");
    await user.click(screen.getByRole("textbox", { name: /Subject/ }));
    await user.click(within(bar).getByRole("button", { name: "{{positionTitle}}" }));
    expect(screen.getByRole("textbox", { name: /Subject/ })).toHaveValue("{{positionTitle}}");

    const email = await screen.findByRole("textbox", { name: "Email" });
    await typeInto(email, "at {{currentCompany}}, ");
    await user.click(within(bar).getByRole("button", { name: "{{senderFirstName}}" }));
    await user.click(screen.getByRole("switch", { name: "Share with workspace" }));
    await user.click(screen.getByRole("button", { name: "Save template" }));

    await waitFor(() =>
      expect(emailTemplatesApi.createEmailTemplate).toHaveBeenCalledWith({
        name: "Intro",
        subject: "{{positionTitle}}",
        body: "Hi {{firstName}},\n\nat {{currentCompany}}, {{senderFirstName}}",
        shared: true,
      }),
    );
  });

  it("holds Save on a variable no email can fill, and says which", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "New template" }));
    await user.type(screen.getByRole("textbox", { name: /Name/ }), "Signed");
    await user.type(screen.getByRole("textbox", { name: /Subject/ }), "For {{{{curentTitle}}");
    expect(screen.getByText(/\{\{curentTitle\}\} isn't a variable Uncava can fill/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save template" })).toBeDisabled();
  });

  it("asks before deleting a shared template, saying everyone loses it", async () => {
    const user = userEvent.setup();
    const shared = aTemplate({ shared: true, version: 3 });
    vi.mocked(emailTemplatesApi.listEmailTemplates).mockResolvedValue(listing([shared]));
    renderPage();
    await user.click(await screen.findByRole("button", { name: `Actions for ${shared.name}` }));
    await user.click(screen.getByRole("menuitem", { name: /Delete/ }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/everyone loses it/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Delete template" }));
    await waitFor(() => expect(emailTemplatesApi.deleteEmailTemplate).toHaveBeenCalledWith("t1", 3));
  });

  it("explains what a template is when there is none", async () => {
    vi.mocked(emailTemplatesApi.listEmailTemplates).mockResolvedValue(listing([]));
    renderPage();
    expect(await screen.findByText("No email templates yet")).toBeInTheDocument();
  });
});
