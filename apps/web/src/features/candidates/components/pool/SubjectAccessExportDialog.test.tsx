import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import { ApiRequestError } from "../../../../lib/apiClient";
import { saveBlob } from "../../../../lib/saveBlob";
import * as poolApi from "../../api/poolApi";
import { SubjectAccessExportDialog, subjectAccessFileName } from "./SubjectAccessExportDialog";

vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  exportSubjectAccess: vi.fn(),
}));
vi.mock("../../../../lib/saveBlob", () => ({ saveBlob: vi.fn() }));

function renderDialog(onClose = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <SubjectAccessExportDialog personId="person-1" fullName="Noor Haddad" onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return onClose;
}

describe("SubjectAccessExportDialog", () => {
  afterEach(() => vi.resetAllMocks());

  it("says what is in the file and asks the admin to check it before sending", () => {
    renderDialog();

    expect(screen.getByRole("list", { name: "What's in it" })).toHaveTextContent(/AI proposed about them/);
    expect(screen.getByRole("note")).toHaveTextContent("Notes and call transcripts can mention other people.");
    expect(screen.getByText(/Recorded in the audit log/)).toBeInTheDocument();
  });

  it("keeps the dialog open and says why when the export is refused", async () => {
    vi.mocked(poolApi.exportSubjectAccess).mockRejectedValue(
      new ApiRequestError({ code: "FORBIDDEN", detail: "no", status: 403, correlationId: "none" }),
    );
    const onClose = renderDialog();

    await userEvent.click(screen.getByRole("button", { name: "Download" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(saveBlob).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("names the file after the person and the day, accents and all", () => {
    expect(subjectAccessFileName("Zoë  Al-Ḥaddād", new Date("2026-11-11T09:00:00Z"))).toBe(
      "uncava-subject-access-zoe-al-haddad-2026-11-11.zip",
    );
    expect(subjectAccessFileName("محمد", new Date("2026-11-11T09:00:00Z"))).toBe(
      "uncava-subject-access-person-2026-11-11.zip",
    );
  });
});
