import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { HiringPersona, WorkspaceDetail } from "../../workspace/api/types";
import { WorkspacePersonaCard } from "./WorkspacePersonaCard";

vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  updatePersona: vi.fn(),
}));

let currentUser: User = aUser();

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: currentUser }),
}));

const persona: HiringPersona = {
  summary: null,
  sectors: ["retail"],
  competitors: [],
  geographies: [],
  notes: null,
};

beforeEach(() => {
  currentUser = aUser();
  vi.mocked(workspaceApi.updatePersona).mockImplementation(async (saved) => ({
    persona: saved,
  }) as WorkspaceDetail);
});

const renderCard = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ToastProvider>
        <WorkspacePersonaCard persona={persona} />
      </ToastProvider>
    </QueryClientProvider>,
  );

describe("WorkspacePersonaCard", () => {
  it("tells an in-house firm its persona is the parent its business units fall back to", () => {
    renderCard();

    expect(screen.getByText(/reads it as the parent company/)).toBeInTheDocument();
  });

  it("tells an agency only that it tailors research", () => {
    currentUser = aUser({ workspace: aWorkspace({ mode: "AGENCY" }) });
    renderCard();

    expect(screen.getByText(/uses it to tailor research to you/)).toBeInTheDocument();
    expect(screen.queryByText(/business unit/)).not.toBeInTheDocument();
  });

  it("saves the whole persona, the seeded sector included", async () => {
    const user = userEvent.setup();
    renderCard();

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
