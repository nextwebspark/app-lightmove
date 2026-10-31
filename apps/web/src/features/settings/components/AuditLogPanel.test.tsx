import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as auditLogApi from "../api/auditLogApi";
import type { AuditLogEntry } from "../api/types";
import { AuditLogPanel } from "./AuditLogPanel";

vi.mock("../api/auditLogApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/auditLogApi")>()),
  auditLog: vi.fn(),
  auditLogPeople: vi.fn(),
  saveAuditLogCsv: vi.fn(),
}));

function anEntry(overrides: Partial<AuditLogEntry> = {}): AuditLogEntry {
  return {
    id: 41,
    occurredAt: "2026-10-30T09:15:00Z",
    type: "COMPANIES_EXPORTED",
    group: "exportsAndDownloads",
    outcome: "SUCCESS",
    actorKind: "MEMBER",
    actorUserId: "u1",
    actorName: "Mira Saleh",
    actorAvatarUrl: null,
    actorRemoved: false,
    summary: "Exported 12 companies from In universe",
    positionTitle: "Group CFO",
    ipAddress: "102.44.18.7",
    ...overrides,
  };
}

function renderPanel() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <AuditLogPanel />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

/** Settings → Security → Audit log: the server's own lines, a filter row, Load more and a CSV of the same view. */
describe("AuditLogPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(auditLogApi.auditLogPeople).mockResolvedValue([
      { userId: "u1", name: "Mira Saleh", avatarUrl: null, removed: false },
      { userId: "u2", name: "Omar Haddad", avatarUrl: null, removed: true },
    ]);
  });

  it("draws each line with who, what, where and from which address", async () => {
    vi.mocked(auditLogApi.auditLog).mockResolvedValue({
      entries: [
        anEntry(),
        anEntry({
          id: 40,
          type: "LOGIN_FAILED",
          group: "signIn",
          outcome: "FAILURE",
          actorName: "Omar Haddad",
          actorRemoved: true,
          summary: "Sign-in failed: wrong password",
          positionTitle: null,
        }),
      ],
      nextCursor: null,
    });
    renderPanel();

    expect(await screen.findByText("Exported 12 companies from In universe")).toBeInTheDocument();
    expect(screen.getByText("Group CFO")).toBeInTheDocument();
    expect(screen.getAllByText("102.44.18.7")).toHaveLength(2);
    expect(screen.getByText("Sign-in failed: wrong password")).toBeInTheDocument();
    expect(screen.getByText("Failed", { selector: "span" })).toBeInTheDocument();
    expect(screen.getByText("(removed)")).toBeInTheDocument();
    expect(await screen.findByRole("option", { name: "Omar Haddad (removed)" })).toBeInTheDocument();
  });

  it("asks again when a filter changes", async () => {
    vi.mocked(auditLogApi.auditLog).mockResolvedValue({ entries: [anEntry()], nextCursor: null });
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Exported 12 companies from In universe");

    await user.selectOptions(screen.getByRole("combobox", { name: "Category" }), "signIn");
    await user.selectOptions(screen.getByRole("combobox", { name: "Outcome" }), "FAILURE");

    await waitFor(() =>
      expect(auditLogApi.auditLog).toHaveBeenLastCalledWith(
        expect.objectContaining({ group: "signIn", outcome: "FAILURE" }),
        null,
        expect.anything(),
      ),
    );
  });

  it("loads older lines from the cursor the last page gave", async () => {
    vi.mocked(auditLogApi.auditLog)
      .mockResolvedValueOnce({ entries: [anEntry()], nextCursor: 41 })
      .mockResolvedValueOnce({ entries: [anEntry({ id: 39, summary: "Created the workspace" })], nextCursor: null });
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Load more" }));

    expect(await screen.findByText("Created the workspace")).toBeInTheDocument();
    expect(auditLogApi.auditLog).toHaveBeenLastCalledWith(expect.anything(), 41, expect.anything());
    expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument();
  });

  it("says a refused read failed rather than that nothing happened", async () => {
    vi.mocked(auditLogApi.auditLog).mockRejectedValue(
      new ApiRequestError({ code: "FORBIDDEN", detail: "server words", status: 403, correlationId: "c1" }),
    );
    renderPanel();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(/No activity matches these filters/)).not.toBeInTheDocument();
  });

  it("says when nothing matches", async () => {
    vi.mocked(auditLogApi.auditLog).mockResolvedValue({ entries: [], nextCursor: null });
    renderPanel();

    expect(await screen.findByText(/No activity matches these filters/)).toBeInTheDocument();
  });

  it("exports the filtered view and says the export was recorded", async () => {
    vi.mocked(auditLogApi.auditLog).mockResolvedValue({ entries: [anEntry()], nextCursor: null });
    vi.mocked(auditLogApi.saveAuditLogCsv).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Exported 12 companies from In universe");

    await user.selectOptions(screen.getByRole("combobox", { name: "Category" }), "exportsAndDownloads");
    await user.click(screen.getByRole("button", { name: "Export CSV" }));

    await waitFor(() =>
      expect(auditLogApi.saveAuditLogCsv).toHaveBeenCalledWith(
        expect.objectContaining({ group: "exportsAndDownloads" }),
      ),
    );
    expect(await screen.findByText("Exported as a CSV — recorded in the audit log")).toBeInTheDocument();
  });
});
