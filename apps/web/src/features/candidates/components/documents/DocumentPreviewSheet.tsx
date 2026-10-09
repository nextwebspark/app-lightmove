import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, Select } from "../../../../components/ui";
import { messageFor } from "../../../../lib/errorCodes";
import { useFocusTrap } from "../../../../lib/useFocusTrap";
import * as documentsApi from "../../api/documentsApi";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument, PersonDocumentVersion } from "../../api/types";
import { formatBytes } from "../../lib/documents";
import type { PersonDocuments } from "../../lib/usePersonDocuments";

export interface PreviewTarget {
  documentId: string;
  versionId: string;
}

/**
 * A wide panel centred over the drawer showing one version of a document: a PDF in a frame, an image as itself,
 * anything else as a Download. The bytes are fetched with the bearer token and shown from an object
 * URL, never a link anyone could share, and the server records the read.
 */
export function DocumentPreviewSheet({
  scope,
  documents,
  target,
  onTargetChange,
  onClose,
}: {
  scope: DocumentScope;
  documents: PersonDocuments;
  target: PreviewTarget | null;
  onTargetChange: (target: PreviewTarget) => void;
  onClose: () => void;
}) {
  const document = documents.documents.data?.find((candidate) => candidate.id === target?.documentId) ?? null;
  const version = document?.versions.find((candidate) => candidate.id === target?.versionId) ?? null;
  const shown = document !== null && version !== null;
  const panelRef = useRef<HTMLElement>(null);
  useFocusTrap(panelRef, shown, { startAt: "panel" });

  useEffect(() => {
    if (!shown) return;
    // Captured on the window rather than joining useEscapeKey's stack: the drawer beneath re-registers
    // its handler whenever its page re-renders, which would put it above this sheet and close both.
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.stopPropagation();
      onClose();
    };
    window.addEventListener("keydown", closeOnEscape, true);
    return () => window.removeEventListener("keydown", closeOnEscape, true);
  }, [shown, onClose]);

  if (!document || !version) return null;

  // Portalled: the drawer it opens from animates with a transform, which makes it the containing block
  // for anything `fixed` inside it, so the panel opened centred in the drawer instead of on the screen.
  return createPortal(
    <div className="fixed inset-0 z-[100] grid place-items-center p-2.5 sm:p-6">
      <div className="absolute inset-0 bg-u-scrim" onClick={onClose} />
      <section
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={`Preview ${document.title}`}
        className="relative flex h-full max-h-[92dvh] w-full max-w-[1020px] animate-fade-up flex-col rounded-[10px] border border-u-border-strong bg-u-surface shadow-u-e3 outline-none"
      >
        <header className="flex flex-none items-center gap-3 border-b border-u-border px-4 py-3">
          <span className="grid size-[30px] flex-none place-items-center rounded-[7px] bg-u-raised text-u-text3">
            <Icon d={ICONS.fileText} size={15} />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-[14px] font-semibold text-u-text">{document.title}</h2>
            <p className="truncate font-mono text-[11.5px] text-u-text3">
              v{version.versionNo} · {version.fileName} · {formatBytes(version.sizeBytes)} ·{" "}
              {version.uploadedByName ?? "Someone"},{" "}
              {new Date(version.uploadedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}
            </p>
          </div>
          {document.versions.length > 1 && (
            <Select
              density="compact"
              aria-label="Version"
              value={version.id}
              onChange={(event) => onTargetChange({ documentId: document.id, versionId: event.target.value })}
              className="hidden w-auto flex-none sm:block"
            >
              {document.versions.map((option, index) => (
                <option key={option.id} value={option.id}>
                  Version {option.versionNo}
                  {index === 0 ? " (current)" : ""}
                </option>
              ))}
            </Select>
          )}
          <Button
            type="button"
            variant="secondary"
            className="flex-none px-3 py-1.5 text-[13px]"
            onClick={() => void documents.download(document, version)}
          >
            <Icon d={ICONS.importInto} size={13} />
            Download
          </Button>
          <button
            type="button"
            aria-label="Close preview"
            onClick={onClose}
            className="grid flex-none place-items-center rounded-[6px] p-1.5 text-u-text3 hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={16} />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-auto bg-u-bg">
          {version.previewable ? (
            <PreviewBody scope={scope} document={document} version={version} />
          ) : (
            <div className="grid h-full place-items-center p-6 text-center">
              <div>
                <p className="text-[14px] font-semibold text-u-text">This file can’t be previewed yet</p>
                <p className="mb-3.5 mt-1 font-mono text-[12px] text-u-text3">
                  Download it to read it. PDFs and images open here.
                </p>
                <Button type="button" className="mx-auto" onClick={() => void documents.download(document, version)}>
                  Download
                </Button>
              </div>
            </div>
          )}
        </div>
        <p className="flex-none border-t border-u-border px-4 py-2 font-mono text-[11px] text-u-text3">
          Served by Uncava itself, never through a shareable link. Opening and downloading are both recorded.
        </p>
      </section>
    </div>,
    window.document.body,
  );
}

function PreviewBody({
  scope,
  document,
  version,
}: {
  scope: DocumentScope;
  document: PersonDocument;
  version: PersonDocumentVersion;
}) {
  const content = useQuery({
    queryKey: [...documentsApi.DOCUMENTS_KEY(scope), "preview", version.id],
    queryFn: ({ signal }) => documentsApi.documentContent(scope, document.id, version.id, true, signal),
    // Each open is a read the server audits, and a CV should not outlive the sheet in memory.
    gcTime: 0,
    staleTime: Infinity,
    retry: false,
  });
  const isPdf = version.contentType === "application/pdf";
  const url = useObjectUrl(content.data ?? null, isPdf ? "application/pdf" : version.contentType);

  if (content.isError) {
    return <p className="p-6 text-[13px] text-u-text3">{messageFor(content.error)}</p>;
  }
  if (!url) {
    return <p className="p-6 text-[13px] text-u-text3">Loading…</p>;
  }
  if (isPdf) {
    // Not sandboxed: Chrome refuses to run its PDF viewer inside a sandboxed frame. The blob is retyped
    // as a PDF above, so whatever the bytes were it can only ever be rendered as one.
    return <iframe title={`${document.title}, version ${version.versionNo}`} src={url} className="size-full border-0" />;
  }
  return (
    <div className="grid min-h-full place-items-center p-6">
      <img src={url} alt={`${document.title}, version ${version.versionNo}`} className="max-w-full rounded-[4px] shadow-u-e3" />
    </div>
  );
}

/** An object URL for the blob, retyped, and revoked when it changes or the sheet closes. */
function useObjectUrl(blob: Blob | null, type: string): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blob) {
      setUrl(null);
      return;
    }
    const next = URL.createObjectURL(blob.slice(0, blob.size, type));
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [blob, type]);
  return url;
}
