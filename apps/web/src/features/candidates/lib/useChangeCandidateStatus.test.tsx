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

function TwoRows({ onSettled }: { onSettled: (candidateId: string) => void }) {
  const change = useChangeCandidateStatus("p1", () => {});
  const move = (candidateId: string) =>
    change.mutate(
      { candidateId, name: candidateId, status: "engaged" },
      { onSettled: () => onSettled(candidateId) },
    );
  return (
    <>
      <button onClick={() => move("c1")}>Move first</button>
      <button onClick={() => move("c2")}>Move second</button>
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
    const save = screen.getByRole("button", { name: "Done" });
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

  it("fills in the way out picked, which the dialog still lets them correct", async () => {
    renderMover("notInterested");
    await userEvent.click(await screen.findByRole("button", { name: "Move" }));

    const wayOut = await screen.findByRole("combobox", { name: "Way out" });
    expect(wayOut).toHaveDisplayValue("Not interested");
    await userEvent.selectOptions(wayOut, "Unresponsive");
    expect(screen.getByText("Unresponsive asks for no reason.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Done" }));

    expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "c1", "unresponsive", null);
  });

  it("sends nothing when the question is cancelled", async () => {
    renderMover("notInterested");
    await userEvent.click(await screen.findByRole("button", { name: "Move" }));
    await userEvent.click(await screen.findByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(candidatesApi.changeCandidateStatus).not.toHaveBeenCalled();
  });

  it("stops, and says so, when the reasons cannot be read rather than sending a move certain to be refused", async () => {
    vi.mocked(pipelineApi.exitReasons).mockRejectedValue(new Error("offline"));
    renderMover("notInterested");
    await userEvent.click(await screen.findByRole("button", { name: "Move" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(candidatesApi.changeCandidateStatus).not.toHaveBeenCalled();
  });

  it("moves straight through to a status in process", async () => {
    renderMover("engaged");
    await userEvent.click(await screen.findByRole("button", { name: "Move" }));

    expect(candidatesApi.changeCandidateStatus).toHaveBeenCalledWith("p1", "c1", "engaged", null);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("settles every row it moved, not only the last, when a second move starts before the first is saved", async () => {
    const pending: Array<(saved: Candidate) => void> = [];
    vi.mocked(candidatesApi.changeCandidateStatus).mockImplementation(
      (_projectId, candidateId) =>
        new Promise<Candidate>((resolve) =>
          pending.push(() => resolve({ id: candidateId, fullName: candidateId, status: "engaged" } as Candidate)),
        ),
    );
    const settled = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ToastProvider>
          <TwoRows onSettled={settled} />
        </ToastProvider>
      </QueryClientProvider>,
    );

    await userEvent.click(await screen.findByRole("button", { name: "Move first" }));
    await userEvent.click(screen.getByRole("button", { name: "Move second" }));
    await vi.waitFor(() => expect(pending).toHaveLength(2));
    pending.forEach((resolve) => resolve({} as Candidate));

    await vi.waitFor(() => expect(settled).toHaveBeenCalledTimes(2));
    expect(settled).toHaveBeenCalledWith("c1");
    expect(settled).toHaveBeenCalledWith("c2");
  });
});
