import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as candidatePrivacyApi from "../../candidates/api/candidatePrivacyApi";
import type { CandidatePrivacySettings } from "../../candidates/api/candidatePrivacyApi";
import { CandidatePrivacyCard } from "./CandidatePrivacyCard";

vi.mock("../../candidates/api/candidatePrivacyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/candidatePrivacyApi")>()),
  putCandidatePrivacy: vi.fn(),
}));

const UNSET: CandidatePrivacySettings = {
  controllerName: null,
  privacyContactEmail: null,
  noticeUrl: null,
  defaultLawfulBasis: "legitimate_interest",
  readyForNotices: false,
  privacyMode: "notices",
  keepOnFile: true,
  keepOnFileMonths: 24,
  shareWithClient: true,
  marketing: false,
  renewalDaysBefore: 30,
  readyForConsent: false,
};

const SAVED: CandidatePrivacySettings = {
  controllerName: "Meridian Search Partners FZ-LLC",
  privacyContactEmail: "privacy@meridian.example",
  noticeUrl: "https://meridian.example/privacy",
  defaultLawfulBasis: "legitimate_interest",
  readyForNotices: true,
  privacyMode: "notices",
  keepOnFile: true,
  keepOnFileMonths: 24,
  shareWithClient: true,
  marketing: false,
  renewalDaysBefore: 30,
  readyForConsent: false,
};

function renderCard(saved: CandidatePrivacySettings) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <CandidatePrivacyCard saved={saved} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("CandidatePrivacyCard", () => {
  beforeEach(() => vi.mocked(candidatePrivacyApi.putCandidatePrivacy).mockReset());

  it("says notices wait for the firm's details, and saves the whole card", async () => {
    vi.mocked(candidatePrivacyApi.putCandidatePrivacy).mockResolvedValue(SAVED);
    const user = userEvent.setup();
    renderCard(UNSET);

    expect(screen.getByText(/before anyone can send a privacy notice/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();

    await user.type(screen.getByLabelText(/^Firm's legal name/), "Meridian Search Partners FZ-LLC");
    await user.type(screen.getByLabelText(/^Privacy contact email/), "privacy@meridian.example");
    await user.type(screen.getByLabelText(/^Privacy notice link/), "https://meridian.example/privacy");
    await user.selectOptions(screen.getByLabelText(/^Default lawful basis/), "consent");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(candidatePrivacyApi.putCandidatePrivacy).toHaveBeenCalledWith(
        expect.objectContaining({
          controllerName: "Meridian Search Partners FZ-LLC",
          privacyContactEmail: "privacy@meridian.example",
          noticeUrl: "https://meridian.example/privacy",
          defaultLawfulBasis: "consent",
          privacyMode: "notices",
        }),
      ),
    );
    expect(await screen.findByText("Candidate privacy saved")).toBeInTheDocument();
  });

  it("refuses a link that is not https before asking, and shows the server's field error", async () => {
    vi.mocked(candidatePrivacyApi.putCandidatePrivacy).mockRejectedValueOnce(
      new ApiRequestError({
        code: "VALIDATION_FAILED",
        detail: "Validation failed",
        status: 400,
        correlationId: "c1",
        fieldErrors: { privacyContactEmail: "Enter an email address" },
      }),
    );
    const user = userEvent.setup();
    renderCard(SAVED);

    const link = screen.getByLabelText(/^Privacy notice link/);
    await user.clear(link);
    await user.type(link, "http://meridian.example/privacy");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText("Enter a link starting with https://")).toBeInTheDocument();
    expect(candidatePrivacyApi.putCandidatePrivacy).not.toHaveBeenCalled();

    await user.clear(link);
    await user.type(link, "https://meridian.example/notice");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(candidatePrivacyApi.putCandidatePrivacy).toHaveBeenCalledTimes(1));
    expect(await screen.findByText("Enter an email address")).toBeInTheDocument();
  });

  it("locks the mode until the firm's details are saved", () => {
    renderCard(UNSET);

    expect(screen.getByRole("radio", { name: "Notices and consent requests" })).toBeDisabled();
    expect(
      screen.getByText("Save your firm's name, privacy contact and notice link to choose a mode."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Consent requests" })).not.toBeInTheDocument();
  });

  it("offers the purposes in consent mode, and saves them with the mode", async () => {
    vi.mocked(candidatePrivacyApi.putCandidatePrivacy).mockResolvedValue({ ...SAVED, privacyMode: "consent" });
    const user = userEvent.setup();
    renderCard(SAVED);

    await user.click(screen.getByRole("radio", { name: "Notices and consent requests" }));
    const purposes = screen.getByRole("group", { name: "Consent requests" });
    expect(purposes).toHaveTextContent("Each is asked on its own, with nothing ticked. No answer changes nothing.");
    expect(screen.getByRole("switch", { name: "Keep my details on file for future roles" })).toBeChecked();
    expect(screen.getByRole("switch", { name: "Career and market updates" })).not.toBeChecked();
    await user.click(screen.getByRole("switch", { name: "Career and market updates" }));
    await user.selectOptions(screen.getByRole("combobox", { name: "Valid for" }), "36");
    const days = screen.getByRole("spinbutton", { name: "Days before consent ends" });
    await user.clear(days);
    await user.type(days, "0");
    expect(screen.getByText("Never asked again: consent simply ends on its date.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(candidatePrivacyApi.putCandidatePrivacy).toHaveBeenCalledWith(
        expect.objectContaining({ privacyMode: "consent", marketing: true, keepOnFileMonths: 36, renewalDaysBefore: 0 }),
      ),
    );
  });

  it("refuses an ask-again period past 90 days before asking", async () => {
    const user = userEvent.setup();
    renderCard({ ...SAVED, privacyMode: "consent" });

    const days = screen.getByRole("spinbutton", { name: "Days before consent ends" });
    await user.clear(days);
    await user.type(days, "120");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Enter 0 to 90 days")).toBeInTheDocument();
    expect(candidatePrivacyApi.putCandidatePrivacy).not.toHaveBeenCalled();
  });
});
