import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import * as privacyPageApi from "../api/privacyPageApi";
import type { PrivacyPage } from "../api/privacyPageApi";
import PrivacyLinkPage from "./PrivacyLinkPage";

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: null, loading: false }),
}));

vi.mock("../api/privacyPageApi", async (importOriginal) => ({
  ...(await importOriginal<typeof privacyPageApi>()),
  getPrivacyPage: vi.fn(),
  objectFromPrivacyPage: vi.fn(),
  requestFromPrivacyPage: vi.fn(),
}));

const PAGE: PrivacyPage = {
  expired: false,
  controllerName: "Meridian Search Partners FZ-LLC",
  privacyContactEmail: "privacy@meridian.example",
  noticeUrl: "https://meridian.example/privacy",
  lawfulBasis: "legitimate_interest",
  source: "extension",
  heldSince: "2026-04-14T10:12:00Z",
  categories: ["name_and_role", "career", "work_email", "linkedin"],
  expiresAt: "2026-10-22T10:00:00Z",
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

describe("PrivacyLinkPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("names the firm and the kinds of data held, then sends an objection once confirmed", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockResolvedValue(PAGE);
    vi.mocked(privacyPageApi.objectFromPrivacyPage).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "What we hold about you, and your choices" })).toBeInTheDocument();
    const held = screen.getByRole("list", { name: "What we hold" });
    expect(within(held).getByText("Career history")).toBeInTheDocument();
    expect(within(held).getByText("LinkedIn profile")).toBeInTheDocument();
    expect(screen.getByText("Your public professional profile.")).toBeInTheDocument();
    expect(screen.getAllByText(/Meridian Search Partners FZ-LLC/).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: /Don't contact me again/ }));
    expect(screen.getByRole("heading", { name: "Stop all contact from us?" })).toBeInTheDocument();
    expect(privacyPageApi.objectFromPrivacyPage).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Don't contact me again" }));

    expect(await screen.findByRole("heading", { name: "You won't hear from us again" })).toBeInTheDocument();
    expect(privacyPageApi.objectFromPrivacyPage).toHaveBeenCalledWith("the-token");
  });

  it("asks for a deletion as a request the firm answers", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockResolvedValue(PAGE);
    vi.mocked(privacyPageApi.requestFromPrivacyPage).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /Delete my data/ }));
    await user.click(screen.getByRole("button", { name: "Send the request" }));

    await waitFor(() => expect(privacyPageApi.requestFromPrivacyPage).toHaveBeenCalledWith("the-token", "deletion"));
    expect(await screen.findByText(/has your request to delete your data/)).toBeInTheDocument();
  });

  it("names whom to write to once the link has expired, and nothing about the person", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockResolvedValue({
      ...PAGE,
      expired: true,
      lawfulBasis: null,
      source: null,
      heldSince: null,
      categories: [],
    });
    renderPage();

    expect(await screen.findByRole("heading", { name: "This link has expired" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "privacy@meridian.example" })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "What we hold" })).not.toBeInTheDocument();
  });

  it("says an unknown link isn't valid, naming nobody", async () => {
    vi.mocked(privacyPageApi.getPrivacyPage).mockRejectedValueOnce(
      new ApiRequestError({ code: "NOT_FOUND", detail: "Not found", status: 404, correlationId: "c1" }),
    );
    renderPage();

    expect(await screen.findByRole("heading", { name: "This link isn't valid" })).toBeInTheDocument();
    expect(screen.queryByText(/Meridian/)).not.toBeInTheDocument();
  });
});
