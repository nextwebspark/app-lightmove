import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as bookingApi from "../api/bookingApi";
import BookingPage from "./BookingPage";

vi.mock("../api/bookingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/bookingApi")>()),
  getBookingPage: vi.fn(),
  getBookingSlots: vi.fn(),
  bookOnPage: vi.fn(),
}));
// The real scheduler is a web component that calls Nylas; the page's job is to hand it the right page.
vi.mock("../components/BookingScheduler", () => ({
  default: ({ configurationId, schedulerApiUrl }: { configurationId: string; schedulerApiUrl: string }) => (
    <div data-testid="scheduler">
      {configurationId} @ {schedulerApiUrl}
    </div>
  ),
}));

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/book/yara-haddad"]}>
        <Routes>
          <Route path="/book/:slug" element={<BookingPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("BookingPage", () => {
  beforeEach(() => vi.resetAllMocks());

  it("opens the consultant's scheduler page under their name", async () => {
    vi.mocked(bookingApi.getBookingPage).mockResolvedValue({
      kind: "NYLAS",
      configurationId: "page-1",
      schedulerApiUrl: "https://api.us.nylas.com",
      consultantName: "Yara Haddad",
      minutes: 30,
    });
    renderPage();

    expect(await screen.findByRole("heading", { name: "Book a call with Yara Haddad" })).toBeInTheDocument();
    expect(await screen.findByTestId("scheduler")).toHaveTextContent("page-1 @ https://api.us.nylas.com");
    expect(bookingApi.getBookingPage).toHaveBeenCalledWith("yara-haddad", expect.anything());
  });

  it("offers a direct mailbox's free times on Uncava's own page and books the one picked", async () => {
    vi.mocked(bookingApi.getBookingPage).mockResolvedValue({
      kind: "DIRECT",
      configurationId: null,
      schedulerApiUrl: null,
      consultantName: "Yara Haddad",
      minutes: 30,
    });
    vi.mocked(bookingApi.getBookingSlots).mockResolvedValue({
      timeZone: "Asia/Dubai",
      minutes: 30,
      earliestDate: "2026-10-05",
      latestDate: "2027-04-05",
      previousFrom: null,
      days: [{ date: "2026-10-05", starts: ["2026-10-05T06:00:00Z", "2026-10-05T06:30:00Z"] }],
    });
    vi.mocked(bookingApi.bookOnPage).mockResolvedValue(undefined);
    renderPage();

    expect(await screen.findByRole("heading", { name: "Book a call with Yara Haddad" })).toBeInTheDocument();
    expect(screen.queryByTestId("scheduler")).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "10:30" }));
    await userEvent.type(screen.getByRole("textbox", { name: "Your name" }), "Priya Raman");
    await userEvent.type(screen.getByRole("textbox", { name: "Your email" }), "priya@target.example");
    await userEvent.click(screen.getByRole("button", { name: "Book 10:30" }));

    expect(await screen.findByRole("status")).toHaveTextContent("You're booked");
    expect(bookingApi.bookOnPage).toHaveBeenCalledWith("yara-haddad", {
      startsAt: "2026-10-05T06:30:00Z",
      name: "Priya Raman",
      email: "priya@target.example",
    });
  });

  it("says a link that leads nowhere is no longer available", async () => {
    vi.mocked(bookingApi.getBookingPage).mockRejectedValue(new Error("not found"));
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("This booking link is no longer available.");
    expect(screen.queryByTestId("scheduler")).not.toBeInTheDocument();
  });
});
