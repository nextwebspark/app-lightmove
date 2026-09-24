import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../components/ui/Toast";
import type { AssistantThread, AssistantTurn, LiveStep } from "./api/types";
import { AssistantProvider } from "./AssistantProvider";
import { AssistantPanel } from "./components/AssistantPanel";

const ask = vi.hoisted(() => vi.fn());
const getThread = vi.hoisted(() => vi.fn());
const listThreads = vi.hoisted(() => vi.fn());
const acceptProposal = vi.hoisted(() => vi.fn());
const listStarters = vi.hoisted(() => vi.fn());
vi.mock("./api/assistantApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/assistantApi")>()),
  ask,
  listStarters,
  getThread,
  listThreads,
  acceptProposal,
}));

function turn(id: string, threadId: string, over: Partial<AssistantTurn> = {}): AssistantTurn {
  return {
    id,
    threadId,
    question: `Question ${id}`,
    answer: `Answer ${id}`,
    steps: [],
    proposal: null,
    proposalAccepted: null,
    createdAt: "2026-01-01T00:00:00Z",
    ...over,
  };
}

function thread(id: string, turns: AssistantTurn[]): AssistantThread {
  return { id, title: `Chat ${id}`, projectId: "p1", turns };
}

const CARD = {
  title: "Two retailers",
  companies: [
    { apolloAccountId: "a1", companyName: "Majid Al Futtaim", country: "United Arab Emirates", employees: 40000, logoUrl: null },
    { apolloAccountId: "a2", companyName: "Landmark Group", country: "United Arab Emirates", employees: 50000, logoUrl: null },
  ],
};

function mount() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <AssistantProvider>
          <AssistantPanel contextLabel="Meridian · CFO" projectId="p1" />
        </AssistantProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

async function send(question: string) {
  await userEvent.type(screen.getByRole("textbox", { name: "Ask the assistant" }), question);
  await userEvent.click(screen.getByRole("button", { name: "Send" }));
}

describe("a chat with the assistant", () => {
  beforeEach(() => {
    localStorage.clear();
    listStarters.mockResolvedValue({ sectorAssumed: false, starters: [] });
  });
  afterEach(() => vi.resetAllMocks());

  it("asks a suggested question in one press, marking the adjacent sectors out", async () => {
    const answered = turn("t1", "th1", { question: "Top Supermarkets companies in UAE" });
    listStarters.mockResolvedValue({
      sectorAssumed: false,
      starters: [
        { kind: "SECTOR", prompt: "Top 10 Retail companies in UAE" },
        { kind: "ADJACENT", prompt: "Top Supermarkets companies in UAE" },
      ],
    });
    ask.mockResolvedValue(answered);
    getThread.mockResolvedValue(thread("th1", [answered]));

    mount();

    expect(await screen.findByText("Adjacent · transferable talent")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Top Supermarkets companies in UAE/ }));

    expect(ask).toHaveBeenCalledWith("p1", "Top Supermarkets companies in UAE", null, expect.any(Function), expect.any(Function));
    expect(await screen.findByText("Answer t1")).toBeInTheDocument();
  });

  it("says when it assumed retail for a firm with no sector on record", async () => {
    listStarters.mockResolvedValue({
      sectorAssumed: true,
      starters: [{ kind: "SECTOR", prompt: "Top 10 Retail companies" }],
    });

    mount();

    expect(await screen.findByRole("button", { name: /Top 10 Retail companies/ })).toBeInTheDocument();
    expect(screen.getByText(/Assuming retail/)).toBeInTheDocument();
  });

  it("answers with a card of companies that can be filed straight away", async () => {
    const answered = turn("t1", "th1", { question: "Top retailers in UAE", proposal: CARD });
    ask.mockResolvedValue(answered);
    getThread.mockResolvedValue(thread("th1", [answered]));
    acceptProposal.mockResolvedValue({ added: 2, skipped: 0 });

    mount();
    await send("Top retailers in UAE");

    expect(await screen.findByText("Majid Al Futtaim")).toBeInTheDocument();
    expect(ask).toHaveBeenCalledWith("p1", "Top retailers in UAE", null, expect.any(Function), expect.any(Function));

    await userEvent.click(screen.getByRole("button", { name: "Universe" }));

    await waitFor(() => expect(acceptProposal).toHaveBeenCalledWith("t1", ["a1", "a2"], "inUniverse"));
  });

  it("asks a follow-up in the same chat", async () => {
    const first = turn("t1", "th1");
    const second = turn("t2", "th1");
    ask.mockResolvedValueOnce(first).mockResolvedValueOnce(second);
    getThread.mockResolvedValueOnce(thread("th1", [first])).mockResolvedValue(thread("th1", [first, second]));

    mount();
    await send("Question t1");
    await screen.findByText("Answer t1");
    await send("Question t2");

    expect(await screen.findByText("Answer t2")).toBeInTheDocument();
    expect(ask).toHaveBeenLastCalledWith("p1", "Question t2", "th1", expect.any(Function), expect.any(Function));
  });

  it("lists this project's chats and opens one", async () => {
    listThreads.mockResolvedValue([{ id: "old", title: "Qatar contractors", updatedAt: "2026-01-01T00:00:00Z" }]);
    getThread.mockResolvedValue(thread("old", [turn("t9", "old")]));

    mount();
    await userEvent.click(screen.getByRole("button", { name: "History" }));
    await userEvent.click(await screen.findByRole("button", { name: "Qatar contractors" }));

    expect(await screen.findByText("Answer t9")).toBeInTheDocument();
    expect(listThreads).toHaveBeenCalledWith("p1");
  });

  it("starts a new chat, leaving the old one in the history", async () => {
    const answered = turn("t1", "th1");
    ask.mockResolvedValue(answered);
    getThread.mockResolvedValue(thread("th1", [answered]));

    mount();
    await send("Question t1");
    await screen.findByText("Answer t1");
    await userEvent.click(screen.getByRole("button", { name: "New chat" }));

    expect(screen.queryByText("Answer t1")).not.toBeInTheDocument();
    expect(screen.getByText("Find companies for this mandate.")).toBeInTheDocument();
  });

  it("shows each step while it works, and keeps them with the answer", async () => {
    const answered = turn("t1", "th1", {
      steps: [{ label: "Searching retail companies in United Arab Emirates", detail: "342 matched, showing the top 25" }],
    });
    let finish: (value: AssistantTurn) => void = () => {};
    ask.mockImplementation((_projectId, _question, _threadId, onStep: (step: LiveStep) => void) => {
      onStep({ index: 0, label: "Searching retail companies in United Arab Emirates", detail: null, done: false });
      onStep({ index: 0, label: "Searching retail companies in United Arab Emirates", detail: "342 matched, showing the top 25", done: true });
      onStep({ index: 1, label: "Preparing 10 companies", detail: null, done: false });
      return new Promise<AssistantTurn>((resolve) => {
        finish = resolve;
      });
    });
    getThread.mockResolvedValue(thread("th1", [answered]));

    mount();
    await send("Top retailers in UAE");

    expect(await screen.findByText("Preparing 10 companies")).toBeInTheDocument();
    expect(screen.getByText(/342 matched, showing the top 25/)).toBeInTheDocument();

    finish(answered);

    expect(await screen.findByText("Answer t1")).toBeInTheDocument();
    expect(screen.queryByText("Preparing 10 companies")).not.toBeInTheDocument();
    expect(screen.getByText(/342 matched, showing the top 25/)).toBeInTheDocument();
  });

  it("holds the card back until the answer is saved, then draws it once and fileable", async () => {
    const answered = turn("t1", "th1", { question: "Top retailers in UAE", proposal: CARD });
    let finish: (value: AssistantTurn) => void = () => {};
    ask.mockImplementation((_projectId, _question, _threadId, onStep: (step: LiveStep) => void,
      onProposal: (proposal: typeof CARD) => void) => {
      onStep({ index: 0, label: "Preparing 2 companies", detail: "2 on the card", done: true });
      onProposal(CARD);
      return new Promise<AssistantTurn>((resolve) => {
        finish = resolve;
      });
    });
    getThread.mockResolvedValue(thread("th1", [answered]));

    mount();
    await send("Top retailers in UAE");

    expect(await screen.findByText("Preparing the card for 2 companies…")).toBeInTheDocument();
    expect(screen.getByText("Writing the answer")).toBeInTheDocument();
    expect(screen.queryByText("Landmark Group")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Universe" })).not.toBeInTheDocument();

    finish(answered);

    expect(await screen.findByText("Answer t1")).toBeInTheDocument();
    expect(screen.queryByText("Preparing the card for 2 companies…")).not.toBeInTheDocument();
    expect(screen.getAllByText("Landmark Group")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Universe" })).toBeEnabled();
  });

  it("leaves the person in the chat they moved to when an earlier answer lands", async () => {
    const answered = turn("t1", "th1");
    let finish: (value: AssistantTurn) => void = () => {};
    ask.mockImplementation(() => new Promise<AssistantTurn>((resolve) => {
      finish = resolve;
    }));
    getThread.mockResolvedValue(thread("th1", [answered]));

    mount();
    await send("Question t1");
    await userEvent.click(screen.getByRole("button", { name: "New chat" }));

    finish(answered);

    await waitFor(() => expect(screen.getByText("Find companies for this mandate.")).toBeInTheDocument());
    expect(screen.queryByText("Answer t1")).not.toBeInTheDocument();
  });

  it("says a slow answer is still coming rather than inviting a paid retry", async () => {
    const { ApiRequestError } = await import("../../lib/apiClient");
    ask.mockRejectedValue(new ApiRequestError({
      code: "ASSISTANT_STILL_ANSWERING", detail: "", status: 202, correlationId: "none",
    }));

    mount();
    await send("Top retailers in UAE");

    expect(await screen.findByText(/no need to ask again/)).toBeInTheDocument();
  });

  it("shows a sent question once, acknowledged at once, and follows the chat down as it grows", async () => {
    const scrolled = vi.fn();
    Element.prototype.scrollTo = scrolled;
    const answered = turn("t1", "th1", { question: "Which sector is best?" });
    let finish: (value: AssistantTurn) => void = () => {};
    ask.mockImplementation(() => new Promise<AssistantTurn>((resolve) => {
      finish = resolve;
    }));
    getThread.mockResolvedValue(thread("th1", [answered]));

    mount();
    await send("Which sector is best?");

    expect(await screen.findByText("Reading your question")).toBeInTheDocument();
    expect(screen.getAllByText("Which sector is best?")).toHaveLength(1);
    expect(scrolled).toHaveBeenCalled();
    scrolled.mockClear();

    finish(answered);

    expect(await screen.findByText("Answer t1")).toBeInTheDocument();
    expect(scrolled).toHaveBeenCalled();
    expect(screen.getAllByText("Which sector is best?")).toHaveLength(1);
    expect(screen.queryByText("Reading your question")).not.toBeInTheDocument();
  });
});
