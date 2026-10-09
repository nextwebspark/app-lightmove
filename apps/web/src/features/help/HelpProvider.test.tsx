import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../components/ui";
import { aUser, aWorkspace } from "../../test/fixtures/user";
import type { User } from "../auth/api/types";
import * as gettingStartedApi from "../gettingstarted/api/gettingStartedApi";
import { HelpProvider, useHelp } from "./HelpProvider";

let currentUser: User = aUser();
vi.mock("../auth/AuthProvider", () => ({ useAuth: () => ({ user: currentUser }) }));
vi.mock("../gettingstarted/api/gettingStartedApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../gettingstarted/api/gettingStartedApi")>()),
  setDismissed: vi.fn(),
}));

function OpenButton() {
  const { openHelp, hasUnseenNews } = useHelp();
  return (
    <>
      <button type="button" onClick={() => openHelp()}>
        open help
      </button>
      <span data-testid="unseen">{String(hasUnseenNews)}</span>
      <input aria-label="a field" />
    </>
  );
}

const renderAt = (path = "/projects/p1/strategy") =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <ToastProvider>
          <HelpProvider>
            <OpenButton />
          </HelpProvider>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe("Help", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    currentUser = aUser();
  });

  it("opens on ?, but not while typing", async () => {
    const user = userEvent.setup();
    renderAt();

    await user.type(screen.getByLabelText("a field"), "?");
    expect(screen.queryByRole("dialog", { name: "Help" })).not.toBeInTheDocument();

    await user.click(document.body);
    await user.keyboard("?");
    expect(await screen.findByRole("dialog", { name: "Help" })).toBeInTheDocument();
  });

  it("offers the page's help, shortcuts, what's new and a support mail naming the workspace", async () => {
    renderAt();
    await userEvent.click(screen.getByRole("button", { name: "open help" }));

    expect(screen.getByText("Help for this page")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Filter the market/ }).length).toBeGreaterThan(0);
    expect(screen.getByText("Keyboard shortcuts")).toBeInTheDocument();
    expect(screen.getByText("What's new")).toBeInTheDocument();
    const mail = screen.getByRole("link", { name: /Email support@uncava.com/ });
    expect(mail.getAttribute("href")).toContain(encodeURIComponent(currentUser.workspace!.id));
    expect(screen.getByRole("link", { name: /Connect Claude, ChatGPT or Cursor/ })).toHaveAttribute("href", "/docs/mcp");
  });

  it("reads an article in the panel, and searches", async () => {
    const user = userEvent.setup();
    renderAt("/");
    await user.click(screen.getByRole("button", { name: "open help" }));

    await user.type(screen.getByRole("searchbox", { name: "Search help" }), "spreadsheet");
    await user.click(screen.getByRole("button", { name: /Import a spreadsheet/ }));

    expect(screen.getByRole("heading", { name: "Import a spreadsheet" })).toBeInTheDocument();
    expect(screen.getByText(/Download the template/)).toBeInTheDocument();
  });

  it("clears the unread dot once opened", async () => {
    renderAt();
    expect(screen.getByTestId("unseen").textContent).toBe("true");

    await userEvent.click(screen.getByRole("button", { name: "open help" }));

    await waitFor(() => expect(screen.getByTestId("unseen").textContent).toBe("false"));
  });

  it("brings the Getting started card back for staff, and not for a client contact", async () => {
    vi.mocked(gettingStartedApi.setDismissed).mockResolvedValue({ dismissed: false, focusProjectId: null, steps: [] });
    const { unmount } = renderAt();
    await userEvent.click(screen.getByRole("button", { name: "open help" }));
    await userEvent.click(screen.getByRole("button", { name: /Show getting started/ }));
    await waitFor(() => expect(gettingStartedApi.setDismissed).toHaveBeenCalledWith(false));
    unmount();

    currentUser = aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) });
    renderAt();
    await userEvent.click(screen.getByRole("button", { name: "open help" }));
    expect(screen.queryByRole("button", { name: /Show getting started/ })).not.toBeInTheDocument();
  });
});
