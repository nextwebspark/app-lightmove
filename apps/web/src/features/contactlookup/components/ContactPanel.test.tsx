import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { ApiRequestError } from "../../../lib/apiClient";
import { copyText } from "../../../lib/clipboard";
import * as billingApi from "../../billing/api/billingApi";
import { aBilling } from "../../billing/test/fixtures";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate, CandidateContacts, CandidateEmail } from "../../candidates/api/types";
import * as contactLookupApi from "../api/contactLookupApi";
import { ContactDetailsDialog } from "./ContactDetailsDialog";
import { ContactPanel } from "./ContactPanel";

vi.mock("../api/contactLookupApi", async (importOriginal) => ({
  ...(await importOriginal<typeof contactLookupApi>()),
  findEmail: vi.fn(),
  findPhone: vi.fn(),
}));

vi.mock("../../../lib/clipboard", () => ({ copyText: vi.fn() }));

vi.mock("../../billing/api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof billingApi>()),
  getBilling: vi.fn(),
}));

vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatesApi>()),
  replaceContacts: vi.fn(),
  updateCandidate: vi.fn(),
}));

const NONE: CandidateContacts = {
  emails: [],
  phones: [],
  emailsLookedUpAt: null,
  phonesLookedUpAt: null,
  source: null,
};

const hakan: Candidate = {
  id: "c1",
  triageCompanyId: null,
  companyName: "Alac Partners",
  fullName: "Hakan Alac",
  title: "Managing Partner",
  seniority: null,
  status: "identified",
  linkedinUrl: "https://www.linkedin.com/in/hakan-alac",
  locationCountry: null,
  locationCity: null,
  nationality: null,
  gender: null,
  yearsExperience: null,
  aiInferredFields: [],
  summary: null,
  personId: "person-1",
  linkedinUrlLocked: false,
  compensation: {
    currency: null,
    baseSalary: null,
    bonus: null,
    allowances: null,
    longTermIncentive: null,
    noticePeriod: null,
    allowanceLines: [],
    longTermIncentiveTypes: [],
  },
  career: [],
  languages: [],
  education: [],
  skills: [],
  source: "extension",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-09-01T09:00:00Z",
  enrichedAt: null,
  contacts: NONE,
};

const found = (address: string, kind: CandidateEmail["kind"], verified = false): CandidateEmail => ({
  address,
  kind,
  verified,
  status: verified ? "Verified" : null,
  source: "contactout",
  foundAt: "2026-09-16T17:27:16Z",
});

const renderPanel = (
  candidate: Candidate,
  props: Partial<Parameters<typeof ContactPanel>[0]> = {},
) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <ContactPanel
          projectId="p1"
          candidate={candidate}
          canWrite
          lookupOffered
          onSaved={() => {}}
          {...props}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );

/**
 * The Contact section as channel rows. What matters: the screen says only what the provider said
 * (work, personal, verified), a spent channel is never re-sold, and a miss is shown to every reader.
 */
describe("ContactPanel", () => {
  it("lists the addresses in the server's order, pilled as the provider said, with a green Verified where verified", () => {
    renderPanel({
      ...hakan,
      contacts: {
        ...NONE,
        emails: [
          found("hakan@alacpartners.com", "work", true),
          found("hakanalac@gmail.com", "personal"),
          found("listed.only@elsewhere.example", null),
        ],
        emailsLookedUpAt: "2026-09-16T17:27:16Z",
        source: "contactout",
      },
    });

    const addresses = screen.getAllByRole("link", { name: /@/ }).map((link) => link.textContent);
    expect(addresses).toEqual([
      "hakan@alacpartners.com",
      "hakanalac@gmail.com",
      "listed.only@elsewhere.example",
    ]);
    expect(screen.getByRole("link", { name: "hakan@alacpartners.com" })).toHaveAttribute(
      "href",
      "mailto:hakan@alacpartners.com",
    );
    // One green Verified pill, after the kind, on the one address the provider vouched for.
    const verified = screen.getAllByText("Verified");
    expect(verified).toHaveLength(1);
    expect(verified[0].closest("li")).toHaveTextContent(/hakan@alacpartners\.com.*work.*Verified/);
    expect(screen.getByText("work")).toBeInTheDocument();
    expect(screen.getByText("personal")).toBeInTheDocument();
    // Asked and answered: nothing left to buy on this channel, and no label says who answered —
    // that is the audit trail's, not the row's.
    expect(screen.queryByText(/via ContactOut/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Find/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Find email|Find more/ })).not.toBeInTheDocument();
  });

  it("draws no kind pill and no Verified for an address the provider did not classify", () => {
    renderPanel({
      ...hakan,
      contacts: { ...NONE, emails: [found("listed.only@elsewhere.example", null)], source: "contactout" },
    });

    expect(screen.queryByText("work")).not.toBeInTheDocument();
    expect(screen.queryByText("personal")).not.toBeInTheDocument();
    expect(screen.queryByText("Verified")).not.toBeInTheDocument();
  });

  it("a phone-only lookup spends the phone channel and leaves email still offered", () => {
    renderPanel({
      ...hakan,
      contacts: {
        ...NONE,
        phones: [{ number: "+61 421 904 554", kind: null, verified: false, status: null, source: "contactout", foundAt: "2026-09-16T17:27:24Z" }],
        phonesLookedUpAt: "2026-09-16T17:27:24Z",
        source: "contactout",
      },
    });

    expect(screen.getByRole("link", { name: "+61 421 904 554" })).toHaveAttribute(
      "href",
      "tel:+61421904554",
    );
    expect(screen.queryByRole("button", { name: /Find phone|Find more/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Find email/ })).toBeInTheDocument();
  });

  it("shows a miss to a reader who could never press the button", () => {
    renderPanel(
      {
        ...hakan,
        contacts: { ...NONE, phonesLookedUpAt: "2026-09-16T17:55:24Z", source: "contactout" },
      },
      { canWrite: false, lookupOffered: false },
    );

    expect(screen.getByText("No phone on record")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Find/ })).not.toBeInTheDocument();
  });

  it("keeps a typed value as a value, and a miss beside it as a miss", () => {
    renderPanel({
      ...hakan,
      contacts: {
        ...NONE,
        emails: [{ ...found("typed@them.example", null), source: "manual" }],
        emailsLookedUpAt: "2026-09-16T17:55:24Z",
        source: "contactout",
      },
    });

    expect(screen.getByRole("link", { name: "typed@them.example" })).toBeInTheDocument();
    // The email channel was spent; nothing is offered on it again. Phone was never asked.
    expect(screen.queryByRole("button", { name: /Find email|Find more/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Find phone" })).toBeInTheDocument();
  });

  it("offers Find email on an empty channel and Find more beside a typed one", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    const { rerender } = renderPanel(hakan);
    expect(screen.getByRole("button", { name: "Find email" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Find phone" })).toBeEnabled();
    expect(await screen.findByText("1 credit")).toBeInTheDocument();
    expect(screen.getByText("5 credits")).toBeInTheDocument();

    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <ToastProvider>
          <ContactPanel
            projectId="p1"
            candidate={{
              ...hakan,
              contacts: { ...NONE, emails: [{ ...found("typed@them.example", null), source: "manual" }] },
            }}
            canWrite
            lookupOffered
            onSaved={() => {}}
          />
        </ToastProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("button", { name: "Find more" })).toHaveAttribute(
      "title",
      expect.stringContaining("Your entry is kept"),
    );
  });

  it("disables both buttons, and says why, until the person has a LinkedIn profile", () => {
    renderPanel({ ...hakan, linkedinUrl: null });

    expect(screen.getByRole("button", { name: "Find email" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Find phone" })).toBeDisabled();
    expect(screen.getAllByText(/Add their LinkedIn profile URL so Uncava can find contacts/)).toHaveLength(2);
  });

  it("keeps the values on screen while a lookup runs", async () => {
    vi.mocked(contactLookupApi.findEmail).mockReturnValue(new Promise(() => {}));
    renderPanel({
      ...hakan,
      contacts: { ...NONE, emails: [{ ...found("typed@them.example", null), source: "manual" }] },
    });

    await userEvent.click(screen.getByRole("button", { name: "Find more" }));

    expect(await screen.findByRole("button", { name: /Finding…/ })).toBeDisabled();
    expect(screen.getByRole("link", { name: "typed@them.example" })).toBeInTheDocument();
    // One lookup at a time: the other channel waits.
    expect(screen.getByRole("button", { name: "Find phone" })).toBeDisabled();
  });

  it("hands the answer back and toasts a miss", async () => {
    const onSaved = vi.fn();
    const answered = { ...hakan, contacts: { ...NONE, emailsLookedUpAt: "2026-09-16T17:55:24Z", source: "contactout" } };
    vi.mocked(contactLookupApi.findEmail).mockResolvedValue({ outcome: "none", candidate: answered, creditsSpent: 0, creditsLeft: 10 });
    renderPanel(hakan, { onSaved });

    await userEvent.click(screen.getByRole("button", { name: "Find email" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(answered));
    expect(await screen.findByRole("status")).toHaveTextContent("No email on record for Hakan Alac");
  });

  it("copies a value and says so", async () => {
    vi.mocked(copyText).mockResolvedValue(true);
    renderPanel({
      ...hakan,
      contacts: {
        ...NONE,
        phones: [{ number: "+61 421 904 554", kind: null, verified: false, status: null, source: "contactout", foundAt: "2026-09-16T17:27:24Z" }],
      },
    });

    await userEvent.click(screen.getByRole("button", { name: "Copy phone" }));

    expect(copyText).toHaveBeenCalledWith("+61 421 904 554");
    expect(await screen.findByRole("status")).toHaveTextContent("Phone copied");
  });

  it("keeps a missing-profile refusal inside the section, and toasts everything else", async () => {
    vi.mocked(contactLookupApi.findEmail).mockRejectedValueOnce(
      new ApiRequestError({
        code: "CONTACT_LOOKUP_NO_PROFILE",
        detail: "no profile",
        status: 409,
        correlationId: "x",
      }),
    );
    vi.mocked(contactLookupApi.findPhone).mockRejectedValueOnce(
      new ApiRequestError({
        code: "CONTACT_LOOKUP_NO_CREDITS",
        detail: "no credits",
        status: 409,
        correlationId: "x",
      }),
    );
    renderPanel(hakan);

    await userEvent.click(screen.getByRole("button", { name: "Find email" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/LinkedIn profile URL first/);

    await userEvent.click(screen.getByRole("button", { name: "Find phone" }));
    expect((await screen.findByText(/used this period's contact lookup credits/)).closest('[role="alert"]')).not.toBeNull();
    expect(screen.getByRole("button", { name: "Find phone" })).toBeEnabled();
  });

  it("leaves running out of credits to the out-of-credits sheet rather than toasting it", async () => {
    vi.mocked(contactLookupApi.findPhone).mockRejectedValueOnce(
      new ApiRequestError({
        code: "INSUFFICIENT_CREDITS",
        detail: "Your workspace is out of contact credits",
        status: 402,
        correlationId: "x",
        required: 5,
        available: 0,
      }),
    );
    renderPanel(hakan);

    await userEvent.click(screen.getByRole("button", { name: "Find phone" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Find phone" })).toBeEnabled());
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  /**
   * The pencil's edit mode: the same rows as inputs. What matters: every line can be changed, added
   * or removed and all of it lands in one save; a duplicate is caught before anything is sent; and the
   * profile link is not here — it is edited with the details.
   */
  describe("the primary", () => {
    const two: Candidate = {
      ...hakan,
      contacts: {
        ...NONE,
        emails: [found("hakanalac@gmail.com", "personal"), found("hakan@alacpartners.com", "work", true)],
        phones: [{ number: "+971 50 000 0001", kind: null, verified: false, status: null, source: "manual", foundAt: "2026-09-16T17:27:24Z" }],
      },
    };

    beforeEach(() => vi.mocked(candidatesApi.replaceContacts).mockReset());

    it("stars the first of a channel holding several, and makes another primary in one press", async () => {
      const onSaved = vi.fn();
      vi.mocked(candidatesApi.replaceContacts).mockResolvedValue(two);
      renderPanel(two, { onSaved });

      expect(screen.getByRole("img", { name: "Primary email" }).closest("li")).toHaveTextContent("hakanalac@gmail.com");
      // One phone is primary by being the only one: nothing to mark.
      expect(screen.queryByRole("img", { name: "Primary phone" })).not.toBeInTheDocument();

      await userEvent.click(screen.getByRole("button", { name: "Make primary email" }));

      await waitFor(() =>
        expect(candidatesApi.replaceContacts).toHaveBeenCalledWith("p1", "c1", {
          emails: [
            { value: "hakanalac@gmail.com", kind: "personal", verified: false, primary: false },
            { value: "hakan@alacpartners.com", kind: "work", verified: true, primary: true },
          ],
          phones: [{ value: "+971 50 000 0001", kind: null, verified: false, primary: true }],
        }),
      );
      expect(onSaved).toHaveBeenCalledWith(two);
    });

    it("marks the primary for a reader but offers them nothing to change", () => {
      renderPanel(two, { canWrite: false });

      expect(screen.getByRole("img", { name: "Primary email" })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Make primary email" })).not.toBeInTheDocument();
    });
  });

  describe("editing", () => {
    beforeEach(() => {
      vi.mocked(candidatesApi.replaceContacts).mockReset();
      vi.mocked(candidatesApi.updateCandidate).mockReset();
    });

    const held: Candidate = {
      ...hakan,
      source: "manual",
      contacts: {
        ...NONE,
        emails: [found("hakan@alacpartners.com", "work", true), { ...found("old@them.example", null), source: "manual" }],
        phones: [{ number: "+61 421 904 554", kind: null, verified: false, status: null, source: "contactout", foundAt: "2026-09-16T17:27:24Z" }],
        source: "contactout",
      },
    };

    it("saves every line — added, retagged, verified, removed — as one contacts write", async () => {
      const onDone = vi.fn();
      vi.mocked(candidatesApi.replaceContacts).mockResolvedValue(held);
      renderPanel(held, { editing: true, onDone, onCancel: () => {} });

      // Retag the second address, vouch for it, drop the phone, add a new phone.
      await userEvent.selectOptions(screen.getByRole("combobox", { name: "Email 2 kind" }), "personal");
      await userEvent.click(screen.getByRole("switch", { name: "Email 2 verified" }));
      await userEvent.click(screen.getByRole("button", { name: "Remove phone 1" }));
      await userEvent.click(screen.getByRole("button", { name: "Add phone" }));
      await userEvent.type(screen.getByRole("textbox", { name: "Phone 1" }), "+971 50 000 0000");
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      await waitFor(() =>
        expect(candidatesApi.replaceContacts).toHaveBeenCalledWith("p1", "c1", {
          emails: [
            { value: "hakan@alacpartners.com", kind: "work", verified: true, primary: true },
            { value: "old@them.example", kind: "personal", verified: true, primary: false },
          ],
          phones: [{ value: "+971 50 000 0000", kind: null, verified: false, primary: true }],
        }),
      );
      // The contacts have their own write: the profile itself is not re-sent.
      expect(candidatesApi.updateCandidate).not.toHaveBeenCalled();
      expect(onDone).toHaveBeenCalledWith(held);
      expect(await screen.findByRole("status")).toHaveTextContent("Contact saved");
    });

    it("moves the primary star to another line and saves it with the rest", async () => {
      vi.mocked(candidatesApi.replaceContacts).mockResolvedValue(held);
      renderPanel(held, { editing: true, onDone: () => {}, onCancel: () => {} });

      expect(screen.getByRole("img", { name: "Primary email" })).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "Make primary email" }));
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      await waitFor(() =>
        expect(candidatesApi.replaceContacts).toHaveBeenCalledWith("p1", "c1", {
          emails: [
            { value: "hakan@alacpartners.com", kind: "work", verified: true, primary: false },
            { value: "old@them.example", kind: null, verified: false, primary: true },
          ],
          phones: [{ value: "+61 421 904 554", kind: null, verified: false, primary: true }],
        }),
      );
    });

    it("refuses the same address twice before anything is sent", async () => {
      renderPanel(held, { editing: true, onDone: () => {}, onCancel: () => {} });

      await userEvent.click(screen.getByRole("button", { name: "Add email" }));
      await userEvent.type(screen.getByRole("textbox", { name: "Email 3" }), "HAKAN@alacpartners.com");
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("This email is already listed");
      expect(candidatesApi.replaceContacts).not.toHaveBeenCalled();
    });

    it("leaves the profile link out, read or edited: it is edited with the details", () => {
      const { unmount } = renderPanel(held);
      expect(screen.queryByText("LinkedIn")).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /linkedin\.com/ })).not.toBeInTheDocument();
      unmount();

      renderPanel(held, { editing: true, onDone: () => {}, onCancel: () => {} });
      expect(screen.queryByRole("textbox", { name: "LinkedIn" })).not.toBeInTheDocument();
    });

    it("cancels without sending anything", async () => {
      const onCancel = vi.fn();
      renderPanel(held, { editing: true, onDone: () => {}, onCancel });

      await userEvent.type(screen.getByRole("textbox", { name: "Email 1" }), "x");
      await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

      expect(onCancel).toHaveBeenCalled();
      expect(candidatesApi.replaceContacts).not.toHaveBeenCalled();
    });
  });

  describe("the contact details window", () => {
    const held: Candidate = {
      ...hakan,
      contacts: { ...NONE, emails: [{ ...found("hakan@alacpartners.com", "work"), source: "manual" }] },
    };
    const renderWindow = (props: Partial<Parameters<typeof ContactDetailsDialog>[0]> = {}) =>
      render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <ToastProvider>
            <ContactDetailsDialog
              projectId="p1"
              candidate={held}
              lookupOffered
              onSaved={() => {}}
              onClose={() => {}}
              {...props}
            />
          </ToastProvider>
        </QueryClientProvider>,
      );

    beforeEach(() => {
      vi.mocked(candidatesApi.replaceContacts).mockReset();
      vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    });

    it("saves every line in one write and closes", async () => {
      const onSaved = vi.fn();
      const onClose = vi.fn();
      vi.mocked(candidatesApi.replaceContacts).mockResolvedValue(held);
      renderWindow({ onSaved, onClose });

      await userEvent.click(screen.getByRole("button", { name: "Add phone" }));
      await userEvent.type(screen.getByRole("textbox", { name: "Phone 1" }), "+971 50 000 0000");
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      await waitFor(() =>
        expect(candidatesApi.replaceContacts).toHaveBeenCalledWith("p1", "c1", {
          emails: [{ value: "hakan@alacpartners.com", kind: "work", verified: false, primary: true }],
          phones: [{ value: "+971 50 000 0000", kind: null, verified: false, primary: true }],
        }),
      );
      expect(onSaved).toHaveBeenCalledWith(held);
      expect(onClose).toHaveBeenCalled();
    });

    it("offers Find on each channel, and holds it while the lines have unsaved edits", async () => {
      renderWindow();

      expect(screen.getByRole("button", { name: /Find more/ })).toBeEnabled();
      expect(screen.getByRole("button", { name: /Find phone/ })).toBeEnabled();

      await userEvent.type(screen.getByRole("textbox", { name: "Email 1" }), "x");

      expect(screen.getByRole("button", { name: /Find more/ })).toBeDisabled();
      expect(screen.getByRole("button", { name: /Find phone/ })).toBeDisabled();
    });

    it("says an empty channel is empty, and holds Save until something changes", async () => {
      renderWindow();

      expect(await screen.findByText("No phone number yet")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Add phone" })).toBeEnabled();
      expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    });

    it("tags a line and hands the star on when the primary is removed", async () => {
      vi.mocked(candidatesApi.replaceContacts).mockResolvedValue(held);
      renderWindow({
        candidate: {
          ...held,
          contacts: {
            ...NONE,
            emails: [
              { ...found("hakan@alacpartners.com", "work"), source: "manual" },
              { ...found("hakan@gmail.com", null), source: "manual" },
            ],
          },
        },
      });

      await userEvent.click(screen.getByRole("button", { name: "Remove email 1" }));
      await userEvent.click(
        within(screen.getByRole("group", { name: "Email 1 kind" })).getByRole("button", { name: "Personal" }),
      );

      expect(screen.getByRole("img", { name: "Primary email" })).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "Save" }));
      await waitFor(() =>
        expect(candidatesApi.replaceContacts).toHaveBeenCalledWith("p1", "c1", {
          emails: [{ value: "hakan@gmail.com", kind: "personal", verified: false, primary: true }],
          phones: [],
        }),
      );
    });
  });
});
