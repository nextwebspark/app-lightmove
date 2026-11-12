import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { isFittingPill, SHOWN, stubLayout } from "../../../test/stubLayout";
import type { CandidateContacts } from "../api/types";
import { PersonHeaderLayout, PillRow } from "./ProfileParts";

const SKILLS = ["Java", "Scala", "Spark", "Kafka", "Flink", "Beam"];

describe("PillRow", () => {
  afterEach(() => vi.restoreAllMocks());

  it("holds a long list to two lines, with the rest one press away", async () => {
    // Two lines of 100 hold two 48s each; "+N more" takes the second line's last place.
    stubLayout({ rowWidth: 100, widthOf: (element) => (isFittingPill(element) ? 48 : 40) });
    render(<PillRow label="Skills" values={SKILLS} />);

    for (const skill of ["Java", "Scala", "Spark"]) expect(screen.getByText(skill, SHOWN)).toBeInTheDocument();
    for (const skill of ["Kafka", "Flink", "Beam"]) expect(screen.queryByText(skill, SHOWN)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "+3 more", expanded: false }));
    for (const skill of SKILLS) expect(screen.getByText(skill, SHOWN)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "See less", expanded: true }));
    expect(screen.queryByText("Beam", SHOWN)).not.toBeInTheDocument();
  });

  it("offers nothing to unfold when every value fits", () => {
    stubLayout({ rowWidth: 400, widthOf: (element) => (isFittingPill(element) ? 48 : 40) });
    render(<PillRow label="Languages" values={["English", "Arabic"]} />);

    expect(screen.getByText("Arabic", SHOWN)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("PersonHeaderLayout", () => {
  const contact = { kind: null, verified: false, status: null, source: "manual" as const, foundAt: "2026-09-16T17:27:24Z" };
  const contacts = (emails: string[], phones: string[]): CandidateContacts => ({
    emails: emails.map((address) => ({ ...contact, address })),
    phones: phones.map((number) => ({ ...contact, number })),
    emailsLookedUpAt: null,
    phonesLookedUpAt: null,
    source: null,
  });
  const renderHeader = (
    held: CandidateContacts,
    props: Partial<Parameters<typeof PersonHeaderLayout>[0]> = {},
  ) =>
    render(
      <PersonHeaderLayout
        avatar={null}
        fullName="Aleksandra Mizinska"
        linkedinUrl={null}
        title="Managing Associate"
        contacts={held}
        employer={{ name: "ALAC Partners", logoUrl: null }}
        location="Dubai · United Arab Emirates"
        {...props}
      >
        {null}
      </PersonHeaderLayout>,
    );
  const several = contacts(["a.m@gmail.example", "a.m@alacpartners.example"], ["+971 50 123 4567", "+971 4 000 0000"]);

  it("writes to the primary email in Uncava, reads the phone, and opens the rest for editing", async () => {
    const onWriteEmail = vi.fn();
    const onEditContacts = vi.fn();
    renderHeader(several, { onWriteEmail, onEditContacts });

    await userEvent.click(screen.getByRole("button", { name: "Write email to a.m@gmail.example" }));
    expect(onWriteEmail).toHaveBeenCalled();
    // Nothing leaves for the device's own mail or dialer.
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("+971 50 123 4567").closest("button")).toBeNull();
    expect(screen.queryByText("a.m@alacpartners.example")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "2 more contacts" }));
    await userEvent.click(screen.getByRole("button", { name: "Edit contact details" }));
    expect(onEditContacts).toHaveBeenCalledTimes(2);
  });

  it("offers to add a channel with nothing on record, to whoever may edit", async () => {
    const onEditContacts = vi.fn();
    const { unmount } = renderHeader(contacts([], ["+971 50 123 4567"]), { onEditContacts });

    await userEvent.click(screen.getByRole("button", { name: "Add email" }));
    expect(onEditContacts).toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Add phone" })).not.toBeInTheDocument();
    unmount();

    renderHeader(contacts([], []));
    expect(screen.queryByRole("button", { name: /Add email|Add phone|Edit contact details/ })).not.toBeInTheDocument();
  });

  it("reads the contacts as text for a reader, with the rest a press away on the Contact tab", async () => {
    const onShowContacts = vi.fn();
    renderHeader(several, { onShowContacts, emailNote: "Do not contact" });

    expect(screen.getByText("a.m@gmail.example")).toHaveAttribute("title", "Do not contact");
    expect(screen.queryByRole("button", { name: /Write email|Edit contact details/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "2 more contacts" }));
    expect(onShowContacts).toHaveBeenCalled();
  });

  it("names the employer beside its location, and opens it where the page can", async () => {
    const onOpen = vi.fn();
    const { unmount } = renderHeader(contacts([], []));
    expect(screen.getByText("ALAC Partners")).toBeInTheDocument();
    expect(screen.getByText("Dubai · United Arab Emirates")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "ALAC Partners" })).not.toBeInTheDocument();
    unmount();

    renderHeader(contacts([], []), { employer: { name: "ALAC Partners", logoUrl: null, onOpen } });
    await userEvent.click(screen.getByRole("button", { name: "ALAC Partners" }));
    expect(onOpen).toHaveBeenCalled();
  });
});
