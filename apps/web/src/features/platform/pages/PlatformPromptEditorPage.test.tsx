import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as platformConfigApi from "../api/platformConfigApi";
import type { PlatformPromptDetail } from "../api/types";
import { PlatformPromptEditorPage } from "./PlatformPromptEditorPage";

vi.mock("../api/platformConfigApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/platformConfigApi")>()),
  getPrompt: vi.fn(),
  savePrompt: vi.fn(),
  restorePromptVersion: vi.fn(),
  revertPrompt: vi.fn(),
  refreshPlatformConfig: vi.fn(),
}));

const CONFIG = { currentVersion: 7, loadedVersion: 7, loadedAt: null };

const shipped: PlatformPromptDetail = {
  id: "assistant-core",
  label: "Assistant",
  resource: "prompts/assistant-core.st",
  variables: ["brief", "hiring"],
  shippedBody: "You help.\n{hiring}\n{brief}",
  activeBody: null,
  activeVersion: null,
  versions: [],
  config: CONFIG,
};

const changed: PlatformPromptDetail = {
  ...shipped,
  activeBody: "You help warmly.\n{hiring}\n{brief}",
  activeVersion: 2,
  versions: [
    { version: 2, active: true, createdByName: "Lina Haddad", createdAt: "2026-10-29T10:00:00Z" },
    { version: 1, active: false, createdByName: "Lina Haddad", createdAt: "2026-10-21T10:00:00Z" },
  ],
};

function renderAt(promptId: string) {
  return render(
    <MemoryRouter initialEntries={[`/settings/platform-prompts/${promptId}`]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route path="/settings/platform-prompts/:promptId" element={<PlatformPromptEditorPage />} />
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("PlatformPromptEditorPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(platformConfigApi.getPrompt).mockResolvedValue(shipped);
  });

  it("opens on the shipped text with its placeholders named, and offers no revert", async () => {
    renderAt("assistant-core");

    expect(await screen.findByLabelText("Prompt text")).toHaveValue("You help.\n{hiring}\n{brief}");
    expect(screen.getByText("Shipped", { selector: "span.rounded-full" })).toBeInTheDocument();
    expect(screen.getByText("Keep these placeholders exactly as written: {brief} {hiring}")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Revert to shipped" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("refuses a draft that drops a placeholder before sending it", async () => {
    renderAt("assistant-core");
    const editor = await screen.findByLabelText("Prompt text");

    await userEvent.clear(editor);
    await userEvent.type(editor, "You help. {{hiring}");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Put back {brief}")).toBeInTheDocument();
    expect(platformConfigApi.savePrompt).not.toHaveBeenCalled();
  });

  it("saves against the version it opened on and names the version now in use", async () => {
    vi.mocked(platformConfigApi.savePrompt).mockResolvedValue(changed);
    renderAt("assistant-core");
    const editor = await screen.findByLabelText("Prompt text");

    await userEvent.type(editor, " Be brief.");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(platformConfigApi.savePrompt).toHaveBeenCalledWith(
        "assistant-core",
        "You help.\n{hiring}\n{brief} Be brief.",
        null,
      ),
    );
    expect(await screen.findByText("Version 2 is in use from the next AI call")).toBeInTheDocument();
    expect(screen.getByLabelText("Prompt text")).toHaveValue(changed.activeBody);
  });

  it("shows the server's placeholder refusal under the editor", async () => {
    vi.mocked(platformConfigApi.savePrompt).mockRejectedValue(
      new ApiRequestError({
        code: "PLATFORM_PROMPT_VARIABLES_CHANGED",
        detail: "A prompt must keep exactly the placeholders it shipped with",
        status: 400,
        correlationId: "ref",
        missing: [],
        added: ["tone"],
      }),
    );
    renderAt("assistant-core");
    const editor = await screen.findByLabelText("Prompt text");

    await userEvent.type(editor, " Be brief.");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Take out {tone}")).toBeInTheDocument();
  });

  it("lists the saved versions, restores an earlier one and reverts to the shipped prompt", async () => {
    vi.mocked(platformConfigApi.getPrompt).mockResolvedValue(changed);
    vi.mocked(platformConfigApi.restorePromptVersion).mockResolvedValue({ ...changed, activeVersion: 3 });
    vi.mocked(platformConfigApi.revertPrompt).mockResolvedValue(shipped);
    renderAt("assistant-core");
    const versions = await screen.findByRole("region", { name: "Versions" });

    expect(within(versions).getByText("In use")).toBeInTheDocument();
    await userEvent.click(within(versions).getByRole("button", { name: "Restore" }));
    await waitFor(() => expect(platformConfigApi.restorePromptVersion).toHaveBeenCalledWith("assistant-core", 1));
    expect(await screen.findByText("Version 1 restored as version 3")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Revert to shipped" }));
    await waitFor(() => expect(platformConfigApi.revertPrompt).toHaveBeenCalledWith("assistant-core"));
    expect(await screen.findByText("Back to the shipped prompt")).toBeInTheDocument();
  });
});
