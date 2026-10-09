import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as gettingStartedApi from "../api/gettingStartedApi";
import type { GettingStarted, GettingStartedStepKey } from "../api/gettingStartedApi";
import { GettingStartedCard } from "./GettingStartedCard";

vi.mock("../api/gettingStartedApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/gettingStartedApi")>()),
  gettingStarted: vi.fn(),
  setDismissed: vi.fn(),
  setSkipped: vi.fn(),
}));
vi.mock("../../workspace/lib/vocabulary", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../workspace/lib/vocabulary")>();
  return { ...actual, useWorkspaceVocabulary: () => actual.vocabularyFor("AGENCY") };
});

const ALL: GettingStartedStepKey[] = [
  "OPEN_POSITION",
  "WRITE_BRIEF",
  "FIND_COMPANIES",
  "MAP_EXECUTIVES",
  "CONNECT_MAILBOX",
  "INVITE_COLLEAGUE",
];

function stateWith(done: GettingStartedStepKey[], extra: Partial<GettingStarted> = {}): GettingStarted {
  return {
    dismissed: false,
    focusProjectId: done.includes("OPEN_POSITION") ? "p1" : null,
    steps: ALL.map((step) => ({ step, done: done.includes(step), skipped: false, completedAt: null })),
    ...extra,
  };
}

const renderCard = (onOpenPosition = vi.fn()) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <ToastProvider>
          <GettingStartedCard onOpenPosition={onOpenPosition} />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe("GettingStartedCard", () => {
  beforeEach(() => vi.resetAllMocks());

  it("starts with signup's two steps already done", async () => {
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(stateWith([]));

    renderCard();

    expect(await screen.findByText("Get your first map in 30 minutes")).toBeInTheDocument();
    expect(screen.getByText("2 of 8")).toBeInTheDocument();
    expect(screen.getByText("Create your account")).toBeInTheDocument();
    expect(screen.getByText("Set up your workspace")).toBeInTheDocument();
  });

  it("opens the New position dialog from its row, and holds the position steps until one exists", async () => {
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(stateWith([]));
    const onOpenPosition = vi.fn();

    renderCard(onOpenPosition);
    await userEvent.click(await screen.findByRole("button", { name: /new position/i }));

    expect(onOpenPosition).toHaveBeenCalled();
    expect(screen.getAllByText("Open a position first.")).toHaveLength(4);
    expect(screen.queryByRole("link", { name: /open strategy/i })).not.toBeInTheDocument();
  });

  it("deep-links each step to its screen on the newest position", async () => {
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(stateWith(["OPEN_POSITION"]));

    renderCard();

    expect(await screen.findByRole("link", { name: /open the brief/i })).toHaveAttribute("href", "/projects/p1");
    expect(screen.getByRole("link", { name: /open strategy/i })).toHaveAttribute("href", "/projects/p1/strategy");
    expect(screen.getByRole("link", { name: /open in universe/i })).toHaveAttribute(
      "href",
      "/projects/p1/companies/universe",
    );
    expect(screen.getByRole("link", { name: /open team/i })).toHaveAttribute("href", "/team");
    expect(screen.getByText("3 of 8")).toBeInTheDocument();
  });

  it("skips a step and lets the skip be undone", async () => {
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(stateWith(["OPEN_POSITION"]));
    const skipped = stateWith(["OPEN_POSITION"]);
    skipped.steps[4] = { ...skipped.steps[4], skipped: true };
    vi.mocked(gettingStartedApi.setSkipped).mockResolvedValue(skipped);

    renderCard();
    await userEvent.click(await screen.findByRole("button", { name: "Skip: Connect your mailbox" }));

    expect(gettingStartedApi.setSkipped).toHaveBeenCalledWith("CONNECT_MAILBOX", true);
    expect(await screen.findByRole("button", { name: "Undo skip: Connect your mailbox" })).toBeInTheDocument();
  });

  it("puts itself away, with an Undo", async () => {
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(stateWith([]));
    vi.mocked(gettingStartedApi.setDismissed).mockResolvedValue(stateWith([], { dismissed: true }));

    renderCard();
    await userEvent.click(await screen.findByRole("button", { name: "I know my way around" }));

    expect(gettingStartedApi.setDismissed).toHaveBeenCalledWith(true);
    await waitFor(() => expect(screen.queryByText("Get your first map in 30 minutes")).not.toBeInTheDocument());
    expect(await screen.findByRole("button", { name: "Undo" })).toBeInTheDocument();
  });

  it("is gone once every step is done or skipped", async () => {
    const finished = stateWith(ALL.slice(0, 5));
    finished.steps[5] = { ...finished.steps[5], skipped: true };
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(finished);

    renderCard();

    await waitFor(() => expect(gettingStartedApi.gettingStarted).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.queryByText("Get your first map in 30 minutes")).not.toBeInTheDocument();
  });

  it("stands its fallback in once put away", async () => {
    vi.mocked(gettingStartedApi.gettingStarted).mockResolvedValue(stateWith([], { dismissed: true }));

    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <GettingStartedCard onOpenPosition={vi.fn()} fallback={<p>Start your first search</p>} />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Start your first search")).toBeInTheDocument();
    expect(screen.queryByText("Get your first map in 30 minutes")).not.toBeInTheDocument();
  });
});
