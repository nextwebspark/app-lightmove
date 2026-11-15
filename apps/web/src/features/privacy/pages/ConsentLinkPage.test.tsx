import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { formatInstantDate } from "../../../lib/datetime";
import * as privacyPageApi from "../api/privacyPageApi";
import type { PrivacyPage, PrivacyPageConsent } from "../api/privacyPageApi";
import PrivacyLinkPage from "./PrivacyLinkPage";

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: null, loading: false }),
}));

vi.mock("../api/privacyPageApi", async (importOriginal) => ({
  ...(await importOriginal<typeof privacyPageApi>()),
  getPrivacyPage: vi.fn(),
  answerConsents: vi.fn(),
}));

const KEEP: PrivacyPageConsent = {
  id: "keep-1",
  purpose: "keep_on_file",
  status: "requested",
  open: true,
  wording: "We would keep your details for 24 months, so we can think of you when a suitable role comes up.",
  validUntil: null,
  answeredAt: null,
  keepOnFileMonths: 24,
  roleTitle: null,
  clientName: null,
};

const UPDATES: PrivacyPageConsent = {
  ...KEEP,
  id: "updates-1",
  purpose: "marketing",
  wording: "Occasional emails from us about roles and the market. You can stop them at any time.",
  keepOnFileMonths: null,
};

const PAGE: PrivacyPage = {
  expired: false,
  controllerName: "Meridian Search Partners FZ-LLC",
  privacyContactEmail: "privacy@meridian.example",
  noticeUrl: "https://meridian.example/privacy",
  lawfulBasis: "legitimate_interest",
  source: "extension",
  heldSince: "2026-04-14T10:12:00Z",
  categories: ["name_and_role"],
  expiresAt: "2026-10-22T10:00:00Z",
  purpose: "consent",
  consents: [KEEP, UPDATES],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/privacy/the-token"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/privacy/:token" element={<PrivacyLinkPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("a consent request's page", () => {
  beforeEach(() => vi.clearAllMocks());

  it("asks each purpose with nothing selected, and sends the answers given", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage)
      .mockResolvedValueOnce(PAGE)
      .mockResolvedValue({
        ...PAGE,
        consents: [
          { ...KEEP, status: "given", open: false, validUntil: "2028-09-22T10:00:00Z" },
          { ...UPDATES, status: "declined", open: false },
        ],
      });
    vi.mocked(privacyPageApi.answerConsents).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "Meridian Search Partners FZ-LLC asks for your permission" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Consent request")).toBeInTheDocument();
    const keep = within(screen.getByRole("group", { name: "Keep your details on file for future roles" }));
    expect(keep.getByText(KEEP.wording!)).toBeInTheDocument();
    expect(screen.queryAllByRole("button", { pressed: true })).toHaveLength(0);
    const send = screen.getByRole("button", { name: "Send my answers" });
    expect(send).toBeDisabled();

    await user.click(keep.getByRole("button", { name: "Yes" }));
    await user.click(
      within(screen.getByRole("group", { name: "Career and market updates" })).getByRole("button", { name: "No" }),
    );
    await user.click(send);

    await waitFor(() =>
      expect(privacyPageApi.answerConsents).toHaveBeenCalledWith("the-token", [
        { consentId: "keep-1", answer: "given" },
        { consentId: "updates-1", answer: "declined" },
      ]),
    );
    expect(await screen.findByRole("heading", { name: "Thank you, your answers are saved" })).toBeInTheDocument();
    expect(screen.getByText(/We've emailed you a link to change your mind/)).toBeInTheDocument();
    const answers = within(screen.getByRole("list", { name: "Your answers" }));
    expect(await answers.findByText(`Yes, until ${formatInstantDate("2028-09-22T10:00:00Z")}`)).toBeInTheDocument();
    expect(answers.getByText("No")).toBeInTheDocument();
  });

  it("asks a renewal once, about keeping the details for another period", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockResolvedValue({
      ...PAGE,
      consents: [
        {
          ...KEEP,
          status: "given",
          validUntil: "2026-10-14T10:00:00Z",
          answeredAt: "2024-10-14T10:00:00Z",
          wording: "You agreed on 14 Oct 2024 that we could keep your details on file for future roles. If you say yes, we keep them until 14 Oct 2028.",
        },
      ],
    });
    renderPage();

    expect(
      await screen.findByRole("heading", {
        name: `Your consent to keep your details ends on ${formatInstantDate("2026-10-14T10:00:00Z")}`,
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Keep your details on file for another 24 months" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send my answer" })).toBeDisabled();
  });

  it("says a request past its 30 days has closed and changed nothing", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockResolvedValue({ ...PAGE, expired: true, consents: [] });
    renderPage();

    expect(await screen.findByRole("heading", { name: "This request has closed" })).toBeInTheDocument();
    expect(screen.getByText(/Nothing changed: you didn't agree to anything/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "privacy@meridian.example" })).toBeInTheDocument();
  });
});

describe("a manage link's page", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists what is in force and withdraws one only once confirmed", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockResolvedValue({
      ...PAGE,
      purpose: "manage",
      expiresAt: "2028-09-22T10:00:00Z",
      consents: [
        { ...KEEP, status: "given", open: false, answeredAt: "2026-09-22T10:00:00Z", validUntil: "2028-09-22T10:00:00Z" },
      ],
    });
    vi.mocked(privacyPageApi.answerConsents).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "Your consents" })).toBeInTheDocument();
    expect(
      screen.getByText(
        `Given on ${formatInstantDate("2026-09-22T10:00:00Z")} · until ${formatInstantDate("2028-09-22T10:00:00Z")}`,
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Withdraw" }));
    expect(
      screen.getByRole("heading", { name: "Withdraw your consent to keep your details on file?" }),
    ).toBeInTheDocument();
    expect(privacyPageApi.answerConsents).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Withdraw consent" }));

    expect(await screen.findByRole("heading", { name: "Your consent is withdrawn" })).toBeInTheDocument();
    expect(privacyPageApi.answerConsents).toHaveBeenCalledWith("the-token", [
      { consentId: "keep-1", answer: "withdrawn" },
    ]);
  });
});
