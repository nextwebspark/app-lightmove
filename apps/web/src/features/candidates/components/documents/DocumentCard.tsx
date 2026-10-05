import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Popover } from "../../../../components/ui/Popover";
import { cn } from "../../../../lib/cn";
import type { PersonDocument, PersonDocumentVersion } from "../../api/types";
import { shortWhen } from "../../lib/candidateActivity";
import { DOCUMENT_CATEGORIES, formatBytes } from "../../lib/documents";
import type { PersonDocuments } from "../../lib/usePersonDocuments";
import { CategoryPill, FileTypeBadge, VersionPill } from "./DocumentParts";

const ICON_BUTTON =
  "grid flex-none place-items-center rounded-[5px] p-1.5 text-u-text3 hover:bg-u-raised hover:text-u-text disabled:opacity-40";
const MENU_ITEM =
  "flex w-full items-center gap-2 rounded-[5px] px-2 py-1.5 text-start text-[12.5px] hover:bg-u-raised disabled:cursor-not-allowed disabled:opacity-45";

/**
 * One document as `Candidates.dc.html` draws it: its latest file, Preview for a PDF or image, Download,
 * Upload new version, a menu to rename, recategorise, mark the primary CV or delete, and its versions
 * on request. Deleting asks first, because the timeline keeps no copy of the name.
 */
export function DocumentCard({
  document,
  documents,
  onPreview,
  onUploadVersion,
}: {
  document: PersonDocument;
  documents: PersonDocuments;
  onPreview: (document: PersonDocument, version: PersonDocumentVersion) => void;
  onUploadVersion: (document: PersonDocument) => void;
}) {
  const [historyOpen, setHistoryOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(document.title);
  const [confirming, setConfirming] = useState<"document" | string | null>(null);
  const latest = document.versions[0];
  if (!latest) return null;

  const rename = () => {
    const title = draft.trim();
    setRenaming(false);
    if (title && title !== document.title) {
      documents.updating.mutate({ documentId: document.id, patch: { title } });
    }
  };

  return (
    <li
      className={cn(
        "rounded-[8px] border bg-u-surface px-3 py-2.5",
        document.primaryCv ? "border-u-accent/45" : "border-u-border",
      )}
    >
      <div className="flex items-start gap-2.5">
        <FileTypeBadge fileName={latest.fileName} />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 flex-wrap items-center gap-1.5">
            {document.primaryCv && (
              <span title="Primary CV — the one the header opens" className="flex-none text-u-accent">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-label="Primary CV">
                  <path d={ICONS.star} />
                </svg>
              </span>
            )}
            {renaming ? (
              <form
                className="flex min-w-0 flex-1 items-center gap-1.5"
                onSubmit={(event) => {
                  event.preventDefault();
                  rename();
                }}
              >
                <input
                  autoFocus
                  value={draft}
                  maxLength={255}
                  aria-label="Document title"
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Escape") {
                      event.stopPropagation();
                      setRenaming(false);
                    }
                  }}
                  className="min-w-0 flex-1 rounded-[6px] border border-u-accent bg-u-raised px-2 py-1 text-[13px] text-u-text outline-none"
                />
                <button
                  type="submit"
                  className="rounded-[6px] bg-u-accent-solid px-2.5 py-1 text-[12px] font-semibold text-white"
                >
                  Save
                </button>
              </form>
            ) : (
              <span className="min-w-0 truncate text-[13px] font-semibold text-u-text">{document.title}</span>
            )}
            <CategoryPill category={document.category} />
            <VersionPill versionNo={latest.versionNo} />
          </div>
          <p
            title={new Date(latest.uploadedAt).toLocaleString()}
            className="mt-1 font-mono text-[11px] text-u-text3"
          >
            {formatBytes(latest.sizeBytes)} · {latest.uploadedByName ?? "Someone"} · {shortWhen(latest.uploadedAt)}
          </p>
        </div>
        <div className="flex flex-none items-center gap-0.5">
          {latest.previewable && (
            <button
              type="button"
              title="Preview"
              aria-label={`Preview ${document.title}`}
              onClick={() => onPreview(document, latest)}
              className={ICON_BUTTON}
            >
              <Icon d={ICONS.eye} size={14} />
            </button>
          )}
          <button
            type="button"
            title="Download"
            aria-label={`Download ${document.title}`}
            onClick={() => void documents.download(document, latest)}
            className={ICON_BUTTON}
          >
            <Icon d={ICONS.importInto} size={14} />
          </button>
          <Popover
            label={`More for ${document.title}`}
            align="right"
            width={236}
            triggerClassName={ICON_BUTTON}
            trigger={() => <Icon d={ICONS.more} size={14} />}
          >
            {(close) => (
              <div role="menu" aria-label={`${document.title} actions`}>
                <button
                  type="button"
                  role="menuitem"
                  className={MENU_ITEM}
                  onClick={() => {
                    close();
                    setDraft(document.title);
                    setRenaming(true);
                  }}
                >
                  <Icon d={ICONS.pencil} size={13} className="text-u-text3" />
                  Rename
                </button>
                {document.category === "cv" && !document.primaryCv && (
                  <button
                    type="button"
                    role="menuitem"
                    className={MENU_ITEM}
                    onClick={() => {
                      close();
                      documents.updating.mutate({ documentId: document.id, patch: { primaryCv: true } });
                    }}
                  >
                    <Icon d={ICONS.star} size={13} className="text-u-accent" />
                    Make primary CV
                  </button>
                )}
                <p className="type-micro-label px-2 pb-1 pt-2 text-u-text3">Category</p>
                <div role="group" aria-label="Category" className="flex flex-wrap gap-1 px-2 pb-2">
                  {DOCUMENT_CATEGORIES.map((category) => (
                    <button
                      key={category.value}
                      type="button"
                      role="menuitemradio"
                      aria-checked={category.value === document.category}
                      onClick={() => {
                        close();
                        if (category.value !== document.category) {
                          documents.updating.mutate({ documentId: document.id, patch: { category: category.value } });
                        }
                      }}
                      className={cn(
                        "rounded-[4px] border px-1.5 py-px font-mono text-[9.5px] font-bold uppercase tracking-[0.05em]",
                        category.className,
                        category.value === document.category ? "border-current" : "border-transparent",
                      )}
                    >
                      {category.label}
                    </button>
                  ))}
                </div>
                <div className="my-0.5 border-t border-u-border" />
                <button
                  type="button"
                  role="menuitem"
                  disabled={!document.removable}
                  title={
                    document.removable
                      ? "Delete the document and every version"
                      : "Only whoever filed it, or a workspace admin, can delete it"
                  }
                  className={cn(MENU_ITEM, "text-u-offlimits")}
                  onClick={() => {
                    close();
                    setConfirming("document");
                  }}
                >
                  <Icon d={ICONS.trash} size={13} />
                  Delete document
                </button>
              </div>
            )}
          </Popover>
        </div>
      </div>

      {confirming === "document" ? (
        <ConfirmRow
          question="Delete this document and every version?"
          onYes={() => {
            setConfirming(null);
            documents.removing.mutate(document.id);
          }}
          onNo={() => setConfirming(null)}
        />
      ) : (
        <div className="ms-[42px] mt-1.5 flex flex-wrap items-center gap-1">
          <button
            type="button"
            onClick={() => onUploadVersion(document)}
            className="-ms-1.5 flex items-center gap-1 rounded-[5px] px-1.5 py-0.5 font-mono text-[11.5px] text-u-accent hover:bg-u-raised"
          >
            <Icon d={ICONS.exportOut} size={11} />
            Upload new version
          </button>
          {document.versions.length > 1 && (
            <button
              type="button"
              aria-expanded={historyOpen}
              onClick={() => setHistoryOpen((open) => !open)}
              className="flex items-center gap-1 rounded-[5px] px-1.5 py-0.5 font-mono text-[11.5px] text-u-text3 hover:bg-u-raised hover:text-u-text"
            >
              <Icon
                d={ICONS.chevronDown}
                size={11}
                className={cn("transition-transform", !historyOpen && "-rotate-90")}
              />
              {historyOpen ? "Hide" : "Show"} {document.versions.length} versions
            </button>
          )}
        </div>
      )}

      {historyOpen && (
        <ol aria-label="Versions" className="ms-[42px] mt-2 border-t border-u-border">
          {document.versions.map((version, index) => (
            <li key={version.id} className="flex items-center gap-2 border-b border-u-border py-1.5">
              <VersionPill versionNo={version.versionNo} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="min-w-0 truncate text-[12.5px] font-medium text-u-text">{version.fileName}</span>
                  {index === 0 && (
                    <span className="type-micro-label flex-none text-[9.5px] text-u-direct">Current</span>
                  )}
                </div>
                <p className="font-mono text-[11px] text-u-text3">
                  {formatBytes(version.sizeBytes)} · {version.uploadedByName ?? "Someone"} ·{" "}
                  {new Date(version.uploadedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}
                </p>
              </div>
              {confirming === version.id ? (
                <ConfirmRow
                  question={`Delete v${version.versionNo}?`}
                  onYes={() => {
                    setConfirming(null);
                    documents.removingVersion.mutate({ documentId: document.id, versionId: version.id });
                  }}
                  onNo={() => setConfirming(null)}
                />
              ) : (
                <>
                  {version.previewable && (
                    <button
                      type="button"
                      onClick={() => onPreview(document, version)}
                      className="rounded-[5px] px-1.5 py-1 font-mono text-[11.5px] text-u-text3 hover:bg-u-raised hover:text-u-text"
                    >
                      Preview
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => void documents.download(document, version)}
                    className="rounded-[5px] px-1.5 py-1 font-mono text-[11.5px] text-u-text3 hover:bg-u-raised hover:text-u-text"
                  >
                    Download
                  </button>
                  <button
                    type="button"
                    disabled={!version.removable}
                    title={
                      version.removable
                        ? "Delete this file"
                        : "Only whoever uploaded it, or a workspace admin, can delete it"
                    }
                    onClick={() => setConfirming(version.id)}
                    className="rounded-[5px] px-1.5 py-1 font-mono text-[11.5px] text-u-text3 hover:bg-u-raised hover:text-u-offlimits disabled:cursor-not-allowed disabled:opacity-45"
                  >
                    Delete
                  </button>
                </>
              )}
            </li>
          ))}
        </ol>
      )}
    </li>
  );
}

function ConfirmRow({ question, onYes, onNo }: { question: string; onYes: () => void; onNo: () => void }) {
  return (
    <span role="group" aria-label={question} className="ms-auto mt-1.5 flex items-center justify-end gap-2 font-mono text-[11.5px]">
      <span className="text-u-text2">{question}</span>
      <button type="button" onClick={onYes} className="font-semibold text-u-offlimits hover:underline">
        Yes, delete
      </button>
      <button type="button" onClick={onNo} className="text-u-text3 hover:text-u-text">
        No
      </button>
    </span>
  );
}
