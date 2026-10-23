import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as notetakerApi from "../../api/notetakerApi";
import type { InterviewPlan, InterviewQuestion } from "../../api/notetakerApi";
import { InterviewPlanDialog } from "./InterviewPlanDialog";

vi.mock("../../api/notetakerApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../api/notetakerApi")>()),
  getInterviewPlan: vi.fn(),
  draftInterviewPlan: vi.fn(),
  saveInterviewPlan: vi.fn(),
}));

const FIT: InterviewQuestion = {
  id: "q1",
  section: "FIT",
  panel: "TECHNICAL",
  competency: "Treasury",
  gap: null,
  text: "Tell me about a refinancing you led.",
  listenFor: "A named deal and her own call",
  why: null,
  included: true,
  source: "AI",
};

const NOTICE: InterviewQuestion = {
  ...FIT,
  id: "q2",
  section: "GAP",
  panel: null,
  competency: null,
  gap: "NOTICE",
  text: "What notice would you need to give?",
  listenFor: null,
};

function aPlan(overrides: Partial<InterviewPlan> = {}): InterviewPlan {
  return {
    meetingId: "m1",
    title: "Intro call",
    startsAt: "2026-10-28T07:00:00Z",
    drafted: true,
    savedAt: null,
    savedByName: null,
    competencies: [
      { panel: "TECHNICAL", name: "Treasury", weight: 20, evidenced: false },
      { panel: "BEHAVIOURAL", name: "Board", weight: 15, evidenced: true },
    ],
    missing: ["NOTICE"],
    questions: [FIT, NOTICE],
    ...overrides,
  };
}

function renderDialog() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <InterviewPlanDialog projectId="p1" candidateId="c1" meetingId="m1" firstName="Rania" onClose={vi.fn()} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("InterviewPlanDialog", () => {
  beforeEach(() => vi.resetAllMocks());

  it("drafts the plan once on opening, grouped by competency with what to listen for", async () => {
    vi.mocked(notetakerApi.getInterviewPlan).mockResolvedValue(aPlan({ drafted: false, questions: [] }));
    vi.mocked(notetakerApi.draftInterviewPlan).mockResolvedValue(aPlan());
    renderDialog();

    expect(await screen.findByText("Tell me about a refinancing you led.")).toBeInTheDocument();
    expect(notetakerApi.draftInterviewPlan).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Treasury")).toBeInTheDocument();
    expect(screen.getByText("A named deal and her own call")).toBeInTheDocument();
    expect(screen.getByText("For the record: Notice")).toBeInTheDocument();
    expect(screen.getByText("2 of 2 ticked")).toBeInTheDocument();
  });

  it("saves the questions as the consultant left them: unticked, edited and added", async () => {
    vi.mocked(notetakerApi.getInterviewPlan).mockResolvedValue(aPlan());
    vi.mocked(notetakerApi.saveInterviewPlan).mockImplementation(async (_p, _c, _m, questions) =>
      aPlan({ questions: questions.map((question, index) => ({ ...question, id: question.id ?? `n${index}` })) }),
    );
    renderDialog();

    const ask = await screen.findAllByRole("checkbox", { name: "Ask this question" });
    await userEvent.click(ask[1]);
    await userEvent.click(screen.getAllByRole("button", { name: "Edit question" })[0]);
    const edited = screen.getByRole("textbox", { name: "Question" });
    await userEvent.clear(edited);
    await userEvent.type(edited, "Walk me through one refinancing.");
    await userEvent.click(screen.getAllByRole("button", { name: "+ Add your own" })[2]);
    await userEvent.type(screen.getAllByRole("textbox", { name: "Question" }).at(-1)!, "My own question.");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    const saved = vi.mocked(notetakerApi.saveInterviewPlan).mock.calls[0][3];
    expect(saved.map((question) => [question.id, question.text, question.included])).toEqual([
      ["q1", "Walk me through one refinancing.", true],
      ["q2", "What notice would you need to give?", false],
      [null, "My own question.", true],
    ]);
    expect(saved.every((question) => !("key" in question))).toBe(true);
    expect(await screen.findByText("Questions for Rania saved.")).toBeInTheDocument();
  });

  it("offers Try again when the AI could not answer, and drafts again when pressed", async () => {
    vi.mocked(notetakerApi.getInterviewPlan).mockResolvedValue(aPlan({ drafted: false, questions: [] }));
    vi.mocked(notetakerApi.draftInterviewPlan)
      .mockRejectedValueOnce(
        new ApiRequestError({ code: "AI_UNAVAILABLE", detail: "x", status: 502, correlationId: "c" }),
      )
      .mockResolvedValueOnce(aPlan());
    renderDialog();

    expect(await screen.findByText("Couldn't prepare the questions just now.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Tell me about a refinancing you led.")).toBeInTheDocument();
  });
});
