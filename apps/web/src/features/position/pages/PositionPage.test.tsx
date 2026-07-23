import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { Project } from "../../projects/api/types";
import * as positionApi from "../api/positionApi";
import type { Position } from "../api/types";
import { PositionPage } from "./PositionPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/positionApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../api/positionApi")>()),
  getPosition: vi.fn(),
  putPosition: vi.fn(),
  putCriteria: vi.fn(),
  putCompetencies: vi.fn(),
  uploadBriefDocument: vi.fn(),
  removeBriefDocument: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const workspace = {
  id: "w1",
  name: "NextWebSpark Search",
  slug: "nextwebspark-search",
  logoMark: "N",
  emailDomain: "nextwebspark.com",
};

const userOf = (roles: ("ADMIN" | "MEMBER")[]) => ({
  id: "u1",
  email: "alok@nextwebspark.com",
  fullName: "Alok Kumar",
  title: null,
  avatarUrl: null,
  emailVerified: true,
  onboardingHeld: false,
  pendingInvitation: null,
  workspace: { ...workspace, roles },
});

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy Group",
  positionTitle: "Chief Financial Officer",
  stage: "BRIEF",
  health: "OK",
  targetDate: null,
  team: [],
  companies: 0,
  candidates: 0,
  createdAt: "2026-07-01T00:00:00Z",
};

const seeded: Position = {
  mandateReason: "NEW_ROLE",
  internalContext: null,
  narrative: "The CFO will sit on the executive committee.",
  reportsTo: "Group CEO",
  directReports: null,
  teamSize: null,
  location: "UAE",
  employmentType: "FULL_TIME_PERMANENT",
  startTarget: null,
  salaryMin: null,
  salaryMax: null,
  currency: "USD",
  noticeValue: null,
  noticeUnit: null,
  bonusTargetPct: null,
  ltip: null,
  benefits: [],
  confidential: false,
  criteria: [
    { text: "Experience reporting to a board", mode: "REQUIRED", fromBrief: true },
    { text: "Arabic language skills", mode: "PREFERRED", fromBrief: false },
  ],
  technical: [
    { name: "Financial Reporting & Controls", weight: 60 },
    { name: "Treasury", weight: 40 },
  ],
  behavioural: [{ name: "Strategic Leadership", weight: 100 }],
  locked: false,
  lockedAt: null,
  briefDocument: null,
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthProvider>
          <ToastProvider>
            <Routes>
              <Route element={<Outlet context={{ project }} />}>
                <Route path="/" element={<PositionPage />} />
              </Route>
              <Route path="/projects/:projectId/strategy" element={<div>Strategy screen</div>} />
            </Routes>
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("PositionPage — the brief editor", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(userOf(["MEMBER"]));
    vi.mocked(positionApi.getPosition).mockResolvedValue(seeded);
  });

  it("renders the seeded brief: hero, template criteria and the from-brief tag", async () => {
    renderPage();

    // The title shows twice: the hero and the org row's "This role" box.
    expect(await screen.findAllByText("Chief Financial Officer")).toHaveLength(2);
    expect(screen.getByDisplayValue("The CFO will sit on the executive committee.")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Experience reporting to a board")).toBeInTheDocument();
    expect(screen.getByText("From brief")).toBeInTheDocument();
    // Employment type is a fixed-set select showing the seeded value's label.
    expect(screen.getByDisplayValue("Full-time, permanent").tagName).toBe("SELECT");
  });

  it("autosaves a criterion mode change after the debounce", async () => {
    vi.mocked(positionApi.putCriteria).mockResolvedValue({ ...seeded });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    // Flip "Arabic language skills" from Preferred to Required.
    await userEvent.click(screen.getAllByRole("button", { name: "Required" })[1]);

    await waitFor(() => expect(positionApi.putCriteria).toHaveBeenCalledTimes(1), { timeout: 2000 });
    const [, sent] = vi.mocked(positionApi.putCriteria).mock.calls[0];
    expect(sent[1].mode).toBe("REQUIRED");
  });

  it("commits a benefit on blur (no Enter) — the dropped-benefit fix", async () => {
    vi.mocked(positionApi.putPosition).mockResolvedValue({ ...seeded });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    const field = screen.getByLabelText("Add a benefit");
    await userEvent.type(field, "Car allowance");
    await userEvent.tab(); // blur without pressing Enter

    await waitFor(() => expect(positionApi.putPosition).toHaveBeenCalled(), { timeout: 2000 });
    const lastCall = vi.mocked(positionApi.putPosition).mock.calls.at(-1)!;
    expect(lastCall[1].benefits).toContain("Car allowance");
  });

  it("a locked brief is read-only", async () => {
    vi.mocked(positionApi.getPosition).mockResolvedValue({ ...seeded, locked: true, lockedAt: "2026-07-17T00:00:00Z" });
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    expect(screen.getByDisplayValue("Experience reporting to a board")).toBeDisabled();
  });

  it("the footer offers Go to Strategy and navigates there", async () => {
    renderPage();
    await screen.findAllByText("Chief Financial Officer");

    await userEvent.click(screen.getByRole("button", { name: /Go to Strategy/ }));

    expect(await screen.findByText("Strategy screen")).toBeInTheDocument();
  });

  describe("the Position Description upload", () => {
    const withDocument = {
      fileName: "cfo-pd.pdf",
      contentType: "application/pdf",
      fileSize: 248_000,
      uploadedAt: "2026-07-17T00:00:00Z",
      status: "COMPLETED" as const,
    };

    it("shows the empty dropzone, uploads a chosen file, and shows the parsed result", async () => {
      vi.mocked(positionApi.uploadBriefDocument).mockResolvedValue({
        ...seeded,
        reportsTo: "Group CFO",
        briefDocument: withDocument,
      });
      renderPage();
      await screen.findAllByText("Chief Financial Officer");

      expect(screen.getByText("Drop a PD here, or click to browse")).toBeInTheDocument();

      const file = new File(["pd text"], "cfo-pd.pdf", { type: "application/pdf" });
      await userEvent.upload(screen.getByLabelText("Upload position description"), file);

      expect(positionApi.uploadBriefDocument).toHaveBeenCalledWith("p1", file);
      expect(await screen.findByText("Parsed — details extracted to help fill this page")).toBeInTheDocument();
      // The remount-on-uploadedAt-change picked up the freshly extracted field.
      expect(await screen.findByDisplayValue("Group CFO")).toBeInTheDocument();
      expect(screen.getByText("cfo-pd.pdf")).toBeInTheDocument();
    });

    it("shows a parsing spinner while the upload is in flight", async () => {
      let resolveUpload!: (position: Position) => void;
      vi.mocked(positionApi.uploadBriefDocument).mockReturnValue(
        new Promise((resolve) => (resolveUpload = resolve)),
      );
      renderPage();
      await screen.findAllByText("Chief Financial Officer");

      const file = new File(["pd text"], "cfo-pd.pdf", { type: "application/pdf" });
      await userEvent.upload(screen.getByLabelText("Upload position description"), file);

      expect(await screen.findByText("Parsing document and extracting details…")).toBeInTheDocument();
      resolveUpload({ ...seeded, briefDocument: withDocument });
      await waitFor(() =>
        expect(screen.queryByText("Parsing document and extracting details…")).not.toBeInTheDocument(),
      );
    });

    it("Remove clears the file chip back to the dropzone", async () => {
      vi.mocked(positionApi.getPosition).mockResolvedValue({ ...seeded, briefDocument: withDocument });
      vi.mocked(positionApi.removeBriefDocument).mockResolvedValue({ ...seeded, briefDocument: null });
      renderPage();
      await screen.findByText("cfo-pd.pdf");

      await userEvent.click(screen.getByRole("button", { name: "Remove" }));

      expect(positionApi.removeBriefDocument).toHaveBeenCalledWith("p1");
      expect(await screen.findByText("Drop a PD here, or click to browse")).toBeInTheDocument();
    });

    it("an extraction failure toasts and leaves the dropzone empty", async () => {
      vi.mocked(positionApi.uploadBriefDocument).mockRejectedValue(
        Object.assign(new Error("failed"), { code: "BRIEF_EXTRACTION_FAILED", problem: {} }),
      );
      renderPage();
      await screen.findAllByText("Chief Financial Officer");

      const file = new File(["pd text"], "broken.pdf", { type: "application/pdf" });
      await userEvent.upload(screen.getByLabelText("Upload position description"), file);

      expect(await screen.findByText("Something went wrong. Try again.")).toBeInTheDocument();
      expect(screen.getByText("Drop a PD here, or click to browse")).toBeInTheDocument();
    });
  });
});
