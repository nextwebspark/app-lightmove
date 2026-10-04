import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import * as documentsApi from "../../api/documentsApi";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument } from "../../api/types";
import { usePersonDocuments } from "../../lib/usePersonDocuments";
import { DocumentPreviewSheet, type PreviewTarget } from "./DocumentPreviewSheet";
import { PrimaryCvChip } from "./PrimaryCvChip";

vi.mock("../../api/documentsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof documentsApi>()),
  listDocuments: vi.fn(),
  documentContent: vi.fn(),
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
  createdAt: "2026-09-12T09:40:00Z",
  updatedAt: "2026-09-12T09:40:00Z",
  removable: true,
  versions: [
    {
      id: "v3",
      versionNo: 3,
      fileName: "Fatima_Al_Mazrouei_CV.pdf",
      contentType: "application/pdf",
      sizeBytes: 412_000,
      uploadedBy: "u1",
      uploadedByName: "Yara Haddad",
      uploadedAt: "2026-09-12T09:40:00Z",
      previewable: true,
      removable: true,
    },
  ],
};

function Harness() {
  const documents = usePersonDocuments(scope);
  const [target, setTarget] = useState<PreviewTarget | null>(null);
  return (
    <>
      <PrimaryCvChip
        documents={documents}
        onPreview={(document, version) => setTarget({ documentId: document.id, versionId: version.id })}
      />
      <DocumentPreviewSheet
        scope={scope}
        documents={documents}
        target={target}
        onTargetChange={setTarget}
        onClose={() => setTarget(null)}
      />
    </>
  );
}

function renderHarness() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <Harness />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("DocumentPreviewSheet", () => {
  const created: Blob[] = [];
  const revoked: string[] = [];

  beforeEach(() => {
    created.length = 0;
    revoked.length = 0;
    vi.mocked(documentsApi.listDocuments).mockResolvedValue([cv]);
    URL.createObjectURL = vi.fn((blob: Blob) => {
      created.push(blob);
      return `blob:preview-${created.length}`;
    });
    URL.revokeObjectURL = vi.fn((url: string) => {
      revoked.push(url);
    });
  });

  it("opens the primary CV from the header chip as a PDF, whatever type the bytes arrived as", async () => {
    const user = userEvent.setup();
    vi.mocked(documentsApi.documentContent).mockResolvedValue(new Blob(["<html>"], { type: "text/html" }));
    renderHarness();

    await user.click(await screen.findByRole("button", { name: /CV · v3/ }));

    const frame = await screen.findByTitle("Fatima Al Mazrouei CV, version 3");
    expect(frame).toHaveAttribute("src", "blob:preview-1");
    expect(created[0].type).toBe("application/pdf");
    expect(documentsApi.documentContent).toHaveBeenCalledWith(scope, "d1", "v3", true, expect.anything());
  });

  it("closes on Escape and lets the object URL go", async () => {
    const user = userEvent.setup();
    vi.mocked(documentsApi.documentContent).mockResolvedValue(new Blob(["%PDF"], { type: "application/pdf" }));
    renderHarness();
    await user.click(await screen.findByRole("button", { name: /CV · v3/ }));
    await screen.findByTitle("Fatima Al Mazrouei CV, version 3");

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog", { name: "Preview Fatima Al Mazrouei CV" })).not.toBeInTheDocument();
    expect(revoked).toEqual(["blob:preview-1"]);
  });
});
