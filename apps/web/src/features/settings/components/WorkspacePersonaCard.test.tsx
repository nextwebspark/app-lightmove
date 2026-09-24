import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { WorkspaceDetail, WorkspacePersona } from "../../workspace/api/types";
import { WorkspacePersonaCard } from "./WorkspacePersonaCard";

vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  updatePersona: vi.fn(),
}));

const persona: WorkspacePersona = {
  summary: null,
  sectors: ["retail"],
  competitors: [],
  geographies: [],
  notes: null,
};

beforeEach(() => {
  vi.mocked(workspaceApi.updatePersona).mockImplementation(async (saved) => ({
    persona: saved,
  }) as WorkspaceDetail);
});

describe("WorkspacePersonaCard", () => {
  it("saves the whole persona, the seeded sector included", async () => {
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ToastProvider>
          <WorkspacePersonaCard persona={persona} />
        </ToastProvider>
      </QueryClientProvider>,
    );

    await user.type(screen.getByPlaceholderText(/Board and C-suite search/), "Gulf C-suite search");
    await user.type(screen.getByLabelText("Add a competitor"), "Korn Ferry{Enter}");
    await user.type(screen.getByLabelText("Add a geography"), "GCC{Enter}");
    await user.click(screen.getByRole("button", { name: "Save persona" }));

    await waitFor(() => expect(workspaceApi.updatePersona).toHaveBeenCalled());
    expect(workspaceApi.updatePersona).toHaveBeenCalledWith({
      summary: "Gulf C-suite search",
      sectors: ["retail"],
      competitors: ["Korn Ferry"],
      geographies: ["GCC"],
      notes: null,
    });
  });
});
