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
  decideCallSuggestion: vi.fn(),
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

  it("reads the transcript once on opening, then shows one change at a time against what is held", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([], false));
    vi.mocked(notetakerApi.suggestChanges).mockResolvedValue(answerOf([NOTICE, PACKAGE]));
    renderDialog();

    expect(await screen.findByText("Notice period")).toBeInTheDocument();
    expect(notetakerApi.suggestChanges).toHaveBeenCalledTimes(1);
    expect(screen.getByText("1 of 2")).toBeInTheDocument();
    expect(screen.getByText("Not recorded")).toBeInTheDocument();
    expect(screen.getByText("3 months")).toBeInTheDocument();
    expect(screen.getByText("My notice is ninety days.")).toBeInTheDocument();
    expect(screen.getByText("07:42")).toBeInTheDocument();
  });

  it("does not read the transcript again when changes are already waiting", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE]));
    renderDialog();

    expect(await screen.findByText("3 months")).toBeInTheDocument();
    expect(notetakerApi.suggestChanges).not.toHaveBeenCalled();
  });

  it("applies a change, moves to the next, and applies only the ticked package figures", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE, PACKAGE]));
    vi.mocked(notetakerApi.decideCallSuggestion)
      .mockResolvedValueOnce(answerOf([decided(NOTICE, "APPLIED"), PACKAGE]))
      .mockResolvedValueOnce(answerOf([decided(NOTICE, "APPLIED"), decided(PACKAGE, "APPLIED")]));
    renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Apply" }));
    expect(notetakerApi.decideCallSuggestion).toHaveBeenLastCalledWith(
      "p1",
      "c1",
      "r1",
      "notice-period",
      "apply",
      undefined,
    );
    expect(await screen.findByText("2 of 2")).toBeInTheDocument();
    expect(screen.getByText("Annualised from monthly: base 95,000 × 12")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("checkbox", { name: /Bonus/ }));
    await userEvent.click(screen.getByRole("button", { name: "Apply ticked" }));
    expect(notetakerApi.decideCallSuggestion).toHaveBeenLastCalledWith("p1", "c1", "r1", "current-package", "apply", {
      packageFields: ["baseSalary", "allowances"],
    });
    expect(await screen.findByText(/^2 applied/)).toBeInTheDocument();
  });

  it("applies an edited notice period as the researcher chose it", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE]));
    vi.mocked(notetakerApi.decideCallSuggestion).mockResolvedValue(answerOf([decided(NOTICE, "APPLIED")]));
    renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Edit" }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Notice period" }), "2 months");
    await userEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(notetakerApi.decideCallSuggestion).toHaveBeenCalledWith("p1", "c1", "r1", "notice-period", "apply", {
      value: { text: "2 months" },
    });
  });

  it("skips, sums up what was decided, and undoes a line from the summary", async () => {
    vi.mocked(notetakerApi.getCallSuggestions).mockResolvedValue(answerOf([NOTICE]));
    vi.mocked(notetakerApi.decideCallSuggestion)
      .mockResolvedValueOnce(answerOf([decided(NOTICE, "SKIPPED")]))
      .mockResolvedValueOnce(answerOf([NOTICE]));
    renderDialog();

    await userEvent.click(await screen.findByRole("button", { name: "Skip" }));
    expect(await screen.findByText(/^0 applied/)).toBeInTheDocument();
    expect(screen.getByText("Skipped")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Undo" }));
    expect(notetakerApi.decideCallSuggestion).toHaveBeenLastCalledWith(
      "p1",
      "c1",
      "r1",
      "notice-period",
      "reopen",
      undefined,
    );
    expect(await screen.findByRole("button", { name: "Apply" })).toBeInTheDocument();
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

  it("explains a change the record no longer lets in, and offers only Skip", async () => {
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
    expect(screen.queryByRole("button", { name: "Apply" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Skip" })).toBeInTheDocument();
  });
});
