import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as billingApi from "../api/billingApi";
import { aBilling, someCredits } from "../test/fixtures";
import { SettingsBillingPage } from "./SettingsBillingPage";

vi.mock("../api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/billingApi")>()),
  getBilling: vi.fn(),
  getBillingUsage: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

function renderPage() {
  render(
    <MemoryRouter>
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

  it("shows no banner and no buy button below 80% on a card", async () => {
    renderPage();
    await screen.findByRole("region", { name: "Plan" });

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Buy more credits" })).not.toBeInTheDocument();
  });

  it("warns at 80% and offers an admin Buy more, disabled until checkout exists", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aBilling({ credits: someCredits({ level: "EIGHTY", usedPercent: 82, left: 135 }) }),
    );
    renderPage();

    expect(await screen.findByRole("status")).toHaveTextContent("82% of this month's contact credits used");
    for (const buy of screen.getAllByRole("button", { name: "Buy more credits" })) expect(buy).toBeDisabled();
  });

  it("offers a member neither seats nor credits", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling({ credits: someCredits({ level: "OUT", left: 0 }) }));
    renderPage();

    expect(await screen.findByRole("status")).toHaveTextContent("an admin adds more");
    expect(screen.queryByRole("button", { name: "Buy more credits" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Add seats" })).not.toBeInTheDocument();
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
      aBilling({ status: "INVOICED", paymentMethod: { kind: "INVOICED", brand: null, last4: null } }),
    );
    renderPage();

    expect(await screen.findByRole("status")).toHaveTextContent("Paid by invoice");
    expect(screen.getByRole("region", { name: "Payment and invoices" })).toHaveTextContent("Paid by bank transfer");
  });
});
