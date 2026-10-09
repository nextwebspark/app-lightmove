import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as billingApi from "../api/billingApi";
import { aBilling, aTrialBilling, someCredits } from "../test/fixtures";
import { CreditChip } from "./CreditChip";

vi.mock("../api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/billingApi")>()),
  getBilling: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

let client: QueryClient;

function renderChip() {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <CreditChip />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** The topbar chip follows the level the billing read reports, and opens the billing page. */
describe("CreditChip", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
  });

  it("is absent below 80% used", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    renderChip();

    await vi.waitFor(() => expect(client.getQueryState(billingApi.BILLING_KEY)?.status).toBe("success"));
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("names what is left from 80%, and opens Settings → Billing", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aBilling({ credits: someCredits({ level: "NINETY", left: 60, usedPercent: 92 }) }),
    );
    renderChip();

    const chip = await screen.findByRole("link", { name: "60 contact credits left" });
    expect(chip).toHaveAttribute("href", "/settings/billing");
  });

  it("says out once the credits are used up", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling({ credits: someCredits({ level: "OUT", left: 0 }) }));
    renderChip();

    expect(await screen.findByRole("link", { name: "Out of contact credits" })).toBeInTheDocument();
  });

  it("counts a trial's days down", async () => {
    const endsAt = new Date(Date.now() + 9 * 86_400_000 - 3_600_000).toISOString();
    vi.mocked(billingApi.getBilling).mockResolvedValue(aTrialBilling(endsAt));
    renderChip();

    const chip = await screen.findByRole("link", { name: "Trial · 9 days left" });
    // A short label for phones beside the full one; CSS shows one per breakpoint.
    expect(chip).toHaveTextContent("9d");
  });

  it("keeps the days left in front when a trial's credits run low, naming them too", async () => {
    const endsAt = new Date(Date.now() + 9 * 86_400_000 - 3_600_000).toISOString();
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aTrialBilling(endsAt, { credits: someCredits({ level: "NINETY", left: 4 }) }),
    );
    renderChip();

    expect(
      await screen.findByRole("link", { name: "Trial · 9 days left · 4 contact credits left" }),
    ).toBeInTheDocument();
  });

  it("never asks for a pure client, who is answered 404", () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) });
    renderChip();

    expect(billingApi.getBilling).not.toHaveBeenCalled();
  });
});
