import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as apiClient from "../../../../lib/apiClient";
import * as notetakerApi from "../../api/notetakerApi";
import type { Copilot } from "../../api/notetakerApi";
import { CopilotPanel } from "./CopilotPanel";

vi.mock("../../api/notetakerApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../api/notetakerApi")>()),
  getCopilot: vi.fn(),
  decideNudge: vi.fn(),
  stopCopilot: vi.fn(),
  markPlanQuestionAsked: vi.fn(),
}));
vi.mock("../../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../lib/apiClient")>()),
  streamEvents: vi.fn(),
}));

function aCopilot(overrides: Partial<Copilot> = {}): Copilot {
  return {
    recordingId: "r1",
    title: "Intro call",
    candidateName: "Rania Haddad",
    startsAt: "2026-10-23T10:00:00Z",
    status: "RECORDING",
    liveStatus: "LISTENING",
    coach: "RUNNING",
    stoppedByName: null,
    mayStop: true,
    coverage: [
      {
        panel: "TECHNICAL",
        competency: "Treasury",
        weight: 20,
        state: "EVIDENCED",
        quote: "We refinanced 1.2bn.",
        at: "05:33",
      },
      { panel: "TECHNICAL", competency: "M&A", weight: 15, state: "TOUCHED", quote: null, at: null },
      { panel: "BEHAVIOURAL", competency: "Board", weight: 15, state: "NOT_YET", quote: null, at: null },
    ],
    gaps: [
      { gap: "NOTICE", heard: true, at: "09:12" },
      { gap: "PACKAGE", heard: false, at: null },
    ],
    plan: [{ id: "q1", section: "FIT", competency: "M&A", text: "Walk me through an integration.", asked: false }],
    nudges: [{ id: "n1", text: "Ask for an integration example.", reason: "M&A: no example yet" }],
    lines: [
      { seq: 1, at: "2026-10-23T10:01:00Z", speaker: "Omar Siddiqui", text: "Thanks for making time.", team: true },
      { seq: 2, at: "2026-10-23T10:01:10Z", speaker: "Rania Haddad", text: "Happy to.", team: false },
    ],
    speaking: [],
    lastSeq: 2,
    viewers: [
      { userId: "u1", name: "Omar Siddiqui", you: true },
      { userId: "u2", name: "Lina Karam", you: false },
    ],
    ...overrides,
  };
}

function renderPanel(onClose = vi.fn()) {
  render(<CopilotPanel projectId="p1" candidateId="c1" recordingId="r1" variant="window" onClose={onClose} />);
  return onClose;
}

describe("CopilotPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(apiClient.streamEvents).mockReturnValue(new Promise(() => {}));
  });

  it("heads the window with the mark, its name and who is watching, then coverage, gaps and what to ask", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot());
    renderPanel();

    expect(await screen.findByText("Intro call · Rania Haddad")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Interview copilot" })).toBeInTheDocument();
    expect(screen.getByTitle("Lina Karam is watching")).toBeInTheDocument();
    expect(screen.getByTitle("Treasury: Evidenced")).toBeInTheDocument();
    expect(screen.getByTitle("M&A: Touched, no example")).toBeInTheDocument();
    expect(screen.getByText("Ask for an integration example.")).toBeInTheDocument();
    expect(screen.getByText(/Happy to\./)).toBeInTheDocument();
    expect(notetakerApi.getCopilot).toHaveBeenCalledWith("p1", "c1", "r1", 0);

    await userEvent.click(screen.getByTitle("Treasury: Evidenced"));
    expect(screen.getByText(/We refinanced 1\.2bn\./)).toBeInTheDocument();
  });

  it("asks only for the lines after the last one held, and keeps the ones it has", async () => {
    vi.mocked(notetakerApi.getCopilot)
      .mockResolvedValueOnce(aCopilot())
      .mockResolvedValueOnce(
        aCopilot({
          lines: [{ seq: 3, at: "2026-10-23T10:02:00Z", speaker: "Rania Haddad", text: "Three months.", team: false }],
          lastSeq: 3,
        }),
      );
    vi.mocked(notetakerApi.decideNudge).mockResolvedValue(undefined);
    renderPanel();

    await userEvent.click(await screen.findByRole("button", { name: "Dismiss" }));

    expect(notetakerApi.decideNudge).toHaveBeenCalledWith("p1", "c1", "r1", "n1", "dismiss");
    await waitFor(() => expect(notetakerApi.getCopilot).toHaveBeenLastCalledWith("p1", "c1", "r1", 2));
    await userEvent.click(screen.getByRole("tab", { name: "Transcript" }));
    expect(await screen.findByText(/Three months\./)).toBeInTheDocument();
    expect(screen.getByText(/Happy to\./)).toBeInTheDocument();
  });

  it("stops the copilot for everyone only after asking, from the menu", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot());
    vi.mocked(notetakerApi.stopCopilot).mockResolvedValue(
      aCopilot({ liveStatus: "STOPPED", stoppedByName: "Omar Siddiqui", nudges: [] }),
    );
    renderPanel();

    await userEvent.click(await screen.findByRole("button", { name: "More" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Stop copilot" }));
    expect(screen.getByRole("alertdialog", { name: "Stop the copilot for this call?" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Stop copilot" }));

    expect(notetakerApi.stopCopilot).toHaveBeenCalledWith("p1", "c1", "r1");
    expect(await screen.findByText(/Copilot stopped by Omar Siddiqui/)).toBeInTheDocument();
  });

  it("says why suggestions are not current, and what stayed open when the call ended", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot({ coach: "PAUSED" }));
    const { unmount } = render(
      <CopilotPanel projectId="p1" candidateId="c1" recordingId="r1" variant="tab" />,
    );
    expect(await screen.findByText("Suggestions paused. Retrying when more is said.")).toBeInTheDocument();
    unmount();

    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot({ status: "PROCESSING" }));
    renderPanel();
    expect(await screen.findByText(/Not covered: M&A · Board · Package/)).toBeInTheDocument();
    expect(screen.getByText("No more suggestions for this call.")).toBeInTheDocument();
  });

  it("ticks a prepared question as asked by hand, and closes its window on ✕", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot());
    vi.mocked(notetakerApi.markPlanQuestionAsked).mockResolvedValue(undefined);
    const onClose = renderPanel();

    await userEvent.click(await screen.findByRole("tab", { name: /Plan/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: /Walk me through an integration/ }));
    expect(notetakerApi.markPlanQuestionAsked).toHaveBeenCalledWith("p1", "c1", "r1", "q1", true);

    await userEvent.click(screen.getByRole("button", { name: "Close window" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("sits in the drawer's tab as one toolbar: the views, the call without a name, Pop out and the menu", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot());
    const onPopOut = vi.fn();
    render(<CopilotPanel projectId="p1" candidateId="c1" recordingId="r1" variant="tab" onPopOut={onPopOut} />);

    expect(await screen.findByText("Intro call")).toBeInTheDocument();
    expect(screen.queryByText("Intro call · Rania Haddad")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Interview copilot" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Close window" })).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Live" })).toHaveAttribute("aria-selected", "true");

    await userEvent.click(screen.getByRole("button", { name: "Pop out" }));
    expect(onPopOut).toHaveBeenCalled();
  });

  it("counts the suggestions waiting, and none while they are muted", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot());
    const onNudgeCount = vi.fn();
    render(
      <CopilotPanel projectId="p1" candidateId="c1" recordingId="r1" variant="tab" onNudgeCount={onNudgeCount} />,
    );

    await waitFor(() => expect(onNudgeCount).toHaveBeenLastCalledWith(1));
    await userEvent.click(screen.getByRole("button", { name: "More" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Mute suggestions" }));
    expect(onNudgeCount).toHaveBeenLastCalledWith(0);
  });

  it("offers the floating window's way back into Uncava from its menu", async () => {
    vi.mocked(notetakerApi.getCopilot).mockResolvedValue(aCopilot());
    const onShowInUncava = vi.fn();
    render(
      <CopilotPanel
        projectId="p1"
        candidateId="c1"
        recordingId="r1"
        variant="window"
        onClose={vi.fn()}
        onShowInUncava={onShowInUncava}
      />,
    );

    await userEvent.click(await screen.findByRole("button", { name: "More" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Show in Uncava" }));
    expect(onShowInUncava).toHaveBeenCalled();
  });
});
