import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as feedbackApi from "../api/feedbackApi";
import type { FeedbackContext } from "../api/types";
import { FeedbackPanel } from "./FeedbackPanel";

vi.mock("../api/feedbackApi");
vi.mock("../../auth/api/authApi");

const CONTEXT: FeedbackContext = {
  pageUrl: "/projects/abc",
  userAgent: "vitest",
  viewport: "1440x900",
  screenSize: "1440x900",
  devicePixelRatio: "1",
  language: "en",
  timezone: "Asia/Dubai",
  theme: "light",
  reportedAt: "2026-09-01T09:00:00.000Z",
};

/**
 * The form a tester fills in. Its job is to be fast to complete and honest about what it sends —
 * so the assertions here are about what leaves the browser, not about how the panel looks.
 */
describe("FeedbackPanel", () => {
  const screenshot = new Blob(["png-bytes"], { type: "image/png" });

  const renderPanel = (onClose = () => {}) =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <FeedbackPanel screenshot={screenshot} context={CONTEXT} onClose={onClose} />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  beforeEach(() => {
    vi.mocked(feedbackApi.submitFeedback).mockReset();
    // AuthProvider tries to restore a session on mount. There isn't one, so the panel renders its
    // anonymous shape — which is the one the pre-login screens use.
    vi.mocked(authApi.me).mockRejectedValue(new Error("no session"));
    // jsdom implements neither, and the screenshot preview needs both.
    URL.createObjectURL = vi.fn(() => "blob:screenshot");
    URL.revokeObjectURL = vi.fn();
  });

  it("sends what was typed, with the captured screenshot and the collected context", async () => {
    const user = userEvent.setup();
    vi.mocked(feedbackApi.submitFeedback).mockResolvedValue({
      published: true,
      issueNumber: 128,
      issueUrl: "https://github.com/nextwebspark/app-lightmove/issues/128",
    });
    renderPanel();

    await user.type(screen.getByPlaceholderText(/Saving the brief/), "Saving loses step 3");
    await user.type(
      screen.getByPlaceholderText(/What you expected/),
      "The org chart is empty after a reload.",
    );
    await user.click(screen.getByRole("button", { name: "Send report" }));

    await waitFor(() => expect(feedbackApi.submitFeedback).toHaveBeenCalled());

    const [report, sentScreenshot, uploads] = vi.mocked(feedbackApi.submitFeedback).mock.calls[0];
    expect(report.kind).toBe("BUG");
    expect(report.title).toBe("Saving loses step 3");
    expect(report.context).toEqual(CONTEXT);
    expect(sentScreenshot).toBe(screenshot);
    expect(uploads).toEqual([]);
  });

  it("does not send the screenshot once it has been unticked", async () => {
    const user = userEvent.setup();
    vi.mocked(feedbackApi.submitFeedback).mockResolvedValue({
      published: false,
      issueNumber: null,
      issueUrl: null,
    });
    renderPanel();

    await user.type(screen.getByPlaceholderText(/Saving the brief/), "Grid will not load");
    await user.type(screen.getByPlaceholderText(/What you expected/), "It spins and never settles.");
    await user.click(screen.getByRole("checkbox", { name: /Attach this screenshot/ }));
    await user.click(screen.getByRole("button", { name: "Send report" }));

    await waitFor(() => expect(feedbackApi.submitFeedback).toHaveBeenCalled());
    expect(vi.mocked(feedbackApi.submitFeedback).mock.calls[0][1]).toBeNull();
  });

  it("refuses an empty report without calling the server", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByRole("button", { name: "Send report" }));

    expect(await screen.findByText("Give it a short summary")).toBeInTheDocument();
    expect(feedbackApi.submitFeedback).not.toHaveBeenCalled();
  });

  /** A feature request has no steps to reproduce, and asking for them reads as a form nobody read. */
  it("swaps the questions when the report is a feature request", async () => {
    const user = userEvent.setup();
    renderPanel();

    expect(screen.getByText("Steps to reproduce")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /I'd like something/ }));

    expect(screen.queryByText("Steps to reproduce")).not.toBeInTheDocument();
    expect(screen.getByText("What would you like")).toBeInTheDocument();
  });

  it("shows the filed issue when there is one to link to", async () => {
    const user = userEvent.setup();
    vi.mocked(feedbackApi.submitFeedback).mockResolvedValue({
      published: true,
      issueNumber: 128,
      issueUrl: "https://github.com/nextwebspark/app-lightmove/issues/128",
    });
    renderPanel();

    await user.type(screen.getByPlaceholderText(/Saving the brief/), "Saving loses step 3");
    await user.type(screen.getByPlaceholderText(/What you expected/), "The org chart is empty.");
    await user.click(screen.getByRole("button", { name: "Send report" }));

    expect(await screen.findByText("#128")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open it on GitHub/ })).toHaveAttribute(
      "href",
      "https://github.com/nextwebspark/app-lightmove/issues/128",
    );
  });

  /**
   * A deployment with no tracker credential still received the report. Reporting that as a failure
   * would train testers out of filing, which is the one outcome this feature cannot afford.
   */
  it("reads as received, not failed, when no issue tracker is wired up", async () => {
    const user = userEvent.setup();
    vi.mocked(feedbackApi.submitFeedback).mockResolvedValue({
      published: false,
      issueNumber: null,
      issueUrl: null,
    });
    renderPanel();

    await user.type(screen.getByPlaceholderText(/Saving the brief/), "Saving loses step 3");
    await user.type(screen.getByPlaceholderText(/What you expected/), "The org chart is empty.");
    await user.click(screen.getByRole("button", { name: "Send report" }));

    expect(await screen.findByText(/Your report was received/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Open it on GitHub/ })).not.toBeInTheDocument();
  });
});
