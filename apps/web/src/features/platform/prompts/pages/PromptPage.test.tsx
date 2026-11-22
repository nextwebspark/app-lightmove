import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import type { PlatformAction } from "../../../auth/api/types";
import * as promptApi from "../api/promptApi";
import type { PromptDetail } from "../api/types";
import PromptPage from "./PromptPage";
import { detail, model, overview, version } from "./promptFixtures";

vi.mock("../api/promptApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/promptApi")>()),
  getPrompt: vi.fn(),
  getPromptBody: vi.fn(),
  listAiModels: vi.fn(),
  savePromptVersion: vi.fn(),
  tryPromptVersion: vi.fn(),
  putPromptLive: vi.fn(),
}));

const held = vi.hoisted(() => ({ actions: [] as string[] }));
vi.mock("../../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { platformActions: held.actions } }),
}));

const BODIES: Record<number, string> = {
  1: "Score each competency.\nBe brief.",
  2: "Score each competency.\nBe brief.\nQuote the evidence.",
  3: "Score each competency.\nBe specific.\nQuote the evidence and name its source.",
};

const renderPage = (actions: PlatformAction[] = ["PROMPT_MANAGE", "PROMPT_PROMOTE"]) => {
  held.actions = actions;
  return render(
    <MemoryRouter initialEntries={["/platform/prompts/candidate-scorecard-draft"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/platform/prompts/:promptId" element={<PromptPage />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
};

const refusal = (code: string, fieldErrors: Record<string, string> = {}) =>
  new ApiRequestError({ code, detail: "", status: 400, correlationId: "c", fieldErrors });

const editor = () => screen.findByRole("textbox", { name: "Prompt text" });

describe("PromptPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(promptApi.getPrompt).mockResolvedValue(detail());
    vi.mocked(promptApi.getPromptBody).mockImplementation((_id, number) =>
      Promise.resolve({ version: number, body: BODIES[number] }),
    );
    vi.mocked(promptApi.listAiModels).mockResolvedValue([
      model(),
      model({ modelId: "gemini-3.8-flash", displayName: "Gemini 3.8 Flash", retiresOn: "2027-09-02", retiresInDays: 285 }),
      model({ modelId: "gemini-0-plain", displayName: "Gemini Plain", capabilities: ["TOOL_CALLING"] }),
      model({ modelId: "gemini-3.1-flash-lite", displayName: "Gemini 3.1 Flash-Lite", retiresInDays: 40, retiringSoon: true }),
    ]);
  });

  it("opens on the live version, with its history, its text and what the feature sends beside it", async () => {
    renderPage();

    expect(await editor()).toHaveValue(BODIES[2]);
    expect(screen.getByRole("heading", { name: "Assessment draft" })).toBeInTheDocument();
    expect(screen.getByText("Live: v2")).toBeInTheDocument();
    expect(screen.getByText("Version 2 is live")).toBeInTheDocument();
    const history = screen.getByRole("region", { name: "Version history" });
    expect(within(history).getAllByRole("button")).toHaveLength(3);
    expect(within(history).getByText("Shipped with a release.")).toBeInTheDocument();
    expect(within(history).getByText("Gemini 3.8 Flash")).toBeInTheDocument();
    expect(screen.getByLabelText("The message sent with these instructions")).toHaveTextContent("{profile}");
    expect(screen.getByRole("option", { name: /Gemini Plain .* no structured answer/ })).toBeDisabled();
    expect(screen.getByRole("option", { name: "Default model (Gemini 3.5 Flash)" })).toBeInTheDocument();
  });

  it("saves a draft as a new version with its model and settings, and opens it", async () => {
    const saved: PromptDetail = detail({
      versions: [version({ version: 4, origin: "EDITED", changeNote: "Warmer", author: "Noor Rahman" }), ...detail().versions],
    });
    vi.mocked(promptApi.savePromptVersion).mockResolvedValue(saved);
    const user = userEvent.setup();
    renderPage();

    await user.type(await editor(), "\nBe kind.");
    await user.selectOptions(screen.getByRole("combobox", { name: "Model" }), "gemini-3.8-flash");
    await user.type(screen.getByRole("textbox", { name: "Temperature" }), "0.3");
    expect(screen.getByText("Differs from default")).toBeInTheDocument();
    expect(screen.getByText(/Above 0, answers vary/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save as new version" }));
    expect(await screen.findByText("Add a change note first")).toBeInTheDocument();
    expect(promptApi.savePromptVersion).not.toHaveBeenCalled();

    await user.type(screen.getByPlaceholderText("What changed and why"), "Warmer");
    await user.click(screen.getByRole("button", { name: "Save as new version" }));

    await waitFor(() =>
      expect(promptApi.savePromptVersion).toHaveBeenCalledWith("candidate-scorecard-draft", {
        body: `${BODIES[2]}\nBe kind.`,
        provider: "VERTEX_GEMINI",
        modelId: "gemini-3.8-flash",
        settings: { temperature: 0.3, thinkingBudget: null, maxOutputTokens: null },
        changeNote: "Warmer",
      }),
    );
    expect(await screen.findByText("Saved as version 4. Nothing serves it until it is put live")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Put version 4 live" })).toBeInTheDocument();
  });

  it("holds a setting to the model's range before anything is sent", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByRole("textbox", { name: "Temperature" }), "1.5");
    await user.type(screen.getByPlaceholderText("What changed and why"), "Too warm");
    await user.click(screen.getByRole("button", { name: "Save as new version" }));

    expect(await screen.findByText("Temperature must be 0 to 1")).toBeInTheDocument();
    expect(promptApi.savePromptVersion).not.toHaveBeenCalled();
  });

  it("shows the server's contract error under the text, and clears it on the next edit", async () => {
    vi.mocked(promptApi.savePromptVersion).mockRejectedValue(
      refusal("PROMPT_CONTRACT_BROKEN", {
        body: "This prompt must keep {hiring}. It is missing from the text, so the version was not saved.",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await editor(), " ");
    await user.type(screen.getByPlaceholderText("What changed and why"), "Dropped a variable");
    await user.click(screen.getByRole("button", { name: "Save as new version" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This prompt must keep {hiring}.");

    await user.type(await editor(), "x");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("puts a newer version live with a reason, naming the model change", async () => {
    const live = detail({
      prompt: overview({ liveVersion: 3, modelId: "gemini-3.8-flash", modelName: "Gemini 3.8 Flash" }),
      versions: detail().versions.map((entry) => ({ ...entry, live: entry.version === 3 })),
    });
    vi.mocked(promptApi.putPromptLive).mockResolvedValue(live);
    const user = userEvent.setup();
    renderPage();
    await editor();

    await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[0]);
    await user.click(await screen.findByRole("button", { name: "Put version 3 live" }));

    const dialog = await screen.findByRole("dialog", { name: "Put v3 live" });
    expect(dialog).toHaveTextContent("Assessment draft goes from v2 to v3 for every workspace, within seconds.");
    expect(dialog).toHaveTextContent("Gemini 3.5 Flash→Gemini 3.8 Flash");
    expect(dialog).toHaveTextContent("Settings → Data & retention");
    expect(within(dialog).getByRole("button", { name: "Put live" })).toBeDisabled();

    await user.type(within(dialog).getByRole("textbox", { name: "Why put it live anyway" }), "Reads better");
    await user.click(within(dialog).getByRole("button", { name: "Put live" }));

    await waitFor(() =>
      expect(promptApi.putPromptLive).toHaveBeenCalledWith("candidate-scorecard-draft", 3, "Reads better"),
    );
    expect(await screen.findByText("Assessment draft now runs on version 3 on every instance")).toBeInTheDocument();
    expect(screen.getByText("Live: v3")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("rolls back to an earlier version from its history, through the same dialog", async () => {
    vi.mocked(promptApi.putPromptLive).mockResolvedValue(detail({ prompt: overview({ liveVersion: 1, origin: "BUNDLED" }) }));
    const user = userEvent.setup();
    renderPage();
    await editor();

    await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[2]);
    await user.click(await screen.findByRole("button", { name: "Roll back to version 1" }));

    const dialog = await screen.findByRole("dialog", { name: "Roll back to v1" });
    await user.type(within(dialog).getByRole("textbox", { name: "Why roll back" }), "Answers got longer");
    await user.click(within(dialog).getByRole("button", { name: "Roll back" }));

    await waitFor(() =>
      expect(promptApi.putPromptLive).toHaveBeenCalledWith("candidate-scorecard-draft", 1, "Answers got longer"),
    );
  });

  it("offers no way to put a version live to someone who may only save", async () => {
    const user = userEvent.setup();
    renderPage(["PROMPT_MANAGE"]);
    await editor();

    await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[0]);

    expect(await screen.findByText("You can save versions, not put them live")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Put version/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Roll back/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save as new version" })).toBeInTheDocument();
  });

  it("compares two versions: the model, the settings and the changed lines", async () => {
    const user = userEvent.setup();
    renderPage();
    await editor();

    await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[0]);
    await user.click(screen.getByRole("tab", { name: "Compare versions" }));

    expect(screen.getByRole("combobox", { name: "From version" })).toHaveValue("2");
    expect(screen.getByRole("combobox", { name: "To version" })).toHaveValue("3");
    const changes = await screen.findByLabelText("Changes to the text");
    expect(changes).toHaveTextContent("−Be brief.");
    expect(changes).toHaveTextContent("+Be specific.");
    expect(screen.getAllByText("Default (0)").length).toBeGreaterThan(0);
  });

  it("runs the open version on the synthetic input and shows the answer both ways", async () => {
    vi.mocked(promptApi.tryPromptVersion).mockResolvedValue({
      version: 2,
      rawAnswer: '{"scores":[{"id":"T1","score":8,"evidence":"Ran the 2023 refinancing herself"}]}',
      parsedAnswer: { scores: [{ id: "T1", score: 8, evidence: "Ran the 2023 refinancing herself" }] },
      model: "gemini-3.5-flash",
      inputTokens: 3480,
      outputTokens: 412,
      finish: "STOP",
      tookMillis: 2100,
    });
    const user = userEvent.setup();
    renderPage();
    await editor();

    await user.click(screen.getByRole("tab", { name: "Try it" }));
    expect(screen.getByText("Synthetic input · Strong direct evidence")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Run v2" }));

    expect(await screen.findByText("Ran the 2023 refinancing herself")).toBeInTheDocument();
    expect(screen.getByLabelText("Answer as the model returned it")).toHaveTextContent('"score":8');
    expect(screen.getByText("Answered in 2.1 s · 3,480 tokens in, 412 out · finished normally")).toBeInTheDocument();
    expect(promptApi.tryPromptVersion).toHaveBeenCalledWith("candidate-scorecard-draft", 2);
  });

  it("draws no Try it for a prompt that has none", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(detail({ trial: null, userMessageTemplate: null }));
    renderPage();
    await editor();

    expect(screen.queryByRole("tab", { name: "Try it" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("The message sent with these instructions")).toHaveTextContent(
      "The material the feature sends: built in code, after contact details are removed.",
    );
  });
});
