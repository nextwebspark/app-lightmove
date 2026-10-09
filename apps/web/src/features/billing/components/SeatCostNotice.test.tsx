import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as billingApi from "../api/billingApi";
import { aCardBilling } from "../test/fixtures";
import { SeatCostNotice } from "./SeatCostNotice";

vi.mock("../api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/billingApi")>()),
  getBilling: vi.fn(),
}));

const renderNotice = (role: "ADMIN" | "MEMBER" | "CLIENT") =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SeatCostNotice role={role} />
    </QueryClientProvider>,
  );

/** A staff invitation takes a billed seat; a client representative never does. */
describe("SeatCostNotice", () => {
  beforeEach(() => {
    vi.mocked(billingApi.getBilling).mockReset().mockResolvedValue(aCardBilling({ interval: "ANNUAL", seatPriceFils: 39_900 }));
  });

  it("names a staff seat's price, billed yearly on a yearly plan", async () => {
    renderNotice("MEMBER");

    expect(await screen.findByText(/Adds AED 399 a month, billed yearly, before VAT/)).toBeInTheDocument();
  });

  it("says nothing for a client representative, and reads no billing for one", () => {
    renderNotice("CLIENT");

    expect(screen.queryByText(/Adds AED/)).not.toBeInTheDocument();
    expect(billingApi.getBilling).not.toHaveBeenCalled();
  });
});
