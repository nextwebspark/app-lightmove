import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { ApiRequestError } from "../../../lib/apiClient";
import type { AssistantProposal } from "../api/types";
import type { TurnProgress } from "../lib/useAssistantTurn";
import { AssistantTurnView } from "./AssistantTurnView";

const acceptProposal = vi.hoisted(() => vi.fn());
vi.mock("../api/assistantApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/assistantApi")>()),
  acceptProposal,
}));

const PROPOSAL: AssistantProposal = {
  projectId: "p1",
  title: "2 companies not yet in your universe",
  companies: [
    { ref: "c1", origin: "UNIVERSE", apolloAccountId: "a1", companyName: "Marafiq", country: "Saudi Arabia", employees: 2400 },
    { ref: "c2", origin: "UNIVERSE", apolloAccountId: "a2", companyName: "Sakaka Solar", country: "Saudi Arabia", employees: 120 },
  ],
  accepted: null,
};

function progress(over: Partial<TurnProgress> = {}): TurnProgress {
  return {
    answer: "Two match your filter.",
    steps: [],
    status: "SUCCEEDED",
    errorCode: null,
    proposal: PROPOSAL,
    ...over,
  };
}

/** Records what the accept sent looking for, so a test can prove the right mandate was refreshed. */
function mount(over: Partial<TurnProgress> = {}) {
  const queryClient = new QueryClient();
  const invalidated: unknown[] = [];
  vi.spyOn(queryClient, "invalidateQueries").mockImplementation(async (filters) => {
    invalidated.push(filters?.queryKey);
  });
  vi.spyOn(queryClient, "cancelQueries").mockImplementation(async () => {});

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AssistantTurnView
          turnId="t1"
          threadId="th1"
          question="Top IPPs in Saudi Arabia"
          progress={progress(over)}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return { invalidated };
}

describe("filing what the assistant proposed", () => {
  // Reset after rather than before: resetting a mock in `beforeEach` strands the rejected
  // promise a later test hands back, and the run fails on an error the component handled.
  afterEach(() => acceptProposal.mockReset());

  it("sends the ticked refs and the stage to that turn", async () => {
    acceptProposal.mockResolvedValue({ added: 1, skipped: 0 });
    mount();

    await userEvent.click(screen.getByRole("checkbox", { name: "Include Sakaka Solar" }));
    await userEvent.click(screen.getByRole("button", { name: "Shortlist" }));

    await waitFor(() => expect(acceptProposal).toHaveBeenCalledWith("t1", ["c1"], "shortlisted"));
  });

  it("says what was filed and what was already held", async () => {
    acceptProposal.mockResolvedValue({ added: 1, skipped: 1 });
    mount();

    await userEvent.click(screen.getByRole("button", { name: "Universe" }));

    // The toast and the card's own outcome line are one sentence, written once, so a reader who
    // missed the toast finds the same words where the card was.
    expect(await screen.findByRole("status")).toHaveTextContent(
      "1 company filed to In universe, 1 already in this mandate",
    );
    expect(
      screen.getAllByText("1 company filed to In universe, 1 already in this mandate"),
    ).toHaveLength(2);
  });

  // The panel is mounted above the routes, so the mandate the conversation is about is not
  // necessarily the one on screen — the proposal's own project is the only right answer.
  it("refreshes the mandate the proposal names", async () => {
    acceptProposal.mockResolvedValue({ added: 2, skipped: 0 });
    const { invalidated } = mount();

    await userEvent.click(screen.getByRole("button", { name: "Universe" }));

    await waitFor(() => expect(invalidated).toContainEqual(["triage", "p1"]));
    expect(invalidated).toContainEqual(["talentMap", "p1"]);
    // And the conversation itself, so reopening it shows a filed card rather than a live one.
    expect(invalidated).toContainEqual(["assistant", "thread", "th1"]);
  });

  it("replaces the card with its outcome rather than leaving it fileable twice", async () => {
    acceptProposal.mockResolvedValue({ added: 2, skipped: 0 });
    mount();

    await userEvent.click(screen.getByRole("button", { name: "Universe" }));

    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Universe" })).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.getAllByText("2 companies filed to In universe").length).toBeGreaterThan(0);
  });

  it("says what the server said when it refuses", async () => {
    acceptProposal.mockRejectedValue(
      new ApiRequestError({
        code: "ASSISTANT_PROPOSAL_ALREADY_ACCEPTED",
        detail: "Already accepted",
        status: 409,
        correlationId: "c1",
      }),
    );
    mount();

    await userEvent.click(screen.getByRole("button", { name: "Universe" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "These companies have already been filed.",
    );
  });

  it("draws no card for a turn that proposed nothing", () => {
    mount({ proposal: null });

    expect(screen.getByText("Two match your filter.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Universe" })).not.toBeInTheDocument();
  });
});
