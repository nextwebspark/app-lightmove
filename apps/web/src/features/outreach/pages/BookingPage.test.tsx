import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as bookingApi from "../api/bookingApi";
import BookingPage from "./BookingPage";

vi.mock("../api/bookingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/bookingApi")>()),
  getBookingPage: vi.fn(),
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
      configurationId: "page-1",
      schedulerApiUrl: "https://api.us.nylas.com",
      consultantName: "Yara Haddad",
    });
    renderPage();

    expect(await screen.findByRole("heading", { name: "Book a call with Yara Haddad" })).toBeInTheDocument();
    expect(await screen.findByTestId("scheduler")).toHaveTextContent("page-1 @ https://api.us.nylas.com");
    expect(bookingApi.getBookingPage).toHaveBeenCalledWith("yara-haddad", expect.anything());
  });

  it("says a link that leads nowhere is no longer available", async () => {
    vi.mocked(bookingApi.getBookingPage).mockRejectedValue(new Error("not found"));
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("This booking link is no longer available.");
    expect(screen.queryByTestId("scheduler")).not.toBeInTheDocument();
  });
});
