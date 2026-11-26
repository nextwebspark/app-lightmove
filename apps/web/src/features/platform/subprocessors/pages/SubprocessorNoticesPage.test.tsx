import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as subprocessorApi from "../api/subprocessorApi";
import type { SubprocessorNotice } from "../api/types";
import SubprocessorNoticePage from "./SubprocessorNoticePage";
import SubprocessorNoticesPage from "./SubprocessorNoticesPage";

vi.mock("../api/subprocessorApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/subprocessorApi")>()),
  listNotices: vi.fn(),
  publishNotice: vi.fn(),
  listObjections: vi.fn(),
}));

const notice = (overrides: Partial<SubprocessorNotice> = {}): SubprocessorNotice => ({
  id: "sp1",
  provider: "Acme Mail",
  receives: "Account email addresses",
  purpose: "Email delivery in the EU",
  region: "European Union",
  effectiveOn: "2027-01-02",
  listUrl: "https://uncava.com/legal/subprocessors",
  inEffect: false,
  publishedAt: "2026-11-25T09:00:00Z",
  publishedBy: "Noor Rahman",
  emailed: 142,
  owed: 3,
  objections: 1,
  ...overrides,
});

const MAPS = notice({
  id: "sp3",
  provider: "Northwind Maps",
  receives: "Place names typed into a location search, never a person",
  purpose: "Maps and place search",
  effectiveOn: "2026-10-01",
  inEffect: true,
  owed: 0,
  objections: 0,
});

const refusal = (code: string, status: number, fieldErrors: Record<string, string> = {}) =>
  new ApiRequestError({ code, detail: "", status, correlationId: "c", fieldErrors });

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/subprocessors" element={<SubprocessorNoticesPage />} />
            <Route path="/platform/subprocessors/:noticeId" element={<SubprocessorNoticePage />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("Sub-processor notices", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-26T10:00:00Z"));
    vi.mocked(subprocessorApi.listNotices).mockResolvedValue([notice(), MAPS]);
  });

  afterEach(() => vi.useRealTimers());

  it("lists each notice with what it receives, when, who was emailed and its objections", async () => {
    renderAt("/platform/subprocessors");

    const table = await screen.findByRole("table", { name: "Sub-processor notices" });
    const [, acme, maps] = within(table).getAllByRole("row");
    expect(within(acme).getByText("Acme Mail")).toBeInTheDocument();
    expect(within(acme).getByText("Account email addresses")).toBeInTheDocument();
    expect(within(acme).getByText("142 admins")).toBeInTheDocument();
    expect(within(acme).getByText("3 owed, retrying")).toBeInTheDocument();
    expect(within(acme).getByText("1 objection")).toBeInTheDocument();
    expect(within(maps).getByText("In effect")).toBeInTheDocument();
    expect(within(maps).getByText("None")).toBeInTheDocument();
  });

  it("holds Publish while a field is empty, and names a date too soon and a link that is not https", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderAt("/platform/subprocessors");
    await user.click(await screen.findByRole("button", { name: "Publish a change" }));
    const dialog = await screen.findByRole("dialog", { name: "Publish a sub-processor change" });

    expect(within(dialog).getByRole("button", { name: "Publish" })).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox", { name: "Provider" }), "Fabrikam Search");
    await user.type(within(dialog).getByRole("textbox", { name: "Region" }), "European Union");
    await user.type(within(dialog).getByRole("textbox", { name: "What it receives" }), "Names and job titles");
    await user.type(within(dialog).getByRole("textbox", { name: "Purpose" }), "Searching across profiles");
    fireEvent.change(within(dialog).getByLabelText("Takes effect"), { target: { value: "2026-12-10" } });
    await user.type(within(dialog).getByRole("textbox", { name: /Link to the published list/ }), "http://uncava.com");
    await user.click(within(dialog).getByRole("button", { name: "Publish" }));

    expect(await within(dialog).findByText("Takes effect must be at least 30 days from today.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter a link that starts with https://")).toBeInTheDocument();
    expect(subprocessorApi.publishNotice).not.toHaveBeenCalled();
  });

  it("publishes a change 30 days ahead and says every admin is being emailed", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(subprocessorApi.publishNotice).mockResolvedValue(notice({ id: "sp9", provider: "Fabrikam Search" }));
    renderAt("/platform/subprocessors");
    await user.click(await screen.findByRole("button", { name: "Publish a change" }));
    const dialog = await screen.findByRole("dialog", { name: "Publish a sub-processor change" });

    await user.type(within(dialog).getByRole("textbox", { name: "Provider" }), " Fabrikam Search ");
    await user.type(within(dialog).getByRole("textbox", { name: "Region" }), "European Union");
    await user.type(within(dialog).getByRole("textbox", { name: "What it receives" }), "Names and job titles");
    await user.type(within(dialog).getByRole("textbox", { name: "Purpose" }), "Searching across profiles");
    fireEvent.change(within(dialog).getByLabelText("Takes effect"), { target: { value: "2026-12-26" } });
    await user.type(
      within(dialog).getByRole("textbox", { name: /Link to the published list/ }),
      "https://uncava.com/legal/subprocessors",
    );
    await user.click(within(dialog).getByRole("button", { name: "Publish" }));

    await waitFor(() =>
      expect(subprocessorApi.publishNotice).toHaveBeenCalledWith({
        provider: "Fabrikam Search",
        region: "European Union",
        receives: "Names and job titles",
        purpose: "Searching across profiles",
        effectiveOn: "2026-12-26",
        listUrl: "https://uncava.com/legal/subprocessors",
      }),
    );
    expect(await screen.findByText("Published. Every workspace admin is being emailed")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("puts the server's refusal under its field", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(subprocessorApi.publishNotice).mockRejectedValue(
      refusal("VALIDATION_FAILED", 400, { effectiveOn: "Takes effect must be at least 30 days from today." }),
    );
    renderAt("/platform/subprocessors");
    await user.click(await screen.findByRole("button", { name: "Publish a change" }));
    const dialog = await screen.findByRole("dialog", { name: "Publish a sub-processor change" });
    for (const [name, value] of [
      ["Provider", "Fabrikam"],
      ["Region", "EU"],
      ["What it receives", "Names"],
      ["Purpose", "Search"],
    ]) {
      await user.type(within(dialog).getByRole("textbox", { name }), value);
    }
    fireEvent.change(within(dialog).getByLabelText("Takes effect"), { target: { value: "2026-12-30" } });
    await user.type(within(dialog).getByRole("textbox", { name: /Link to the published list/ }), "https://uncava.com/l");
    await user.click(within(dialog).getByRole("button", { name: "Publish" }));

    expect(await within(dialog).findByText("Takes effect must be at least 30 days from today.")).toBeInTheDocument();
  });

  it("opens a notice with its fields and every workspace's objection", async () => {
    vi.mocked(subprocessorApi.listObjections).mockResolvedValue([
      {
        workspaceId: "w1",
        workspaceName: "Gulf Talent Partners",
        objectedBy: "Huda Saeed",
        reason: "We need 60 days to tell our clients.",
        objectedAt: "2026-11-26T08:00:00Z",
      },
    ]);
    renderAt("/platform/subprocessors/sp1");

    expect(await screen.findByRole("heading", { name: "Acme Mail" })).toBeInTheDocument();
    expect(screen.getByText("142 workspace admins · 3 owed, retrying")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "https://uncava.com/legal/subprocessors" })).toBeInTheDocument();
    const objections = await screen.findByRole("table", { name: "Objections" });
    expect(within(objections).getByText("Gulf Talent Partners")).toBeInTheDocument();
    expect(within(objections).getByText("Huda Saeed")).toBeInTheDocument();
    expect(within(objections).getByText("We need 60 days to tell our clients.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Objections · 1" })).toBeInTheDocument();
  });

  it("says when nobody has objected", async () => {
    vi.mocked(subprocessorApi.listObjections).mockResolvedValue([]);
    renderAt("/platform/subprocessors/sp3");

    expect(await screen.findByText("No objections yet.")).toBeInTheDocument();
    expect(screen.getByText("In effect since")).toBeInTheDocument();
  });
});
