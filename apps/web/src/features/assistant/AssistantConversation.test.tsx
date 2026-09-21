import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../components/ui/Toast";
import { ApiRequestError } from "../../lib/apiClient";
import type { AssistantThread, AssistantTurn } from "./api/types";
import { AssistantProvider } from "./AssistantProvider";
import { AssistantPanel } from "./components/AssistantPanel";

const ask = vi.hoisted(() => vi.fn());
const getThread = vi.hoisted(() => vi.fn());
vi.mock("./api/assistantApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/assistantApi")>()),
  ask,
  askIn: ask,
  getThread,
}));

// A turn that never settles: this file is about what is drawn, not about the stream.
const streamEvents = vi.hoisted(() => vi.fn(() => new Promise<void>(() => {})));
vi.mock("../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../lib/apiClient")>()),
  streamEvents,
}));

function turn(over: Partial<AssistantTurn> & { id: string }): AssistantTurn {
  return {
    threadId: "th1",
    status: "SUCCEEDED",
    question: `Question ${over.id}`,
    answer: `Answer ${over.id}`,
    errorCode: null,
    proposal: null,
    createdAt: "2026-01-01T00:00:00Z",
    finishedAt: "2026-01-01T00:01:00Z",
    ...over,
  };
}

function thread(turns: AssistantTurn[]): AssistantThread {
  return {
    id: "th1",
    title: "Top IPPs",
    projectId: "p1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:01:00Z",
    turns,
  };
}

function mount() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <AssistantProvider>
          <AssistantPanel contextLabel="Meridian Energy Group · CFO" projectId="p1" />
        </AssistantProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("the conversation the panel is having", () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem("lm.assistant.open", "1");
  });
  afterEach(() => {
    ask.mockReset();
    getThread.mockReset();
  });

  // The finding on #434: a second question replaced the first, so the panel showed one exchange at
  // a time and read as a bug rather than as a missing feature.
  it("keeps the exchanges already had, in the order they happened", async () => {
    localStorage.setItem("lm.assistant.thread", "th1");
    getThread.mockResolvedValue(thread([turn({ id: "t1" }), turn({ id: "t2" })]));

    mount();

    expect(await screen.findByText("Question t1")).toBeInTheDocument();
    expect(screen.getByText("Answer t2")).toBeInTheDocument();
    // Starters belong to an empty panel, not to a conversation already under way.
    expect(screen.queryByRole("button", { name: /Top 10 IPP operators/i })).not.toBeInTheDocument();
  });

  it("carries a card the conversation was left holding", async () => {
    localStorage.setItem("lm.assistant.thread", "th1");
    getThread.mockResolvedValue(
      thread([
        turn({
          id: "t1",
          proposal: {
            projectId: "p1",
            title: "2 companies not yet in your universe",
            companies: [
              { ref: "c1", origin: "UNIVERSE", apolloAccountId: "a1", companyName: "Marafiq", country: "Saudi Arabia", employees: 2400 },
            ],
            accepted: null,
          },
        }),
      ]),
    );

    mount();

    expect(await screen.findByText("2 companies not yet in your universe")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Shortlist" })).toBeEnabled();
  });

  // The live turn is drawn from its stream and the finished ones from the thread read. Both know
  // about the turn being asked, so the one that is running has to come from exactly one of them.
  it("draws the turn being answered once, not twice", async () => {
    ask.mockResolvedValue({ id: "t2", threadId: "th1", question: "Question t2" });
    getThread.mockResolvedValue(thread([turn({ id: "t1" }), turn({ id: "t2", status: "RUNNING" })]));

    mount();
    await userEvent.type(screen.getByRole("textbox", { name: "Ask the assistant" }), "Question t2");
    await userEvent.click(screen.getByRole("button", { name: "Send" }));

    await waitFor(() => expect(screen.getAllByText("Question t1")).toHaveLength(1));
    expect(screen.getAllByText("Question t2")).toHaveLength(1);
  });

  // A remembered thread can outlive the workspace it belongs to. Keeping it would fail every
  // question asked into it, so the panel lets go and offers its starters again.
  it("lets go of a remembered conversation the server will not open", async () => {
    localStorage.setItem("lm.assistant.thread", "gone");
    getThread.mockRejectedValue(
      new ApiRequestError({ code: "NOT_FOUND", detail: "No such thread", status: 404, correlationId: "c1" }),
    );

    mount();

    expect(await screen.findByRole("button", { name: /Top 10 IPP operators/i })).toBeInTheDocument();
    expect(localStorage.getItem("lm.assistant.thread")).toBeNull();
  });

  it("remembers the conversation across a reload, but opens no stream for it", async () => {
    ask.mockResolvedValue({ id: "t1", threadId: "th1", question: "Question t1" });
    getThread.mockResolvedValue(thread([turn({ id: "t1" })]));

    const first = mount();
    await userEvent.type(screen.getByRole("textbox", { name: "Ask the assistant" }), "Question t1");
    await userEvent.click(screen.getByRole("button", { name: "Send" }));
    await waitFor(() => expect(localStorage.getItem("lm.assistant.thread")).toBe("th1"));
    first.unmount();
    streamEvents.mockClear();

    mount();

    expect(await screen.findByText("Question t1")).toBeInTheDocument();
    // A restored thread is read back; a restored turn id would open a stream on a turn long over.
    expect(streamEvents).not.toHaveBeenCalled();
  });
});
