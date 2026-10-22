import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as notetakerApi from "../../outreach/api/notetakerApi";
import { MyNotetakerRow } from "./MyNotetakerRow";
import { NotetakerCard } from "./NotetakerCard";

vi.mock("../../outreach/api/notetakerApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../outreach/api/notetakerApi")>()),
  getWorkspaceNotetaker: vi.fn(),
  setWorkspaceNotetaker: vi.fn(),
  setWorkspaceNotetakerBackup: vi.fn(),
  getMyNotetaker: vi.fn(),
  setMyNotetaker: vi.fn(),
}));

function renderWith(node: ReactNode) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>{node}</ToastProvider>
    </QueryClientProvider>,
  );
}

describe("Uncava Notetaker in Settings", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(notetakerApi.getWorkspaceNotetaker).mockResolvedValue({
      offered: true,
      enabled: true,
      platforms: ["GOOGLE_MEET"],
      backupOffered: false,
      backupEnabled: false,
    });
  });

  it("switches the notetaker off for the workspace, naming the calls it joins", async () => {
    vi.mocked(notetakerApi.setWorkspaceNotetaker).mockResolvedValue({
      offered: true,
      enabled: false,
      platforms: ["GOOGLE_MEET"],
      backupOffered: false,
      backupEnabled: false,
    });
    renderWith(<NotetakerCard />);

    expect(await screen.findByText("Joins Google Meet calls with executives.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("switch", { name: "Uncava Notetaker for the workspace" }));

    await waitFor(() => expect(notetakerApi.setWorkspaceNotetaker).toHaveBeenCalledWith(false));
    expect(await screen.findByText("Off for the workspace")).toBeInTheDocument();
    expect(screen.getByText("The notetaker is off for the workspace")).toBeInTheDocument();
  });

  it("offers the backup recorder off, naming who receives the call, and turns it on only when asked", async () => {
    vi.mocked(notetakerApi.getWorkspaceNotetaker).mockResolvedValue({
      offered: true,
      enabled: true,
      platforms: ["GOOGLE_MEET"],
      backupOffered: true,
      backupEnabled: false,
    });
    vi.mocked(notetakerApi.setWorkspaceNotetakerBackup).mockResolvedValue({
      offered: true,
      enabled: true,
      platforms: ["GOOGLE_MEET", "MICROSOFT_TEAMS", "ZOOM"],
      backupOffered: true,
      backupEnabled: true,
    });
    renderWith(<NotetakerCard />);

    const backup = await screen.findByRole("switch", { name: "Backup recorder for the workspace" });
    expect(backup).not.toBeChecked();
    expect(screen.getByText(/pass through Recall\.ai, a third party/)).toBeInTheDocument();
    await userEvent.click(backup);

    await waitFor(() => expect(notetakerApi.setWorkspaceNotetakerBackup).toHaveBeenCalledWith(true));
    expect(await screen.findByText("The backup recorder is on")).toBeInTheDocument();
  });

  it("offers no backup recorder where the deployment has none", async () => {
    renderWith(<NotetakerCard />);

    expect(await screen.findByText("Joins Google Meet calls with executives.")).toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "Backup recorder for the workspace" })).not.toBeInTheDocument();
  });

  it("draws no card where the deployment sends no notetaker", async () => {
    vi.mocked(notetakerApi.getWorkspaceNotetaker).mockResolvedValue({
      offered: false,
      enabled: true,
      platforms: [],
      backupOffered: false,
      backupEnabled: false,
    });
    renderWith(<NotetakerCard />);

    await waitFor(() => expect(notetakerApi.getWorkspaceNotetaker).toHaveBeenCalled());
    expect(screen.queryByRole("region", { name: "Uncava Notetaker" })).not.toBeInTheDocument();
  });

  it("saves the consultant's own switch the moment it is flipped", async () => {
    vi.mocked(notetakerApi.getMyNotetaker).mockResolvedValue({ offered: true, workspaceEnabled: true, enabled: true });
    vi.mocked(notetakerApi.setMyNotetaker).mockResolvedValue({ offered: true, workspaceEnabled: true, enabled: false });
    renderWith(<MyNotetakerRow />);

    const toggle = await screen.findByRole("switch", { name: "Uncava Notetaker on my calls" });
    expect(toggle).toHaveAttribute("aria-checked", "true");
    await userEvent.click(toggle);

    await waitFor(() => expect(notetakerApi.setMyNotetaker).toHaveBeenCalledWith(false));
    expect(await screen.findByText("The notetaker won't join your calls")).toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-checked", "false");
  });

  it("greys the consultant's switch while the workspace has the notetaker off", async () => {
    vi.mocked(notetakerApi.getMyNotetaker).mockResolvedValue({ offered: true, workspaceEnabled: false, enabled: true });
    renderWith(<MyNotetakerRow />);

    const toggle = await screen.findByRole("switch", { name: "Uncava Notetaker on my calls" });
    expect(toggle).toBeDisabled();
    expect(toggle).toHaveAttribute("aria-checked", "false");
    expect(
      screen.getByText("Your workspace has the notetaker turned off. An admin turns it on in Integrations."),
    ).toBeInTheDocument();
  });
});
