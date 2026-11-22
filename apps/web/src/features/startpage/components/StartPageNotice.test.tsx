import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser } from "../../../test/fixtures/user";
import type { User } from "../../auth/api/types";
import * as startPageApi from "../api/startPageApi";
import { StartPageNotice } from "./StartPageNotice";

const auth: { user: User | null; reload: () => Promise<User | null> } = { user: null, reload: vi.fn() };
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => auth }));
vi.mock("../api/startPageApi");

const renderNotice = () =>
  render(
    <ToastProvider>
      <MemoryRouter>
        <StartPageNotice />
      </MemoryRouter>
    </ToastProvider>,
  );

describe("StartPageNotice", () => {
  beforeEach(() => vi.clearAllMocks());

  it("says nothing while the start page opens", () => {
    auth.user = aUser({ startPage: { kind: "TASKS", dashboardId: null, projectId: null, section: null, available: true } });
    renderNotice();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("says Home opened in place of a lost start page, links to Settings, and can settle on Home", async () => {
    auth.user = aUser({ startPage: { kind: "DASHBOARD", dashboardId: "d1", projectId: null, section: null, available: false } });
    vi.mocked(startPageApi.saveStartPage).mockResolvedValue({
      kind: "HOME",
      dashboardId: null,
      projectId: null,
      section: null,
      available: true,
    });
    const user = userEvent.setup();
    renderNotice();

    expect(screen.getByText(/no longer available to you, so Uncava opened Home/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Choose another" })).toHaveAttribute("href", "/settings/profile#start-page");

    await user.click(screen.getByRole("button", { name: "Open on Home" }));
    await waitFor(() => expect(startPageApi.saveStartPage).toHaveBeenCalledWith({ kind: "HOME" }));
    expect(auth.reload).toHaveBeenCalled();
  });
});
