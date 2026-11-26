import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as subprocessorNoticesApi from "../api/subprocessorNoticesApi";
import type { SubprocessorChange } from "../api/subprocessorNoticesApi";
import { SubprocessorChangesCard } from "./SubprocessorChangesCard";

vi.mock("../api/subprocessorNoticesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/subprocessorNoticesApi")>()),
  getSubprocessorChanges: vi.fn(),
  objectToSubprocessorChange: vi.fn(),
}));

const change = (overrides: Partial<SubprocessorChange> = {}): SubprocessorChange => ({
  id: "sp1",
  provider: "Acme Mail",
  receives: "Account email addresses, for email delivery in the EU",
  purpose: "Email delivery",
  region: "European Union",
  effectiveOn: "2027-01-02",
  listUrl: "https://uncava.com/legal/subprocessors",
  inEffect: false,
  objection: null,
  ...overrides,
});

const CONTOSO = change({
  id: "sp2",
  provider: "Contoso Transcribe",
  receives: "Call recordings, to write their transcripts",
  effectiveOn: "2027-01-15",
  objection: { reason: "We need to see where it stores them.", objectedAt: "2026-11-27T09:00:00Z", objectedBy: "Huda Saeed" },
});

const NORTHWIND = change({
  id: "sp3",
  provider: "Northwind Maps",
  receives: "Place names typed into a location search, never a person",
  effectiveOn: "2026-10-01",
  inEffect: true,
});

const renderCard = (path = "/settings/data") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SubprocessorChangesCard />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("SubprocessorChangesCard", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(subprocessorNoticesApi.getSubprocessorChanges).mockResolvedValue([change(), CONTOSO, NORTHWIND]);
  });

  it("offers Object… until a change takes effect, and says when the workspace objected", async () => {
    renderCard();

    const acme = await screen.findByRole("listitem", { name: "Acme Mail" });
    expect(within(acme).getByText("Takes effect 02 Jan 2027")).toBeInTheDocument();
    expect(within(acme).getByRole("button", { name: "Object to Acme Mail" })).toBeInTheDocument();

    const contoso = screen.getByRole("listitem", { name: "Contoso Transcribe" });
    expect(within(contoso).getByText(/You objected on 27 Nov/)).toBeInTheDocument();
    expect(within(contoso).getByRole("button", { name: "Change the objection to Contoso Transcribe" })).toBeInTheDocument();

    const northwind = screen.getByRole("listitem", { name: "Northwind Maps" });
    expect(within(northwind).getByText("In effect since 01 Oct 2026")).toBeInTheDocument();
    expect(within(northwind).queryByRole("button")).not.toBeInTheDocument();
  });

  it("says when nothing has been announced", async () => {
    vi.mocked(subprocessorNoticesApi.getSubprocessorChanges).mockResolvedValue([]);
    renderCard();

    expect(await screen.findByText("No changes announced.")).toBeInTheDocument();
  });

  it("sends an objection with its reason, held while the reason is blank", async () => {
    const user = userEvent.setup();
    vi.mocked(subprocessorNoticesApi.objectToSubprocessorChange).mockResolvedValue(
      change({ objection: { reason: "Our clients vet every provider.", objectedAt: "2026-11-27T09:00:00Z", objectedBy: "Huda Saeed" } }),
    );
    renderCard();

    await user.click(await screen.findByRole("button", { name: "Object to Acme Mail" }));
    const dialog = await screen.findByRole("dialog", { name: "Object to Acme Mail" });
    expect(within(dialog).getByText(/It takes effect on 02 Jan 2027\./)).toBeInTheDocument();
    const send = within(dialog).getByRole("button", { name: "Send objection" });
    expect(send).toBeDisabled();

    await user.type(within(dialog).getByRole("textbox", { name: /Why do you object\?/ }), "   ");
    expect(send).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox", { name: /Why do you object\?/ }), "Our clients vet every provider.");
    expect(within(dialog).getByText("34 / 2000")).toBeInTheDocument();
    await user.click(send);

    await waitFor(() =>
      expect(subprocessorNoticesApi.objectToSubprocessorChange).toHaveBeenCalledWith("sp1", "Our clients vet every provider."),
    );
    expect(await screen.findByText("Objection sent. Uncava will contact you about Acme Mail")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const acme = await screen.findByRole("listitem", { name: "Acme Mail" });
    expect(await within(acme).findByText(/You objected on 27 Nov/)).toBeInTheDocument();
  });

  it("opens the objection the email's link names, with the reason already given to change", async () => {
    renderCard("/settings/data?object=sp2");

    const dialog = await screen.findByRole("dialog", { name: "Object to Contoso Transcribe" });
    expect(within(dialog).getByRole("textbox", { name: /Why do you object\?/ })).toHaveValue(
      "We need to see where it stores them.",
    );
  });

  it("says a change already in effect can no longer be objected to", async () => {
    renderCard("/settings/data?object=sp3");

    const dialog = await screen.findByRole("dialog", { name: "Object to Northwind Maps" });
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "This change has already taken effect, so it can no longer be objected to.",
    );
    expect(within(dialog).getByText(/It took effect on 01 Oct 2026\./)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Send objection" })).toBeDisabled();
  });

  it("turns to the closed state when the server says the change took effect meanwhile", async () => {
    const user = userEvent.setup();
    vi.mocked(subprocessorNoticesApi.objectToSubprocessorChange).mockRejectedValue(
      new ApiRequestError({ code: "SUBPROCESSOR_NOTICE_CLOSED", detail: "", status: 409, correlationId: "c", fieldErrors: {} }),
    );
    renderCard("/settings/data?object=sp1");

    const dialog = await screen.findByRole("dialog", { name: "Object to Acme Mail" });
    await user.type(within(dialog).getByRole("textbox", { name: /Why do you object\?/ }), "Too late?");
    await user.click(within(dialog).getByRole("button", { name: "Send objection" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "This change has already taken effect, so it can no longer be objected to.",
    );
  });
});
