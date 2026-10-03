import { useRef, useState, type DragEvent, type ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, Select, Toggle, useToast } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as documentsApi from "../../api/documentsApi";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument, PersonDocumentCategory, PersonDocumentVersion } from "../../api/types";
import {
  categoryOf,
  DOCUMENT_ACCEPT,
  DOCUMENT_CATEGORIES,
  formatBytes,
  groupDocuments,
  guessCategory,
  MAX_DOCUMENT_BYTES,
  planUpload,
} from "../../lib/documents";
import type { PersonDocuments } from "../../lib/usePersonDocuments";
import { DocumentCard } from "./DocumentCard";
import { FileTypeBadge } from "./DocumentParts";

interface TrayItem {
  key: string;
  file: File;
  category: PersonDocumentCategory;
  asNewDocument: boolean;
  /** Set by a card's Upload new version: that document's next version, whatever the file is called. */
  forDocumentId: string | null;
  state: "ready" | "duplicate" | "failed";
  message: string | null;
  duplicateOf: { documentId: string; versionNo: number } | null;
}

let trayKeys = 0;

/**
 * A person's documents as `Candidates.dc.html` draws its Documents tab, and the executive drawer's
 * section as a compact copy: the whole panel takes dropped files, a tray says what each file will
 * become before anything is sent, and the cards are grouped by category, CV first.
 *
 * <p>The tray's outcome is worked out here from the names already on the person — the server decides
 * the same way — but a duplicate is only known by its bytes, so that one is the server's 409.
 */
export function DocumentsPanel({
  scope,
  documents,
  personName,
  compact = false,
  onPreview,
}: {
  scope: DocumentScope;
  documents: PersonDocuments;
  personName: string;
  compact?: boolean;
  onPreview: (document: PersonDocument, version: PersonDocumentVersion) => void;
}) {
  const toast = useToast();
  const filesInput = useRef<HTMLInputElement>(null);
  const versionInput = useRef<HTMLInputElement>(null);
  const versionTarget = useRef<PersonDocument | null>(null);
  const [tray, setTray] = useState<TrayItem[]>([]);
  const [dragging, setDragging] = useState(false);
  const [sending, setSending] = useState(false);
  const list = documents.documents.data ?? [];

  const add = (files: FileList | File[] | null, forDocument: PersonDocument | null = null) => {
    const picked = [...(files ?? [])];
    if (picked.length === 0) return;
    setTray((current) => [
      ...current,
      ...picked.map((file) => {
        const tooLarge = file.size > MAX_DOCUMENT_BYTES;
        return {
          key: `tray-${++trayKeys}`,
          file,
          category: forDocument?.category ?? guessCategory(file.name),
          asNewDocument: false,
          forDocumentId: forDocument?.id ?? null,
          state: tooLarge ? ("failed" as const) : ("ready" as const),
          message: tooLarge ? `Larger than ${formatBytes(MAX_DOCUMENT_BYTES)}` : null,
          duplicateOf: null,
        };
      }),
    ]);
  };

  const patch = (key: string, change: Partial<TrayItem>) =>
    setTray((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));

  const send = async () => {
    setSending(true);
    let created = 0;
    let versions = 0;
    for (const item of tray.filter((entry) => entry.state === "ready")) {
      try {
        const result = item.forDocumentId
          ? await documentsApi.uploadVersion(scope, item.forDocumentId, item.file)
          : await documentsApi.uploadDocument(scope, item.file, {
              category: item.category,
              asNewDocument: item.asNewDocument,
            });
        if (result.outcome === "created") created += 1;
        else versions += 1;
        setTray((current) => current.filter((entry) => entry.key !== item.key));
      } catch (error) {
        if (error instanceof ApiRequestError && error.code === "PERSON_DOCUMENT_DUPLICATE") {
          patch(item.key, { state: "duplicate", duplicateOf: error.problem.duplicateOf ?? null });
        } else {
          patch(item.key, { state: "failed", message: messageFor(error) });
        }
      }
    }
    setSending(false);
    documents.refresh();
    const parts = [
      created > 0 ? `${created} new ${created === 1 ? "document" : "documents"}` : null,
      versions > 0 ? `${versions} new ${versions === 1 ? "version" : "versions"}` : null,
    ].filter(Boolean);
    if (parts.length > 0) toast(`${parts.join(" and ")} — on the timeline`);
  };

  const handleDragOver = (event: DragEvent) => {
    if (!event.dataTransfer.types.includes("Files")) return;
    event.preventDefault();
    setDragging(true);
  };
  const handleDragLeave = (event: DragEvent) => {
    if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
    setDragging(false);
  };
  const handleDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    add(event.dataTransfer.files);
  };

  const ready = tray.filter((item) => item.state === "ready").length;
  const groups = groupDocuments(list);

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={cn("relative", compact ? "pb-3" : "min-h-full pb-2")}
    >
      <input
        ref={filesInput}
        type="file"
        multiple
        accept={DOCUMENT_ACCEPT}
        aria-label="Choose files to upload"
        className="hidden"
        onChange={(event) => {
          add(event.target.files);
          // Cleared so choosing the same file twice in a row still fires a change event.
          event.target.value = "";
        }}
      />
      <input
        ref={versionInput}
        type="file"
        accept={DOCUMENT_ACCEPT}
        aria-label="Choose the new version"
        className="hidden"
        onChange={(event) => {
          add(event.target.files, versionTarget.current);
          event.target.value = "";
        }}
      />

      <div
        className={cn(
          "flex items-center gap-3 rounded-[10px] border-[1.5px] border-dashed border-u-border-strong bg-u-raised",
          compact ? "px-2.5 py-2" : "mt-0 px-3.5 py-3",
        )}
      >
        {!compact && (
          <span className="grid size-[34px] flex-none place-items-center rounded-[8px] bg-u-surface text-u-text3">
            <Icon d={ICONS.uploadCloud} size={17} />
          </span>
        )}
        <div className="min-w-0 flex-1">
          {compact ? (
            <p className="font-mono text-[11.5px] text-u-text3">Drop a CV, cover letter or reference here</p>
          ) : (
            <>
              <p className="text-[13px] font-semibold text-u-text">Drop files anywhere on this tab</p>
              <p className="mt-0.5 font-mono text-[11.5px] text-u-text3">
                PDF, Word, ODT, RTF, TXT, PNG or JPEG · up to {formatBytes(MAX_DOCUMENT_BYTES)} each
              </p>
            </>
          )}
        </div>
        <Button
          type="button"
          className={cn("flex-none", compact ? "px-2.5 py-1 text-[12px]" : "px-3 py-2 text-[13px]")}
          onClick={() => filesInput.current?.click()}
        >
          <Icon d={ICONS.exportOut} size={13} />
          Upload
        </Button>
      </div>

      {tray.length > 0 && (
        <section aria-label="Ready to upload" className="mt-3 rounded-[10px] border border-u-border-strong bg-u-surface shadow-u-e3">
          <div className="flex items-center justify-between px-3 py-2.5">
            <span className="type-micro-label text-u-text3">Ready to upload</span>
            <button
              type="button"
              disabled={sending}
              onClick={() => setTray([])}
              className="font-mono text-[12px] text-u-text3 hover:text-u-text disabled:opacity-50"
            >
              Clear
            </button>
          </div>
          <ul>
            {tray.map((item) => (
              <TrayRow
                key={item.key}
                item={item}
                documents={list}
                disabled={sending}
                onCategory={(category) => patch(item.key, { category })}
                onKeepApart={(asNewDocument) => patch(item.key, { asNewDocument })}
                onRemove={() => setTray((current) => current.filter((entry) => entry.key !== item.key))}
              />
            ))}
          </ul>
          <div className="flex items-center gap-3 border-t border-u-border px-3 py-2.5">
            <span className="min-w-0 flex-1 font-mono text-[11px]/[1.45] text-u-text3">
              {tray.some((item) => item.state === "duplicate")
                ? "A file already on this person is skipped — the same bytes are never stored twice."
                : "A file with the same name as one already here becomes its next version."}
            </span>
            <Button
              type="button"
              className="flex-none px-3.5 py-2 text-[13px]"
              disabled={ready === 0}
              loading={sending}
              onClick={() => void send()}
            >
              {ready === 0 ? "Nothing to upload" : `Upload ${ready === 1 ? "1 file" : `${ready} files`}`}
            </Button>
          </div>
        </section>
      )}

      {documents.documents.isError ? (
        <p className="mt-4 text-[13px] text-u-text3">{messageFor(documents.documents.error)}</p>
      ) : documents.documents.isPending ? (
        <p className="mt-4 text-[13px] text-u-text3">Loading…</p>
      ) : list.length === 0 ? (
        <EmptyDocuments compact={compact} />
      ) : (
        groups.map((group) => (
          <section key={group.category.value} className={compact ? "mt-2" : "mt-4"}>
            {!compact && (
              <h3 className="type-micro-label mb-2 flex items-center gap-2 text-u-text3">
                {group.category.label}
                <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9.5px]">
                  {group.documents.length}
                </span>
              </h3>
            )}
            <ul className="flex flex-col gap-2">
              {group.documents.map((document) => (
                <DocumentCard
                  key={document.id}
                  document={document}
                  documents={documents}
                  onPreview={onPreview}
                  onUploadVersion={(target) => {
                    versionTarget.current = target;
                    versionInput.current?.click();
                  }}
                />
              ))}
            </ul>
          </section>
        ))
      )}

      {!compact && (
        <p className="mt-3.5 font-mono text-[11px] text-u-text3">
          Shared with your whole team. Client contacts never see documents, and every download is recorded.
        </p>
      )}

      {dragging && (
        <div className="pointer-events-none absolute inset-0 z-10 grid place-items-center rounded-[12px] border-2 border-dashed border-u-accent bg-u-accent-tint/80 backdrop-blur-[1px]">
          <div className="text-center">
            <Icon d={ICONS.uploadCloud} size={26} className="mx-auto text-u-accent" />
            <p className="mt-2 text-[14px] font-semibold text-u-text">Drop to add to {personName}’s documents</p>
            <p className="mt-0.5 font-mono text-[11.5px] text-u-text3">
              You choose each file’s category before anything is uploaded
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function TrayRow({
  item,
  documents,
  disabled,
  onCategory,
  onKeepApart,
  onRemove,
}: {
  item: TrayItem;
  documents: readonly PersonDocument[];
  disabled: boolean;
  onCategory: (category: PersonDocumentCategory) => void;
  onKeepApart: (asNewDocument: boolean) => void;
  onRemove: () => void;
}) {
  const outcome = outcomeOf(item, documents);
  return (
    <li className={cn("border-t border-u-border px-3 py-2.5", item.state === "duplicate" && "opacity-55")}>
      <div className="flex items-center gap-2.5">
        <FileTypeBadge fileName={item.file.name} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[12.5px] font-semibold text-u-text">{item.file.name}</p>
          <p className="mt-0.5 font-mono text-[11px] text-u-text3">{formatBytes(item.file.size)}</p>
        </div>
        <Select
          density="compact"
          aria-label={`Category of ${item.file.name}`}
          value={item.category}
          disabled={disabled || outcome.becomesVersion || item.state !== "ready"}
          title={outcome.becomesVersion ? "A new version keeps its document's category" : undefined}
          onChange={(event) => onCategory(event.target.value as PersonDocumentCategory)}
          className="w-auto flex-none"
        >
          {DOCUMENT_CATEGORIES.map((category) => (
            <option key={category.value} value={category.value}>
              {category.label}
            </option>
          ))}
        </Select>
        <button
          type="button"
          disabled={disabled}
          aria-label={`Remove ${item.file.name}`}
          onClick={onRemove}
          className="grid flex-none place-items-center rounded-[5px] p-1 text-u-text3 hover:bg-u-raised hover:text-u-text"
        >
          <Icon d={ICONS.close} size={13} />
        </button>
      </div>
      <div className="ms-[42px] mt-1.5 flex flex-wrap items-center justify-between gap-2">
        <span className={cn("flex min-w-0 items-center gap-1.5 text-[12px] font-medium", outcome.className)}>
          <Icon d={outcome.icon} size={12} className="flex-none" />
          <span className="min-w-0 truncate">{outcome.line}</span>
        </span>
        {outcome.canKeepApart && (
          <label className="flex items-center gap-2 font-mono text-[11.5px] text-u-text2">
            <Toggle
              checked={item.asNewDocument}
              disabled={disabled}
              onChange={onKeepApart}
              label="Keep as a separate document"
            />
            Keep as a separate document
          </label>
        )}
      </div>
    </li>
  );
}

function outcomeOf(
  item: TrayItem,
  documents: readonly PersonDocument[],
): { line: ReactNode; icon: string; className: string; canKeepApart: boolean; becomesVersion: boolean } {
  if (item.state === "failed") {
    return { line: item.message, icon: ICONS.warning, className: "text-u-offlimits", canKeepApart: false, becomesVersion: false };
  }
  if (item.state === "duplicate") {
    const held = documents.find((document) => document.id === item.duplicateOf?.documentId);
    const line = held && item.duplicateOf
      ? `Already uploaded as ${categoryOf(held.category).label} v${item.duplicateOf.versionNo}`
      : "Already on this person";
    return { line, icon: ICONS.ban, className: "text-u-text3", canKeepApart: false, becomesVersion: false };
  }
  const target = item.forDocumentId ? documents.find((document) => document.id === item.forDocumentId) : null;
  if (target?.versions[0]) {
    const current = target.versions[0].versionNo;
    return {
      line: `New version of ${target.title} (v${current} → v${current + 1})`,
      icon: ICONS.fileText,
      className: "text-u-accent",
      canKeepApart: false,
      becomesVersion: true,
    };
  }
  const plan = planUpload(item.file.name, documents, item.asNewDocument);
  if (plan.kind === "version") {
    return {
      line: `New version of ${plan.document.versions[0].fileName} (v${plan.next - 1} → v${plan.next})`,
      icon: ICONS.fileText,
      className: "text-u-accent",
      canKeepApart: true,
      becomesVersion: true,
    };
  }
  return {
    line: plan.sameNameAs ? "New document, kept apart from the file of the same name" : "New document",
    icon: ICONS.plus,
    className: "text-u-direct",
    canKeepApart: plan.sameNameAs !== null,
    becomesVersion: false,
  };
}

function EmptyDocuments({ compact }: { compact: boolean }) {
  if (compact) {
    return (
      <p className="mt-2.5 font-mono text-[12px] text-u-text3">
        No documents yet. A CV filed here is shared with the team on every position this person is in.
      </p>
    );
  }
  return (
    <div className="px-3 pb-5 pt-9 text-center">
      <span className="mx-auto grid size-10 place-items-center rounded-[10px] bg-u-raised text-u-text3">
        <Icon d={ICONS.fileText} size={18} />
      </span>
      <p className="mt-2.5 text-[13px] font-semibold text-u-text">No documents yet</p>
      <p className="mx-auto mt-1 max-w-[340px] font-mono text-[12px]/[1.5] text-u-text3">
        A CV, cover letter or reference filed here is shared by every position this person is in. The first
        CV becomes the one the header opens.
      </p>
    </div>
  );
}
