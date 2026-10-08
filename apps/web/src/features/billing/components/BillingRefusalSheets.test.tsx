import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as billingApi from "../api/billingApi";
import { aBilling, aCardBilling, aTrialBilling } from "../test/fixtures";
import { BillingRefusalSheets } from "./BillingRefusalSheets";

let refused: ((error: ApiRequestError) => void) | null = null;
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  onRequestRefused: (listener: (error: ApiRequestError) => void) => {
    refused = listener;
    return () => {
      refused = null;
    };
  },
}));

vi.mock("../api/billingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/billingApi")>()),
  getBilling: vi.fn(),
}));

vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn(),
}));

let currentUser: User = aUser();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));

const outOfCredits = () =>
  new ApiRequestError({
    code: "INSUFFICIENT_CREDITS",
    detail: "Your workspace is out of contact credits",
    status: 402,
    correlationId: "x",
    required: 5,
    available: 2,
  });

const trialEnded = () =>
  new ApiRequestError({
    code: "TRIAL_ENDED",
    detail: "Your workspace's trial has ended",
    status: 402,
    correlationId: "x",
    trialEndedAt: "2026-10-08T09:00:00Z",
  });

function renderSheets() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <BillingRefusalSheets />
    </QueryClientProvider>,
  );
}

const refuse = (error: ApiRequestError) => act(() => refused?.(error));

/** A 402 opens the out-of-credits sheet and a 429 for fair use the fair-use one, worded for whoever pressed. */
describe("BillingRefusalSheets", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    currentUser = aUser();
    vi.mocked(billingApi.getBilling).mockResolvedValue(aBilling());
    vi.mocked(workspaceApi.members).mockResolvedValue([
      {
        memberId: "m1",
        userId: "u9",
        fullName: "Yara Haddad",
        email: "yara@nextwebspark.com",
        title: null,
        avatarUrl: null,
        roles: ["ADMIN"],
        joinedAt: null,
      },
    ]);
  });

  it("opens nothing for any other refusal", () => {
    renderSheets();
    refuse(new ApiRequestError({ code: "RATE_LIMITED", detail: "", status: 429, correlationId: "x" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("takes an admin on a card from the sheet to Buy more credits", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aCardBilling());
    const user = userEvent.setup();
    renderSheets();
    refuse(outOfCredits());

    const buy = await screen.findByRole("button", { name: "Buy more credits" });
    expect(screen.getByRole("dialog", { name: "No contact credits left" })).toHaveTextContent(
      "This find needs 5 credits and this month's are used up. Nothing was spent. They reset on 1 Nov.",
    );
    await user.click(buy);

    expect(await screen.findByRole("dialog", { name: "Buy more credits" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "No contact credits left" })).not.toBeInTheDocument();
  });

  it("sends an invoiced admin to Uncava", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(
      aBilling({ status: "INVOICED", paymentMethod: { kind: "INVOICED", brand: null, last4: null } }),
    );
    renderSheets();
    refuse(outOfCredits());

    expect(await screen.findByRole("link", { name: "Contact Uncava" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^mailto:billing@uncava\.com/),
    );
  });

  it("has a member ask an admin by name", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    renderSheets();
    refuse(outOfCredits());

    const ask = await screen.findByRole("link", { name: "Ask Yara to add more" });
    expect(ask).toHaveAttribute("href", expect.stringMatching(/^mailto:yara@nextwebspark\.com/));
    expect(screen.getByRole("dialog")).toHaveTextContent("Only an admin can add more — Yara Haddad.");
    expect(screen.queryByRole("button", { name: "Buy more credits" })).not.toBeInTheDocument();
  });

  it("takes an admin from an ended trial's sheet to the plans", async () => {
    vi.mocked(billingApi.getBilling).mockResolvedValue(aTrialBilling("2026-10-08T09:00:00Z"));
    const user = userEvent.setup();
    renderSheets();
    refuse(trialEnded());

    const choose = await screen.findByRole("button", { name: "Choose a plan" });
    expect(screen.getByRole("dialog", { name: "Your trial has ended" })).toHaveTextContent(
      "Your trial ended on 8 Oct 2026. Everything your team mapped is still here",
    );
    await user.click(choose);

    expect(await screen.findByRole("dialog", { name: "Plans" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Your trial has ended" })).not.toBeInTheDocument();
  });

  it("has a member ask an admin to choose a plan once the trial has ended", async () => {
    currentUser = aUser({ workspace: aWorkspace({ roles: ["MEMBER"] }) });
    vi.mocked(billingApi.getBilling).mockResolvedValue(aTrialBilling("2026-10-08T09:00:00Z"));
    renderSheets();
    refuse(trialEnded());

    expect(await screen.findByRole("link", { name: "Ask Yara to choose a plan" })).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toHaveTextContent("once an admin chooses a plan — Yara Haddad.");
  });

  it("opens the fair-use sheet for the use that reached its ceiling", async () => {
    renderSheets();
    refuse(
      new ApiRequestError({
        code: "FAIR_USE_REACHED",
        detail: "",
        status: 429,
        correlationId: "x",
        kind: "PEOPLE_SEARCH_PAGE",
        resetsAt: "2026-11-01T00:00:00Z",
      }),
    );

    const sheet = await screen.findByRole("dialog", { name: "You've reached this month's fair use" });
    expect(sheet).toHaveTextContent("People Search is part of your plan");
    expect(sheet).toHaveTextContent("until 1 Nov. Pages you already have stay open.");
    expect(screen.getByRole("link", { name: "Talk to us" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Not now" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
