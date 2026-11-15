import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { copyText } from "../../../lib/clipboard";
import type { CandidateContacts, CandidatePhone } from "../../candidates/api/types";
import { ContactChannels } from "./ContactPanel";
import type { PhoneReach } from "./PhoneActions";

vi.mock("../../../lib/clipboard", () => ({ copyText: vi.fn() }));

const line = (patch: Partial<CandidatePhone>): CandidatePhone => ({
  number: "050 441 8836",
  kind: null,
  verified: false,
  status: null,
  source: "manual",
  foundAt: "2026-09-16T17:27:24Z",
  ...patch,
});

const mobile = line({ international: "+971 50 441 8836", countrySource: "location", lineType: "mobile" });
const landline = line({ number: "+971 2 667 1200", international: "+971 2 667 1200", countrySource: "given", lineType: "landline" });

const contactsOf = (phones: CandidatePhone[]): CandidateContacts => ({
  emails: [],
  phones,
  emailsLookedUpAt: null,
  phonesLookedUpAt: null,
  source: null,
});

const staff = (patch: Partial<PhoneReach> = {}): PhoneReach => ({
  personName: "Fatima Al Mazrouei",
  locationCountry: "United Arab Emirates",
  actionable: true,
  ...patch,
});

const renderChannels = (phones: CandidatePhone[], reach: PhoneReach) =>
  render(
    <ToastProvider>
      <ContactChannels contacts={contactsOf(phones)} reach={reach} />
    </ToastProvider>,
  );

describe("phone actions", () => {
  it("dials a local number with the calling code from their location, and opens the chat", () => {
    renderChannels([mobile], staff());

    const call = screen.getByRole("link", { name: "Call Fatima Al Mazrouei on 050 441 8836" });
    expect(call).toHaveAttribute("href", "tel:+971504418836");
    expect(call).toHaveAttribute("title", expect.stringContaining("+971 added from Fatima's location (United Arab Emirates)"));
    const chat = screen.getByRole("link", { name: "WhatsApp Fatima Al Mazrouei" });
    expect(chat).toHaveAttribute("href", "https://wa.me/971504418836");
    expect(chat).toHaveAttribute("target", "_blank");
    expect(screen.getByText("+971 from location")).toBeInTheDocument();
  });

  it("offers a landline the call alone", () => {
    renderChannels([landline], staff());

    expect(screen.getByRole("link", { name: /^Call Fatima/ })).toHaveAttribute("href", "tel:+97126671200");
    expect(screen.queryByRole("link", { name: /WhatsApp/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("img", { name: /country code/ })).not.toBeInTheDocument();
  });

  it("says what is missing where no calling code could be read", () => {
    renderChannels([line({ number: "0441 8836" })], staff());

    expect(screen.getByRole("img", { name: "Add the country code to open WhatsApp" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Save as/ })).not.toBeInTheDocument();
  });

  it("leaves the number as text on do not contact", () => {
    renderChannels([mobile], staff({ actionable: false, refusal: "Do not contact is set" }));

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Copy phone" })).not.toBeInTheDocument();
    expect(screen.getByText("050 441 8836")).toHaveAttribute("title", "Do not contact is set");
  });

  it("keeps the added calling code on Save as, and copies the number as written", async () => {
    vi.mocked(copyText).mockResolvedValue(true);
    const onSaveInternational = vi.fn();
    renderChannels([mobile], staff({ onSaveInternational }));

    await userEvent.click(screen.getByRole("button", { name: "Save as +971 50 441 8836" }));
    expect(onSaveInternational).toHaveBeenCalledWith(mobile);

    await userEvent.click(screen.getByRole("button", { name: "Copy phone" }));
    expect(copyText).toHaveBeenCalledWith("050 441 8836");
    expect(await screen.findByText("Phone copied")).toBeInTheDocument();
  });
});
