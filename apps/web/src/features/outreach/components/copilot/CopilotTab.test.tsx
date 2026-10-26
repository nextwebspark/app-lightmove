import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { closeCopilotWindow, openCopilotWindow, useCopilotWindowOpen } from "../../lib/copilotWindow";
import { CopilotTab } from "./CopilotTab";

vi.mock("../../lib/copilotWindow", () => ({
  useCopilotWindowOpen: vi.fn(),
  openCopilotWindow: vi.fn(),
  closeCopilotWindow: vi.fn(),
}));
vi.mock("./CopilotPanel", () => ({
  CopilotPanel: ({ onPopOut }: { onPopOut: () => void }) => (
    <button type="button" onClick={onPopOut}>
      Pop out
    </button>
  ),
}));

function renderTab(onNudgeCount = vi.fn()) {
  render(
    <ToastProvider>
      <CopilotTab
        projectId="p1"
        candidateId="c1"
        recordingId="r1"
        firstName="Rania"
        onShow={vi.fn()}
        onNudgeCount={onNudgeCount}
      />
    </ToastProvider>,
  );
  return onNudgeCount;
}

describe("CopilotTab", () => {
  beforeEach(() => vi.resetAllMocks());

  it("draws the copilot, and pops it out into a window over the call", async () => {
    vi.mocked(useCopilotWindowOpen).mockReturnValue(false);
    vi.mocked(openCopilotWindow).mockResolvedValue("floating");
    renderTab();

    await userEvent.click(screen.getByRole("button", { name: "Pop out" }));
    expect(openCopilotWindow).toHaveBeenCalledWith({ projectId: "p1", candidateId: "c1", recordingId: "r1" }, expect.any(Function));
  });

  it("says the browser blocked the window rather than doing nothing", async () => {
    vi.mocked(useCopilotWindowOpen).mockReturnValue(false);
    vi.mocked(openCopilotWindow).mockResolvedValue("blocked");
    renderTab();

    await userEvent.click(screen.getByRole("button", { name: "Pop out" }));
    expect(await screen.findByText(/Your browser blocked the window/)).toBeInTheDocument();
  });

  it("draws no second copy while the copilot is in its own window, and brings it back on Show here", async () => {
    vi.mocked(useCopilotWindowOpen).mockReturnValue(true);
    const onNudgeCount = renderTab();

    expect(screen.getByText("Copilot is in its own window")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Pop out" })).not.toBeInTheDocument();
    expect(onNudgeCount).toHaveBeenCalledWith(0);
    await userEvent.click(screen.getByRole("button", { name: "Show here" }));
    expect(closeCopilotWindow).toHaveBeenCalledWith("r1");
  });
});
