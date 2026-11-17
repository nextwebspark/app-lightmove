import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { aCurrentCatalog } from "../../../test/fixtures/pipelineCatalog";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { ExitReason, PipelineCatalog } from "../../workspace/api/types";
import { ExitReasonsCard } from "./ExitReasonsCard";

vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  exitReasons: vi.fn(),
  createExitReason: vi.fn(),
  updateExitReason: vi.fn(),
}));

/** The current seed, with Withdrew a way out at Offer as well as Assessment (V226). */
function catalogWithTwoWithdrews(): PipelineCatalog {
  const catalog = aCurrentCatalog();
  const offer = catalog.stages.find((stage) => stage.key === "shortlisted")!;
  offer.statuses.push({ ...offer.statuses[0], id: "withdrewOffer", key: "withdrewOffer", label: "Withdrew", outOfProcess: true, isDefault: false });
  return catalog;
}

const idOf = (catalog: PipelineCatalog, key: string) =>
  catalog.stages.flatMap((stage) => stage.statuses).find((status) => status.key === key)!.id;

function reason(id: string, label: string, statusIds: string[], extra: Partial<ExitReason> = {}): ExitReason {
  return { id, key: id, label, requiresNote: false, position: 1, retired: false, statusIds, ...extra };
}

function renderCard(catalog: PipelineCatalog, isAdmin = true) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <ExitReasonsCard catalog={catalog} isAdmin={isAdmin} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("ExitReasonsCard", () => {
  const catalog = catalogWithTwoWithdrews();
  const withdrew = idOf(catalog, "withdrew");
  const withdrewOffer = idOf(catalog, "withdrewOffer");
  const notInterested = idOf(catalog, "notInterested");

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(pipelineApi.exitReasons).mockResolvedValue({
      reasons: [
        reason("anotherOffer", "Another offer", [withdrew, withdrewOffer]),
        reason("other", "Other", [notInterested], { requiresNote: true }),
        reason("visa", "Visa", [], { retired: true }),
      ],
    });
  });

  it("lists each reason with where it is offered, a shared name told apart by its stage", async () => {
    renderCard(catalog);

    expect(await screen.findByText("Withdrew (Assessment) · Withdrew (Offer)")).toBeInTheDocument();
    expect(screen.getByText("Asks for a note")).toBeInTheDocument();
    expect(screen.getByText("Not interested")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Restore" })).toBeInTheDocument();
  });

  it("ticks a status from the reason's own list, offering only ways out", async () => {
    vi.mocked(pipelineApi.updateExitReason).mockResolvedValue({ reasons: [] });
    renderCard(catalog);

    await userEvent.click((await screen.findAllByRole("button", { name: "Statuses" }))[0]);
    const group = screen.getByRole("group", { name: "Statuses that offer Another offer" });
    expect(within(group).queryByRole("checkbox", { name: "Awaiting reply" })).not.toBeInTheDocument();
    await userEvent.click(within(group).getByRole("checkbox", { name: "Not interested" }));

    expect(pipelineApi.updateExitReason).toHaveBeenCalledWith("anotherOffer", {
      statusIds: [withdrew, withdrewOffer, notInterested],
    });
  });

  it("adds a reason, and says so when the name is taken", async () => {
    vi.mocked(pipelineApi.createExitReason).mockRejectedValue(
      new ApiRequestError({ code: "EXIT_REASON_TAKEN", detail: "x", status: 409, correlationId: "c1" }),
    );
    renderCard(catalog);

    await userEvent.type(await screen.findByRole("textbox", { name: "New reason" }), "other");
    await userEvent.click(screen.getByRole("button", { name: "Add reason" }));

    expect(pipelineApi.createExitReason).toHaveBeenCalledWith("other");
    expect(await screen.findByText("Your team already has that reason.")).toBeInTheDocument();
  });

  it("is read-only for a member", async () => {
    renderCard(catalog, false);

    await screen.findByText("Withdrew (Assessment) · Withdrew (Offer)");
    expect(screen.queryByRole("button", { name: "Statuses" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "New reason" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Restore" })).not.toBeInTheDocument();
  });
});
