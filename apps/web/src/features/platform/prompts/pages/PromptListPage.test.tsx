import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as promptApi from "../api/promptApi";
import PromptListPage from "./PromptListPage";
import { overview } from "./promptFixtures";

vi.mock("../api/promptApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/promptApi")>()),
  listPrompts: vi.fn(),
}));

function Pathname() {
  return <div data-testid="pathname">{useLocation().pathname}</div>;
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={["/platform/prompts"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/platform/prompts" element={<PromptListPage />} />
          <Route path="*" element={<Pathname />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const PROMPTS = [
  overview({ shippedChanged: true }),
  overview({
    id: "assistant-core",
    name: "Assistant",
    description: "The assistant's standing instructions on every screen.",
    kind: "TOOL_CALLING",
    liveVersion: 4,
    origin: "BUNDLED",
    provider: null,
    modelId: null,
    modelName: null,
    modelRetiresInDays: null,
    lastChangedBy: null,
  }),
  overview({ id: "meeting-summary", name: "Meeting summary", modelRetiresInDays: 12 }),
  overview({
    id: "call-changes",
    name: "Call changes",
    modelId: "gemini-1.0-pro",
    modelName: "gemini-1.0-pro",
    modelRetiresInDays: null,
    modelUnavailable: true,
  }),
];

describe("PromptListPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("draws what serves for each prompt: its version, where it came from, its model and who changed it last", async () => {
    vi.mocked(promptApi.listPrompts).mockResolvedValue(PROMPTS);
    renderPage();

    const assessment = (await screen.findByText("Assessment draft")).closest('[role="row"]') as HTMLElement;
    expect(within(assessment).getByText("v2")).toBeInTheDocument();
    expect(within(assessment).getByText("Edited")).toBeInTheDocument();
    expect(within(assessment).getByText("Shipped prompt changed")).toBeInTheDocument();
    expect(within(assessment).getByText("Gemini 3.5 Flash")).toBeInTheDocument();
    expect(within(assessment).getByText(/· Noor Rahman$/)).toBeInTheDocument();

    const assistant = screen.getByText("Assistant").closest('[role="row"]') as HTMLElement;
    expect(within(assistant).getByText("Shipped")).toBeInTheDocument();
    expect(within(assistant).getByText("Default model")).toBeInTheDocument();
    expect(within(assistant).getByText(/· Shipped$/)).toBeInTheDocument();

    expect(screen.getByText("Model retires in 12 days")).toBeInTheDocument();
    const callChanges = screen.getByText("Call changes").closest('[role="row"]') as HTMLElement;
    expect(within(callChanges).getByText("Model unavailable")).toBeInTheDocument();
    expect(screen.getByText("4 of 4 prompts")).toBeInTheDocument();
  });

  it("narrows by search and by what needs attention", async () => {
    vi.mocked(promptApi.listPrompts).mockResolvedValue(PROMPTS);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Assessment draft");

    await user.click(screen.getByRole("radio", { name: "Needs attention" }));
    expect(screen.queryByText("Assistant")).not.toBeInTheDocument();
    expect(screen.getByText("Call changes")).toBeInTheDocument();
    expect(screen.getByText("3 of 4 prompts")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Search prompts"), "assistant-core");
    expect(screen.getByText("No prompts match. Clear the search or pick another filter.")).toBeInTheDocument();
  });

  it("opens a prompt from its row", async () => {
    vi.mocked(promptApi.listPrompts).mockResolvedValue(PROMPTS);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Open Assistant" }));

    expect(screen.getByTestId("pathname").textContent).toBe("/platform/prompts/assistant-core");
  });

  it("says a refused read was refused, never that there are no prompts", async () => {
    vi.mocked(promptApi.listPrompts).mockRejectedValue(
      new ApiRequestError({ code: "INTERNAL_ERROR", detail: "", status: 500, correlationId: "c" }),
    );
    renderPage();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(/of 0 prompts/)).not.toBeInTheDocument();
    expect(screen.queryByText(/No prompts match/)).not.toBeInTheDocument();
  });

  it("asks a super admin with no second factor to set one up", async () => {
    vi.mocked(promptApi.listPrompts).mockRejectedValue(
      new ApiRequestError({ code: "PLATFORM_TWO_FACTOR_REQUIRED", detail: "", status: 403, correlationId: "c" }),
    );
    renderPage();

    expect(await screen.findByText("Set up two-factor to use the platform console")).toBeInTheDocument();
  });
});
