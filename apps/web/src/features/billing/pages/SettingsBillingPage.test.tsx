import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as billingApi from "../api/billingApi";
import * as checkoutReturn from "../lib/checkoutReturn";
import { aBilling, aCardBilling, anInvoicedBilling, aTrialBilling, someCredits } from "../test/fixtures";
import { SettingsBillingPage } from "./SettingsBillingPage";

vi.mock("../api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/billingApi")>()),
  getBilling: vi.fn(),
  getBillingUsage: vi.fn(),
  getBillingCard: vi.fn(),
  startSubscriptionCheckout: vi.fn(),
  startCreditsCheckout: vi.fn(),
  openPortal: vi.fn(),
}));

vi.mock("../lib/checkoutReturn", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../lib/checkoutReturn")>()),
  goToStripe: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

function renderPage(path = "/settings/billing") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <SettingsBillingPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** Settings → Billing: the plan, the one meter, who spent it, and how the workspace pays. */
describe("SettingsBillingPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    vi.mocked(billingApi.getBillingCard).mockResolvedValue({ brand: null, last4: null });
    vi.mocked(billingApi.getBillingUsage).mockResolvedValue({
      periodStart: "2026-10-01T00:00:00Z",
      periodEnd: "2026-11-01T00:00:00Z",
      members: [
        { userId: "u1", name: "Alok Kumar", emailsFound: 12, phonesFound: 4, creditsSpent: 32 },
        { userId: "u2", name: "Sara Al-Mansour", emailsFound: 3, phonesFound: 0, creditsSpent: 3 },
      ],
    });
  });

  it("reads the plan, the month's credits and the price of each find", async () => {
    renderPage();

    const plan = await screen.findByRole("region", { name: "Plan" });
    expect(plan).toHaveTextContent("Pro");
    expect(plan).toHaveTextContent("5 staff seats · AED 499 per seat · billed monthly");
    expect(plan).toHaveTextContent("AED 2,495");
    expect(plan).toHaveTextContent("750 contact credits a month");

    const meter = screen.getByRole("region", { name: "Contact credits" });
    expect(meter).toHaveTextContent("Email found 1 · Phone found 5");
    expect(meter).toHaveTextContent(/412\s*of 750 left this month/);
    expect(meter).toHaveTextContent("Resets to 750 on 1 Nov");
  });

  it("shows a trial's days and credits, and takes its admin to the plans", async () => {
    const endsAt = new Date(Date.now() + 9 * 86_400_000 - 3_600_000).toISOString();
    vi.mocked(billingApi.getBilling).mockResolvedValue(aTrialBilling(endsAt));
    const user = userEvent.setup();
    renderPage();

    const plan = await screen.findByRole("region", { name: "Plan" });
    expect(plan).toHaveTextContent("Trial");
    expect(plan).toHaveTextContent("1 staff seat · free while the trial lasts");
    expect(plan).toHaveTextContent("50 contact credits for the trial");
    expect(plan).not.toHaveTextContent("AED");
    expect(screen.getByRole("region", { name: "Contact credits" })).toHaveTextContent(/50\s*of 50 left in your trial/);
    expect(screen.getByRole("status")).toHaveTextContent("Pro trial · 9 days left");
    expect(screen.getByRole("region", { name: "Payment and invoices" })).toHaveTextContent("No card yet");

    await user.click(within(screen.getByRole("status")).getByRole("button", { name: "Choose a plan" }));

    expect(await screen.findByRole("dialog", { name: "Plans" })).toBeInTheDocument();
  });

  it("says an ended trial's credits lapsed, with no free seats and no Add seats", async () => {
    const endedAt = new Date(Date.now() - 86_400_000).toISOString();
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aTrialBilling(endedAt, { credits: someCredits({ monthly: 0, left: 0, usedPercent: 0, resetsAt: endedAt }) }),
    );
    renderPage();

    const plan = await screen.findByRole("region", { name: "Plan" });
    expect(plan).toHaveTextContent("1 staff seat");
    expect(plan).not.toHaveTextContent("free while the trial lasts");
    expect(plan).toHaveTextContent("Trial ended");
    expect(within(plan).queryByRole("link", { name: "Add seats" })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Contact credits" })).toHaveTextContent(
      "Trial credits lapsed when the trial ended on",
    );
  });

  it("names the card Stripe charges, asked only where the workspace pays by card", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aCardBilling());
    vi.mocked(billingApi.getBillingCard).mockResolvedValue({ brand: "american_express", last4: "0005" });
    renderPage();

    expect(await screen.findByText("Amex •••• 0005")).toBeInTheDocument();
  });

  it("says Paid by card where Stripe cannot name the card", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aCardBilling());
    renderPage();
    const payment = await screen.findByRole("region", { name: "Payment and invoices" });
    expect(payment).toHaveTextContent("Paid by card");
  });

  it("shows no banner and no buy button below 80% on a card", async () => {
    renderPage();
    await screen.findByRole("region", { name: "Plan" });

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Buy more credits" })).not.toBeInTheDocument();
  });

  it("warns at 80% and sends an admin on a card to Checkout for a pack, VAT shown", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aCardBilling({ credits: someCredits({ level: "EIGHTY", usedPercent: 82, left: 135 }) }),
    );
    vi.mocked(billingApi.startCreditsCheckout).mockResolvedValue({ url: "https://checkout.stripe.test/c" });
    const user = userEvent.setup();
    renderPage();

    expect((await screen.findByText(/82% of this month's contact credits used/)).closest('[role="status"]')).not.toBeNull();
    await user.click(screen.getAllByRole("button", { name: "Buy more credits" })[0]);
    const dialog = screen.getByRole("dialog", { name: "Buy more credits" });
    await user.click(within(dialog).getByRole("radio", { name: /500 credits/ }));
    expect(dialog).toHaveTextContent("AED 650 + 5% VAT");
    await user.click(within(dialog).getByRole("button", { name: "Pay AED 682.50" }));

    expect(billingApi.startCreditsCheckout).toHaveBeenCalledWith("contact-500");
    await waitFor(() => expect(checkoutReturn.goToStripe).toHaveBeenCalledWith("https://checkout.stripe.test/c"));
  });

  it("opens the plans and sends a Stripe subscriber's switch to the portal", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aCardBilling());
    vi.mocked(billingApi.openPortal).mockResolvedValue({ url: "https://billing.stripe.test/p" });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Change plan" }));
    const plans = screen.getByRole("dialog", { name: "Plans" });
    expect(within(plans).getByRole("button", { name: "Current plan" })).toBeDisabled();
    expect(within(plans).getByRole("link", { name: "Talk to us" })).toHaveAttribute("href", expect.stringMatching(/^mailto:/));
    await user.click(within(plans).getByRole("radio", { name: "Yearly · save 20%" }));
    await user.click(within(plans).getByRole("button", { name: "Switch to yearly" }));

    expect(billingApi.openPortal).toHaveBeenCalled();
    expect(billingApi.startSubscriptionCheckout).not.toHaveBeenCalled();
  });

  it("sends an admin with no plan to Checkout for the plan and period chosen", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aCardBilling({ plan: null, interval: null, status: null, paymentMethod: { kind: "NONE" } }),
    );
    vi.mocked(billingApi.startSubscriptionCheckout).mockResolvedValue({ url: "https://checkout.stripe.test/s" });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "See plans" }));
    await user.click(screen.getByRole("button", { name: "Choose Core" }));

    expect(billingApi.startSubscriptionCheckout).toHaveBeenCalledWith("CORE", "MONTHLY");
  });

  it("waits on Stripe after Checkout and shows the new credits without a reload", async () => {
    vi.mocked(billingApi.getBilling)
      .mockResolvedValueOnce(aCardBilling())
      .mockResolvedValue(aCardBilling({ credits: someCredits({ bought: 100, left: 512 }) }));
    renderPage("/settings/billing?checkout=credits");

    expect(await screen.findByText(/waiting for Stripe to confirm the credits/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("region", { name: "Contact credits" })).toHaveTextContent("512"), {
      timeout: 5_000,
    });
    expect(screen.queryByText(/waiting for Stripe/)).not.toBeInTheDocument();
  });

  it("opens Invoices & card for an admin on a card", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aCardBilling());
    vi.mocked(billingApi.openPortal).mockResolvedValue({ url: "https://billing.stripe.test/p" });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Invoices & card" }));

    await waitFor(() => expect(checkoutReturn.goToStripe).toHaveBeenCalledWith("https://billing.stripe.test/p"));
  });

  it("offers an invoiced admin no buying, only Contact Uncava", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      anInvoicedBilling({ credits: someCredits({ level: "EIGHTY", usedPercent: 82, left: 135 }) }),
    );
    renderPage();

    await screen.findByRole("region", { name: "Plan" });
    expect(screen.queryByRole("button", { name: "Buy more credits" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Change plan" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Invoices & card" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Contact Uncava" }).length).toBeGreaterThan(0);
    expect(billingApi.getBillingCard).not.toHaveBeenCalled();
  });

  it("offers a member neither seats nor credits", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    vi.mocked(billingApi.getBilling).mockResolvedValue(aCardBilling({ credits: someCredits({ level: "OUT", left: 0 }) }));
    renderPage();

    expect((await screen.findByText(/an admin adds more/)).closest('[role="status"]')).not.toBeNull();
    expect(screen.queryByRole("button", { name: "Buy more credits" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Add seats" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Change plan" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Invoices & card" })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Payment and invoices" })).toHaveTextContent("Paid by card");
    expect(billingApi.getBillingCard).not.toHaveBeenCalled();
  });

  it("lists this month's spend per member, the caller marked", async () => {
    renderPage();

    const used = await screen.findByRole("region", { name: "Used this month" });
    expect(await within(used).findByText("Alok Kumar (you)")).toBeInTheDocument();
    expect(within(used).getByText("32")).toBeInTheDocument();
    expect(within(used).getByText("Sara Al-Mansour")).toBeInTheDocument();
  });

  it("says an invoiced workspace pays by bank transfer", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aBilling({ status: "INVOICED", paymentMethod: { kind: "INVOICED" } }),
    );
    renderPage();

    expect((await screen.findByText(/Paid by invoice/)).closest('[role="status"]')).not.toBeNull();
    expect(screen.getByRole("region", { name: "Payment and invoices" })).toHaveTextContent("Paid by bank transfer");
  });
});
