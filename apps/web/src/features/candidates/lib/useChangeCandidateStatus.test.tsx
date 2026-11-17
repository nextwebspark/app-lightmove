import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aCurrentCatalog } from "../../../test/fixtures/pipelineCatalog";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { ExitReason } from "../../workspace/api/types";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate } from "../api/types";
import { useChangeCandidateStatus } from "./useChangeCandidateStatus";

vi.mock("../../workspace/api/pipelineApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/pipelineApi")>()),
  pipelineCatalog: vi.fn(),
  exitReasons: vi.fn(),
}));
vi.mock("../api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/candidatesApi")>()),
  changeCandidateStatus: vi.fn(),
}));

const catalog = aCurrentCatalog();
const statusId = (key: string) =>
  catalog.stages.flatMap((stage) => stage.statuses).find((status) => status.key === key)!.id;

function reason(id: string, label: string, extra: Partial<ExitReason> = {}): ExitReason {
  return { id, key: id, label, requiresNote: false, position: 1, retired: false, statusIds: [statusId("notInterested")], ...extra };
}

function Mover({ to }: { to: string }) {
  const change = useChangeCandidateStatus("p1", () => {});
  return (
    <>
      <button onClick={() => change.mutate({ candidateId: "c1", name: "Layla Haddad", status: to })}>Move</button>
      {change.dialog}
    </>
  );
}

function renderMover(to: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <Mover to={to} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("useChangeCandidateStatus", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(pipelineApi.pipelineCatalog).mockResolvedValue(catalog);
    vi.mocked(pipelineApi.exitReasons).mockResolvedValue({
      reasons: [
        reason("compensation", "Compensation"),
        reason("other", "Other", { requiresNote: true }),
        reason("retiredOne", "Retired one", { retired: true }),
      ],
    });
    vi.mocked(candidatesApi.changeCandidateStatus).mockResolvedValue({
      id: "c1",
      fullName: "Layla Haddad",
      status: "notInterested",
    } as Candidate);
  });

  it("asks why before a move to a way out that offers reasons, and sends the reason chosen", async () => {
    renderMover("notInterested");
    await screen.findByRole("button", { name: "Move" });
    await userEvent.click(screen.getByRole("button", { name: "Move" }));

    expect(await screen.findByRole("dialog", { name: "Why did Layla Haddad leave?" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Retired one" })).not.toBeInTheDocument();
    const save = screen.getByRole("button", { name: "Move to Not interested" });
    expect(save).toBeDisabled();

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Reason" }), "Other");
    expect(save).toBeDisabled();
    await userEvent.type(screen.getByRole("textbox", { name: "Note" }), "Moving to Riyadh");
    await userEvent.click(save);

    expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "c1", "notInterested", {
      reasonId: "other",
      note: "Moving to Riyadh",
    });
  });

  it("sends nothing when the question is cancelled", async () => {
    renderMover("notInterested");
    await userEvent.click(await screen.findByRole("button", { name: "Move" }));
    await userEvent.click(await screen.findByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(candidatesApi.changeCandidateStatus).not.toHaveBeenCalled();
  });

  it("moves straight through to a status in process", async () => {
    renderMover("engaged");
    await userEvent.click(await screen.findByRole("button", { name: "Move" }));

    expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "c1", "engaged", null);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
