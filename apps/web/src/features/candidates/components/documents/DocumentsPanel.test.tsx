import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as documentsApi from "../../api/documentsApi";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument } from "../../api/types";
import { usePersonDocuments } from "../../lib/usePersonDocuments";
import { DocumentsPanel } from "./DocumentsPanel";

vi.mock("../../api/documentsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof documentsApi>()),
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  uploadVersion: vi.fn(),
}));

const scope: DocumentScope = { kind: "person", personId: "p1" };

const cv: PersonDocument = {
  id: "d1",
  category: "cv",
  title: "Fatima Al Mazrouei CV",
  primaryCv: true,
  projectId: null,
  projectTitle: null,
  createdBy: "u1",
  createdByName: "Yara Haddad",
  createdAt: "2026-04-14T10:20:00Z",
  updatedAt: "2026-09-12T09:40:00Z",
  removable: true,
  versions: [
    {
      id: "v3",
      versionNo: 3,
      fileName: "Fatima_Al_Mazrouei_CV.pdf",
      contentType: "application/pdf",
      sizeBytes: 412_000,
      uploadedBy: "u2",
      uploadedByName: "Yousef Iman",
      uploadedAt: "2026-09-12T09:40:00Z",
      previewable: true,
      removable: false,
    },
    {
      id: "v1",
      versionNo: 1,
      fileName: "Fatima_CV_2024.docx",
      contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      sizeBytes: 86_000,
      uploadedBy: "u1",
      uploadedByName: "Yara Haddad",
      uploadedAt: "2026-04-14T10:20:00Z",
      previewable: false,
      removable: true,
    },
  ],
};

function Harness({ onPreview = () => {} }: { onPreview?: () => void }) {
  const documents = usePersonDocuments(scope);
  return <DocumentsPanel scope={scope} documents={documents} personName="Fatima" onPreview={onPreview} />;
}

function renderPanel(onPreview?: () => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <Harness onPreview={onPreview} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

const pdf = (name: string) => new File(["%PDF-1.7"], name, { type: "application/pdf" });

describe("DocumentsPanel", () => {
  beforeEach(() => {
    vi.mocked(documentsApi.listDocuments).mockResolvedValue([cv]);
  });

  it("says a file named like the CV becomes its next version, and keeps it apart on request", async () => {
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Fatima Al Mazrouei CV");

    await user.upload(screen.getByLabelText("Choose files to upload"), pdf("fatima_al_mazrouei_cv.pdf"));
    const tray = screen.getByRole("region", { name: "Ready to upload" });
    expect(within(tray).getByText("New version of Fatima_Al_Mazrouei_CV.pdf (v3 → v4)")).toBeInTheDocument();

    await user.click(within(tray).getByRole("switch", { name: "Keep as a separate document" }));
    expect(within(tray).getByText("New document, kept apart from the file of the same name")).toBeInTheDocument();

    vi.mocked(documentsApi.uploadDocument).mockResolvedValue({ outcome: "created", document: cv });
    await user.click(within(tray).getByRole("button", { name: "Upload 1 file" }));
    expect(documentsApi.uploadDocument).toHaveBeenCalledWith(scope, expect.any(File), {
      category: "cv",
      asNewDocument: true,
    });
    expect(await screen.findByText("1 new document — on the timeline")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Ready to upload" })).not.toBeInTheDocument();
  });

  it("keeps a file the server already holds in the tray, greyed, naming where it is", async () => {
    const user = userEvent.setup();
    vi.mocked(documentsApi.uploadDocument).mockRejectedValue(
      new ApiRequestError({
        code: "PERSON_DOCUMENT_DUPLICATE",
        detail: "That file is already on this candidate",
        status: 409,
        correlationId: "c",
        duplicateOf: { documentId: "d1", versionNo: 3 },
      }),
    );
    renderPanel();
    await screen.findByText("Fatima Al Mazrouei CV");

    await user.upload(screen.getByLabelText("Choose files to upload"), pdf("Board_pack.pdf"));
    await user.click(screen.getByRole("button", { name: "Upload 1 file" }));

    expect(await screen.findByText("Already uploaded as CV v3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nothing to upload" })).toBeDisabled();
  });

  it("sends Upload new version to that document whatever the file is called", async () => {
    const user = userEvent.setup();
    vi.mocked(documentsApi.uploadVersion).mockResolvedValue({ outcome: "new_version", document: cv });
    renderPanel();
    await user.click(await screen.findByRole("button", { name: "Upload new version" }));
    await user.upload(screen.getByLabelText("Choose the new version"), pdf("Updated.pdf"));

    expect(screen.getByText("New version of Fatima Al Mazrouei CV (v3 → v4)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Upload 1 file" }));
    expect(documentsApi.uploadVersion).toHaveBeenCalledWith(scope, "d1", expect.any(File));
  });

  it("refuses a file over the limit before sending it", async () => {
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Fatima Al Mazrouei CV");
    const huge = pdf("Huge.pdf");
    Object.defineProperty(huge, "size", { value: 21 * 1024 * 1024 });

    await user.upload(screen.getByLabelText("Choose files to upload"), huge);

    expect(screen.getByText("Larger than 20 MB")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nothing to upload" })).toBeDisabled();
  });

  it("offers a version's delete only to whoever may remove it", async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(await screen.findByRole("button", { name: "Show 2 versions" }));
    const versions = screen.getByRole("list", { name: "Versions" });
    const [latest, first] = within(versions).getAllByRole("listitem");
    expect(within(latest).getByRole("button", { name: "Delete" })).toBeDisabled();
    expect(within(first).getByRole("button", { name: "Delete" })).toBeEnabled();
    expect(within(first).queryByRole("button", { name: "Preview" })).not.toBeInTheDocument();
  });
});
