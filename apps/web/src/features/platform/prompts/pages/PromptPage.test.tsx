import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import type { PlatformAction } from "../../../auth/api/types";
import * as promptApi from "../api/promptApi";
import type { PromotionGate, PromptDetail } from "../api/types";
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

const renderPage = (
  actions: PlatformAction[] = ["PROMPT_MANAGE", "PROMPT_PROMOTE"],
  search = "",
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) => {
  held.actions = actions;
  return render(
    <MemoryRouter initialEntries={[`/platform/prompts/candidate-scorecard-draft${search}`]}>
      <QueryClientProvider client={client}>
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

const gate = (overrides: Partial<PromotionGate> = {}): PromotionGate => ({
  state: "PASSED",
  runId: "r42",
  runNumber: 42,
  finishedAt: "2026-11-19T16:26:00Z",
  cases: 12,
  repeats: 3,
  checksBetter: 5,
  checksSame: 3,
  checksWorse: 0,
  regressedCases: 1,
  failedChecks: [],
  datasetId: "d1",
  judged: null,
  ...overrides,
});

/** The fixture's prompt, with what the evals say about its newest version against the live one. */
const gated = (newest: PromotionGate): PromptDetail => ({
  ...detail(),
  versions: detail().versions.map((entry) => (entry.version === 3 ? { ...entry, gate: newest } : entry)),
});

const openPutLive = async (user: ReturnType<typeof userEvent.setup>) => {
  await editor();
  await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[0]);
  await user.click(await screen.findByRole("button", { name: "Put version 3 live" }));
  return screen.findByRole("dialog", { name: "Put v3 live" });
};

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

    expect(screen.getByRole("button", { name: "Save as new version" })).toBeDisabled();

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

  it("refuses a decimal comma rather than reading 0,2 as 2", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByRole("textbox", { name: "Temperature" }), "0,2");
    await user.type(screen.getByRole("textbox", { name: "Max output tokens" }), "1.5");
    await user.type(screen.getByPlaceholderText("What changed and why"), "Cooler");
    await user.click(screen.getByRole("button", { name: "Save as new version" }));

    expect(await screen.findByText("Temperature must be a number, with a point for decimals")).toBeInTheDocument();
    expect(screen.getByText("Max output tokens must be a whole number")).toBeInTheDocument();
    expect(promptApi.savePromptVersion).not.toHaveBeenCalled();
  });

  it("holds no range of its own under Default model when the configured model is not listed", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(detail({ defaultModelId: "gemini-unlisted" }));
    vi.mocked(promptApi.savePromptVersion).mockResolvedValue(detail());
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByRole("combobox", { name: "Model" }), "");
    expect(screen.queryByText("0 to 1")).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Temperature" }), "1.5");
    await user.type(screen.getByPlaceholderText("What changed and why"), "Left to the server");
    await user.click(screen.getByRole("button", { name: "Save as new version" }));

    await waitFor(() => expect(promptApi.savePromptVersion).toHaveBeenCalled());
    expect(vi.mocked(promptApi.savePromptVersion).mock.calls[0][1].settings.temperature).toBe(1.5);
  });

  it("puts a model's refusal under the model, and clears it when another model is picked", async () => {
    vi.mocked(promptApi.savePromptVersion).mockRejectedValue(
      refusal("PROMPT_MODEL_UNAVAILABLE", { modelId: "Gemini 3.8 Flash cannot answer this prompt." }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByRole("combobox", { name: "Model" }), "gemini-3.8-flash");
    await user.type(screen.getByPlaceholderText("What changed and why"), "Newer model");
    await user.click(screen.getByRole("button", { name: "Save as new version" }));
    expect(await screen.findByText("Gemini 3.8 Flash cannot answer this prompt.")).toBeInTheDocument();

    await user.selectOptions(screen.getByRole("combobox", { name: "Model" }), "gemini-3.5-flash");
    expect(screen.queryByText("Gemini 3.8 Flash cannot answer this prompt.")).not.toBeInTheDocument();
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
    expect(await screen.findByText("Assessment draft now runs on version 3 in every workspace")).toBeInTheDocument();
    expect(screen.getByText("Live: v3")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("puts a version live with no reason asked once a holdout run found it no worse", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(gated(gate()));
    vi.mocked(promptApi.putPromptLive).mockResolvedValue(detail({ prompt: overview({ liveVersion: 3 }) }));
    const user = userEvent.setup();
    renderPage();

    const dialog = await openPutLive(user);

    expect(within(dialog).getByText("No worse")).toBeInTheDocument();
    expect(within(dialog).getByText("Run R-0042 · holdout · 19 Nov 2026")).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "12 cases, 3 repeats each. Better on 5 checks, the same on 3, worse on none. One case regressed inside the tolerance.",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Put live" }));

    await waitFor(() => expect(promptApi.putPromptLive).toHaveBeenCalledWith("candidate-scorecard-draft", 3, null));
  });

  it("asks why over a run that gave no result, and sends to the rubric that would make it count", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(
      gated(
        gate({
          state: "NO_RESULT",
          runNumber: 39,
          checksBetter: 0,
          checksSame: 0,
          checksWorse: 0,
          datasetId: "d5",
          judged: { counted: false, state: "UNLABELLED", criteria: 3, forCandidate: 1, forBaseline: [] },
        }),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    const dialog = await openPutLive(user);

    expect(within(dialog).getByText("Run R-0039 on the holdout cases gave no result")).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "Assessment draft has no checks, and its judged criteria do not count until a person has labelled 20 pairs and the judge has been checked against them. Nothing yet shows this version is no worse than the live one.",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Open the rubric" })).toHaveAttribute(
      "href",
      "/platform/evals/datasets/d5?view=rubric",
    );
    expect(within(dialog).getByRole("textbox", { name: "Why put it live anyway" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Put live" })).toBeDisabled();
  });

  it("names what the judge found beside the checks, and warns when the prompt is the judge itself", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue({
      ...gated(gate({ judged: { counted: true, state: "COUNTS", criteria: 4, forCandidate: 2, forBaseline: [] } })),
      prompt: overview({ id: "eval-judge", name: "Eval judge" }),
    });
    const user = userEvent.setup();
    renderPage();

    const dialog = await openPutLive(user);

    expect(
      within(dialog).getByText(/The judge prefers the candidate on 2 of the 4 judged criteria and the live version on none\./),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText(/This prompt is the judge\. Once its live version changes, every rubric's judged criteria are shown only/),
    ).toBeInTheDocument();
  });

  it("asks why over a run that found the version worse, naming the checks it fell on", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(
      gated(
        gate({
          state: "FAILED",
          runNumber: 41,
          finishedAt: "2026-11-13T11:02:00Z",
          failedChecks: [
            { label: "Score inside the labelled band", candidate: 75, baseline: 83 },
            { label: "Self-report alone stays at 7 or below", candidate: 83, baseline: 92 },
          ],
        }),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    const dialog = await openPutLive(user);

    expect(within(dialog).getByText("Regressed")).toBeInTheDocument();
    expect(within(dialog).getByText("Run R-0041 · holdout · 13 Nov 2026")).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "Worse than the live version on two checks: score inside the labelled band (75% against 83%) and self-report alone stays at 7 or below (83% against 92%).",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("textbox", { name: "Why put it live anyway" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Put live" })).toBeDisabled();
  });

  it("says a version has no holdout run, asks why, and points at Evals", async () => {
    const user = userEvent.setup();
    renderPage();

    const dialog = await openPutLive(user);

    expect(within(dialog).getByText("This version has no finished run on the holdout cases")).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Run an eval first" })).toHaveAttribute(
      "href",
      "/platform/evals?tab=runs",
    );
    expect(within(dialog).getByRole("textbox", { name: "Why put it live anyway" })).toBeInTheDocument();
  });

  it("shows the reason field's refusal when the version serving changed under a passed run", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(gated(gate()));
    vi.mocked(promptApi.putPromptLive).mockRejectedValue(
      refusal("VALIDATION_FAILED", { reason: "Say why this version should serve" }),
    );
    const user = userEvent.setup();
    renderPage();

    const dialog = await openPutLive(user);
    await user.click(within(dialog).getByRole("button", { name: "Put live" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Say why this version should serve");
  });

  it("reads the prompt again when a reason is demanded under a passed run, and asks for it on what the evals now say", async () => {
    vi.mocked(promptApi.getPrompt)
      .mockResolvedValueOnce(gated(gate()))
      .mockResolvedValue(
        gated(
          gate({
            state: "FAILED",
            runId: "r43",
            runNumber: 43,
            failedChecks: [{ label: "Score inside the labelled band", candidate: 75, baseline: 83 }],
          }),
        ),
      );
    vi.mocked(promptApi.putPromptLive).mockRejectedValue(
      refusal("VALIDATION_FAILED", { reason: "Say why this version should serve" }),
    );
    const user = userEvent.setup();
    renderPage();

    const stale = await openPutLive(user);
    expect(within(stale).queryByRole("textbox")).not.toBeInTheDocument();
    await user.click(within(stale).getByRole("button", { name: "Put live" }));

    const reason = await screen.findByRole("textbox", { name: "Why put it live anyway" });
    const dialog = screen.getByRole("dialog", { name: "Put v3 live" });
    expect(within(dialog).getByText("Regressed")).toBeInTheDocument();

    vi.mocked(promptApi.putPromptLive).mockResolvedValue(detail({ prompt: overview({ liveVersion: 3 }) }));
    await user.type(reason, "Reads better");
    await user.click(within(dialog).getByRole("button", { name: "Put live" }));

    await waitFor(() =>
      expect(promptApi.putPromptLive).toHaveBeenLastCalledWith("candidate-scorecard-draft", 3, "Reads better"),
    );
  });

  it("opens Put live at once from a run's link to its candidate", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(gated(gate()));
    const user = userEvent.setup();
    renderPage(["PROMPT_MANAGE", "PROMPT_PROMOTE"], "?version=3&putLive=1");

    const dialog = await screen.findByRole("dialog", { name: "Put v3 live" });
    expect(within(dialog).getByText("No worse")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Put version 3 live" })).toBeInTheDocument();
  });

  it("reads the prompt again before the linked dialog opens, so a run that just ended is the one it rests on", async () => {
    // The prompt as it was read before the run: no run yet for its newest version.
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(promptApi.PROMPT_KEY("candidate-scorecard-draft"), detail(), { updatedAt: 0 });
    vi.mocked(promptApi.getPrompt).mockResolvedValue(gated(gate()));
    renderPage(["PROMPT_MANAGE", "PROMPT_PROMOTE"], "?version=3&putLive=1", client);

    const dialog = await screen.findByRole("dialog", { name: "Put v3 live" });

    expect(within(dialog).getByText("No worse")).toBeInTheDocument();
    expect(within(dialog).queryByText("This version has no finished run on the holdout cases")).not.toBeInTheDocument();
    expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("keeps the linked dialog and the reason typed in it when the prompt is read again", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const user = userEvent.setup();
    renderPage(["PROMPT_MANAGE", "PROMPT_PROMOTE"], "?version=3&putLive=1", client);

    const dialog = await screen.findByRole("dialog", { name: "Put v3 live" });
    const reason = within(dialog).getByRole("textbox", { name: "Why put it live anyway" });
    await user.type(reason, "Reads better");

    await client.invalidateQueries({ queryKey: promptApi.PROMPT_KEY("candidate-scorecard-draft") });

    expect(promptApi.getPrompt).toHaveBeenCalledTimes(2);
    expect(reason).toBeInTheDocument();
    expect(reason).toHaveValue("Reads better");
  });

  it("asks why on a rollback whatever the evals said, and draws no check", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue({
      ...detail(),
      versions: detail().versions.map((entry) => (entry.version === 1 ? { ...entry, gate: gate() } : entry)),
    });
    const user = userEvent.setup();
    renderPage();
    await editor();

    await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[2]);
    await user.click(await screen.findByRole("button", { name: "Roll back to version 1" }));

    const dialog = await screen.findByRole("dialog", { name: "Roll back to v1" });
    expect(within(dialog).queryByText("No worse")).not.toBeInTheDocument();
    expect(within(dialog).getByRole("textbox", { name: "Why roll back" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Roll back" })).toBeDisabled();
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

  it("keeps a failed run's error with the version that ran", async () => {
    vi.mocked(promptApi.tryPromptVersion).mockRejectedValue(refusal("AI_UNAVAILABLE"));
    const user = userEvent.setup();
    renderPage();
    await editor();

    await user.click(screen.getByRole("tab", { name: "Try it" }));
    await user.click(screen.getByRole("button", { name: "Run v2" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    await user.click(within(screen.getByRole("region", { name: "Version history" })).getAllByRole("button")[0]);
    expect(await screen.findByRole("button", { name: "Run v3" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says when the live version's model can no longer answer it, and that the default model is", async () => {
    vi.mocked(promptApi.getPrompt).mockResolvedValue(
      detail({
        prompt: overview({ modelId: "gemini-1.0-pro", modelName: "gemini-1.0-pro", modelRetiresInDays: null, modelUnavailable: true }),
        versions: [
          version({ version: 2, origin: "EDITED", live: true, provider: "VERTEX_GEMINI", modelId: "gemini-1.0-pro", modelName: "gemini-1.0-pro" }),
        ],
      }),
    );
    renderPage();
    await editor();

    expect(screen.getByRole("note")).toHaveTextContent(
      "Version 2 names gemini-1.0-pro, which is retired, no longer offered or cannot answer this prompt.",
    );
    expect(screen.getByText("gemini-1.0-pro is no longer offered. The default model answers this version.")).toBeInTheDocument();
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
