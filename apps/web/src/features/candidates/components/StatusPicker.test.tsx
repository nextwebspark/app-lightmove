import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { aSeededCatalog, withRetiredStatus } from "../../../test/fixtures/pipelineCatalog";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import { StatusPicker } from "./StatusPicker";

vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  pipelineCatalog: vi.fn(),
}));

const onChange = vi.fn();

function renderPicker(current: string, options: { currentLabel?: string; disabled?: boolean } = {}) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <StatusPicker aria-label="Status" value={current} onChange={onChange} {...options} />
    </QueryClientProvider>,
  );
}

async function openPicker() {
  await waitFor(() => expect(pipelineApi.pipelineCatalog).toHaveBeenCalled());
  await userEvent.click(screen.getByRole("button", { name: /^Status:/ }));
  const list = await screen.findByRole("listbox", { name: "Status" });
  await within(list).findByRole("group", { name: "Qualified" });
  return list;
}

const offered = (list: HTMLElement) => within(list).getAllByRole("option").map((option) => option.textContent);
const under = (list: HTMLElement, stage: string) =>
  within(within(list).getByRole("group", { name: stage })).getAllByRole("option").map((option) => option.textContent);

describe("StatusPicker", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(pipelineApi.pipelineCatalog).mockResolvedValue(withRetiredStatus(aSeededCatalog(), "researching"));
  });

  it("reads as the status held and opens the statuses under their stages, the held one marked", async () => {
    renderPicker("contacted");
    expect(screen.getByRole("button", { name: "Status: Contacted" })).toBeInTheDocument();

    const list = await openPicker();

    expect(within(list).getAllByRole("group").map((group) => group.getAttribute("aria-label"))).toEqual([
      "Identified",
      "Contacted",
      "Interested",
      "Qualified",
      "Shortlisted",
      "Declined",
    ]);
    expect(under(list, "Declined")).toEqual([
      "Not interested",
      "Withdrew",
      "Not a fit",
      "Rejected by client",
      "No response",
      "Off-limits",
      "Out of scope",
    ]);
    expect(within(list).getByRole("option", { name: "Contacted" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("combobox", { name: "Search statuses for Status" })).toHaveFocus();
  });

  it("narrows to the statuses a typed word names", async () => {
    renderPicker("identified");
    const list = await openPicker();

    await userEvent.keyboard("pack");

    expect(offered(list)).toEqual(["Interested – package gap"]);
  });

  it("shows a whole stage when its name is typed", async () => {
    renderPicker("identified");
    const list = await openPicker();

    await userEvent.keyboard("shortl");

    expect(offered(list)).toEqual(["Shortlisted", "Presented to client", "Client interview", "Offer"]);
  });

  it("never makes a status out of what was typed", async () => {
    renderPicker("identified");
    const list = await openPicker();

    await userEvent.keyboard("second interview{Enter}");

    expect(within(list).queryAllByRole("option")).toHaveLength(0);
    expect(screen.getByText("No status matches")).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("picks the status the arrow keys land on", async () => {
    renderPicker("identified");
    await openPicker();

    await userEvent.keyboard("{ArrowDown}{ArrowDown}{Enter}");

    expect(onChange).toHaveBeenCalledExactlyOnceWith("contacted");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("picks the first match on Enter after a search", async () => {
    renderPicker("identified");
    await openPicker();

    await userEvent.keyboard("off-lim{Enter}");

    expect(onChange).toHaveBeenCalledExactlyOnceWith("offLimits");
  });

  it("picks a clicked status", async () => {
    renderPicker("identified");
    const list = await openPicker();

    await userEvent.click(within(list).getByRole("option", { name: "Screened" }));

    expect(onChange).toHaveBeenCalledExactlyOnceWith("screened");
  });

  it("changes nothing when the held status is picked again, or on Escape", async () => {
    renderPicker("identified");
    const list = await openPicker();
    await userEvent.click(within(list).getByRole("option", { name: "Identified" }));
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    await openPicker();
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Status: Identified" })).toHaveFocus();
  });

  it("leaves a retired status out", async () => {
    renderPicker("identified");
    const list = await openPicker();

    expect(under(list, "Identified")).toEqual(["Identified", "Ready to approach"]);
  });

  it("still offers a retired status the candidate holds", async () => {
    renderPicker("researching");
    const list = await openPicker();

    expect(under(list, "Identified")).toEqual(["Identified", "Researching", "Ready to approach"]);
    expect(within(list).getByRole("option", { name: "Researching" })).toHaveAttribute("aria-selected", "true");
  });

  it("reads a status the catalog no longer lists by the row's own label", async () => {
    renderPicker("assessed", { currentLabel: "Assessed" });

    expect(screen.getByRole("button", { name: "Status: Assessed" })).toBeInTheDocument();
  });

  it("does not open while disabled", async () => {
    renderPicker("identified", { disabled: true });

    await userEvent.click(screen.getByRole("button", { name: /^Status:/ }));

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });
});
