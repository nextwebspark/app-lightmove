import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as notetakerApi from "../api/notetakerApi";
import type { CallSuggestion, CallSuggestions, SuggestedValue } from "../api/notetakerApi";
import { CallChangesDialog } from "./CallChangesDialog";

vi.mock("../api/notetakerApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/notetakerApi")>()),
  getCallSuggestions: vi.fn(),
  suggestChanges: vi.fn(),
  reviewCallSuggestions: vi.fn(),
}));

const EMPTY: SuggestedValue = {
  text: null,
  city: null,
  country: null,
  currency: null,
  baseSalary: null,
  bonus: null,
  allowances: null,
  working: null,
};

const NOTICE: CallSuggestion = {
  id: "notice-period",
  kind: "NOTICE_PERIOD",
  status: "PENDING",
  value: { ...EMPTY, text: "3 months" },
  current: { ...EMPTY },
  said: "My notice is ninety days.",
  at: "07:42",
  available: true,
  edited: false,
  was: null,
  appliedAt: null,
  appliedBy: null,
};

const PACKAGE: CallSuggestion = {
  id: "current-package",
  kind: "CURRENT_PACKAGE",
  status: "PENDING",
  value: {
    ...EMPTY,
    currency: "AED",
    baseSalary: 1_140_000,
    bonus: 380_000,
    allowances: 216_000,
    working: "Annualised from monthly: base 95,000 × 12",
  },
  current: { ...EMPTY },
  said: "Basic is ninety-five a month.",
  at: "09:15",
  available: true,
  edited: false,
  was: null,
  appliedAt: null,
  appliedBy: null,
};

function answerOf(suggestions: CallSuggestion[], asked = true): CallSuggestions {
  return {
    recordingId: "r1",
    title: "Intro call",
    startsAt: "2026-10-21T10:00:00Z",
    asked,
    mayAskAgain: asked && suggestions.every((each) => each.status !== "PENDING"),
    suggestions,
  };
}

function decided(suggestion: CallSuggestion, status: CallSuggestion["status"]): CallSuggestion {
  return { ...suggestion, status };
}

function renderDialog() {
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <CallChangesDialog
          projectId="p1"
          candidateId="c1"
          personId="person-1"
          recordingId="r1"
          firstName="Priya"
          onClose={onClose}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return onClose;
}

describe("CallChangesDialog", () => {
  beforeEach(() => vi.resetAllMocks());

  it("reads the transcript once on opening, then lists every change against what is held", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([], false));
    vi.mocked(notetakerApi.suggestChanges).mockResolvedValue(answerOf([NOTICE, PACKAGE]));
    renderDialog();

    expect(await screen.findByText("2 changes found")).toBeInTheDocument();
    expect(notetakerApi.suggestChanges).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Notice period")).toBeInTheDocument();
    expect(screen.getByText("Current package")).toBeInTheDocument();
    expect(screen.getAllByText("Not recorded")).toHaveLength(2);
    expect(screen.getByText("3 months")).toBeInTheDocument();
    expect(screen.getByText("My notice is ninety days.")).toBeInTheDocument();
    expect(screen.getByText("07:42")).toBeInTheDocument();
    expect(screen.getByText("Annualised from monthly: base 95,000 × 12")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Apply 2 changes" })).toBeInTheDocument();
  });

  it("does not read the transcript again when changes are already waiting", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE]));
    renderDialog();

    expect(await screen.findByText("3 months")).toBeInTheDocument();
    expect(notetakerApi.suggestChanges).not.toHaveBeenCalled();
  });

  it("saves every kept change in one request, as edited and with only the ticked figures, then closes", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE, PACKAGE]));
    vi.mocked(notetakerApi.reviewCallSuggestions).mockResolvedValue(
      answerOf([decided(NOTICE, "APPLIED"), decided(PACKAGE, "APPLIED")]),
    );
    const onClose = renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Edit notice period" }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Notice period" }), "2 months");
    await userEvent.click(screen.getByRole("checkbox", { name: "Bonus" }));
    await userEvent.click(screen.getByRole("button", { name: "Apply 2 changes" }));

    expect(notetakerApi.reviewCallSuggestions).toHaveBeenCalledTimes(1);
    expect(notetakerApi.reviewCallSuggestions).toHaveBeenCalledWith("p1", "c1", "r1", [
      { id: "notice-period", value: { text: "2 months" } },
      { id: "current-package", packageFields: ["baseSalary", "allowances"] },
    ]);
    expect(await screen.findByText("2 changes saved to Priya's record")).toBeInTheDocument();
    expect(onClose).toHaveBeenCalled();
  });

  it("skips what is unticked, and skips everything when nothing is kept", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE, PACKAGE]));
    vi.mocked(notetakerApi.reviewCallSuggestions).mockResolvedValue(
      answerOf([decided(NOTICE, "SKIPPED"), decided(PACKAGE, "SKIPPED")]),
    );
    renderDialog();

    await userEvent.click(await screen.findByRole("checkbox", { name: "Apply notice period" }));
    expect(screen.getByText(/1 selected/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Apply 1 change" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "None" }));
    await userEvent.click(screen.getByRole("button", { name: "Skip all" }));
    expect(notetakerApi.reviewCallSuggestions).toHaveBeenCalledWith("p1", "c1", "r1", []);
  });

  it("shows a refused change on its own row and saves nothing", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE, PACKAGE]));
    vi.mocked(notetakerApi.reviewCallSuggestions).mockRejectedValue(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "Choose a notice period: None, 1, 2, 3 or 6 months.",
        status: 400,
        correlationId: "c",
        fieldErrors: { "notice-period": "Choose a notice period: None, 1, 2, 3 or 6 months." },
      }),
    );
    const onClose = renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Apply 2 changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Choose a notice period: None, 1, 2, 3 or 6 months.");
    expect(onClose).not.toHaveBeenCalled();
  });

  it("closes on Not now without saving anything", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE]));
    const onClose = renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Not now" }));

    expect(onClose).toHaveBeenCalled();
    expect(notetakerApi.reviewCallSuggestions).not.toHaveBeenCalled();
  });

  it("reads a reviewed call back as a record of what changed, with no way to undo it", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(
      answerOf([
        {
          ...decided(NOTICE, "APPLIED"),
          was: { ...EMPTY, text: "1 month" },
          appliedAt: "2026-10-21T11:00:00Z",
          appliedBy: "Yara Haddad",
        },
        decided(PACKAGE, "SKIPPED"),
      ]),
    );
    renderDialog();

    expect(await screen.findByText(/^1 applied/)).toBeInTheDocument();
    expect(screen.getByText("Applied by Yara")).toBeInTheDocument();
    expect(screen.getByText("1 month").tagName).toBe("DEL");
    expect(screen.getByText("My notice is ninety days.")).toBeInTheDocument();
    expect(screen.getByText("Skipped")).toBeInTheDocument();
    expect(screen.getByText("To change any of it, edit Priya's record.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.getAllByRole("button")).toEqual([screen.getByRole("button", { name: "Close" })]);
  });

  it("lists changes already decided apart from those still waiting, read only", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([decided(NOTICE, "APPLIED"), PACKAGE]));
    renderDialog();

    expect(await screen.findByText("1 change found")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Already reviewed" })).toHaveTextContent("Applied");
    expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Apply notice period" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Apply 1 change" })).toBeInTheDocument();
  });

  it("says so when the call stated nothing new", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([]));
    renderDialog();

    expect(await screen.findByText("Nothing to change")).toBeInTheDocument();
    expect(screen.getByText("The call didn't state anything new for Priya's record.")).toBeInTheDocument();
  });

  it("offers Try again when the AI could not answer, and reads again when pressed", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([], false));
    vi.mocked(notetakerApi.suggestChanges)
      .mockRejectedValueOnce(
        new ApiRequestError({
          code: "AI_UNAVAILABLE",
          detail: "x",
          status: 502,
          correlationId: "c",
        }),
      )
      .mockResolvedValueOnce(answerOf([NOTICE]));
    renderDialog();

    expect(await screen.findByText("Couldn't read the transcript just now.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByText("3 months")).toBeInTheDocument();
    await waitFor(() => expect(notetakerApi.suggestChanges).toHaveBeenCalledTimes(2));
  });

  it("explains a change the record no longer lets in, and leaves it unticked", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(
      answerOf([
        {
          ...NOTICE,
          id: "open-to-relocate",
          kind: "OPEN_TO_RELOCATE",
          value: { ...EMPTY, text: "Open to relocate" },
          available: false,
        },
      ]),
    );
    renderDialog();

    expect(await screen.findByText(/the Open to relocate tag was renamed or retired/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Apply open to relocate" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "Apply open to relocate" })).not.toBeChecked();
    expect(screen.getByRole("button", { name: "Skip all" })).toBeInTheDocument();
  });
});
