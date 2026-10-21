import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import * as documentsApi from "../../candidates/api/documentsApi";
import { DOCUMENT_ACCEPT, formatBytes } from "../../candidates/lib/documents";
import type { AttachmentLimits } from "../api/conversationApi";
import {
  attachmentProblemOf,
  documentOf,
  totalBytesOf,
  uploadOf,
  withAttachment,
  type ComposerAttachment,
} from "../lib/composerAttachments";

/**
 * The composer's Attach control and the files it holds: one of the person's own documents, or a file from this
 * computer. The server reads each file's bytes and decides; this only says beforehand what it would refuse.
 */
export function AttachButton({
  projectId,
  candidateId,
  attachments,
  limits,
  disabled,
  onChange,
}: {
  projectId: string;
  candidateId: string;
  attachments: readonly ComposerAttachment[];
  limits: AttachmentLimits;
  disabled: boolean;
  onChange: (next: ComposerAttachment[]) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const isFull = attachments.length >= limits.maxFiles;

  return (
    <>
      <input
        ref={input}
        type="file"
        multiple
        hidden
        accept={DOCUMENT_ACCEPT}
        aria-label="Attach a file from this computer"
        onChange={(event) => {
          const picked = Array.from(event.target.files ?? []);
          event.target.value = "";
          onChange(picked.reduce<ComposerAttachment[]>((held, file) => withAttachment(held, uploadOf(file)), [...attachments]));
        }}
      />
      <Popover
        align="left"
        placement="auto"
        width={300}
        hasPopup="dialog"
        disabled={disabled || isFull}
        triggerClassName="inline-flex items-center gap-1 rounded-u-control px-2 py-1 text-note font-medium text-u-text2 hover:bg-u-raised hover:text-u-text disabled:opacity-50"
        trigger={() => (
          <>
            <Icon d={ICONS.paperclip} size={13} />
            Attach
          </>
        )}
      >
        {(close) => (
          <AttachChoices
            projectId={projectId}
            candidateId={candidateId}
            attachments={attachments}
            onPick={(attachment) => {
              onChange(withAttachment(attachments, attachment));
              close();
            }}
            onUpload={() => {
              close();
              input.current?.click();
            }}
          />
        )}
      </Popover>
    </>
  );
}

/** Mounted only while Attach is open, so their documents are read when someone looks for one, not with every composer. */
function AttachChoices({
  projectId,
  candidateId,
  attachments,
  onPick,
  onUpload,
}: {
  projectId: string;
  candidateId: string;
  attachments: readonly ComposerAttachment[];
  onPick: (attachment: ComposerAttachment) => void;
  onUpload: () => void;
}) {
  const scope = { kind: "position", projectId, candidateId } as const;
  const documents = useQuery({
    queryKey: documentsApi.DOCUMENTS_KEY(scope),
    queryFn: ({ signal }) => documentsApi.listDocuments(scope, signal),
  });
  const latestVersions = (documents.data ?? []).flatMap((document) =>
    document.versions.length > 0 ? [{ document, version: document.versions[0] }] : [],
  );
  return (
    <div className="flex flex-col py-1.5">
      <h3 id="attach-their-documents" className="px-2 pb-1 pt-1.5 type-micro-label text-u-text3">
        Their documents
      </h3>
      {documents.isPending ? (
        <p className="px-2 py-1.5 text-note text-u-text3">Reading their documents…</p>
      ) : documents.isError ? (
        <p className="px-2 py-1.5 text-note text-u-text3">Their documents couldn&rsquo;t be read just now.</p>
      ) : latestVersions.length === 0 ? (
        <p className="px-2 py-1.5 text-note text-u-text3">No documents on file for them yet.</p>
      ) : (
        <ul aria-labelledby="attach-their-documents" className="flex flex-col">
          {latestVersions.map(({ document, version }) => {
            const attached = attachments.some((attachment) => attachment.key === `document:${version.id}`);
            return (
              <li key={version.id}>
                <button
                  type="button"
                  disabled={attached}
                  className="flex w-full items-center gap-2 px-2 py-1.5 text-start text-note text-u-text2 hover:bg-u-raised hover:text-u-text disabled:opacity-50"
                  onClick={() => onPick(documentOf(version.id, version.fileName, version.sizeBytes))}
                >
                  <Icon d={ICONS.fileText} size={13} className="flex-none text-u-text3" />
                  <span className="min-w-0 flex-1 truncate">{document.title}</span>
                  <span className="flex-none font-mono text-meta text-u-text3">
                    {attached ? "Attached" : formatBytes(version.sizeBytes)}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="mt-1 border-t border-u-border pt-1">
        <button
          type="button"
          className="flex w-full items-center gap-2 px-2 py-1.5 text-start text-note text-u-text2 hover:bg-u-raised hover:text-u-text"
          onClick={onUpload}
        >
          <Icon d={ICONS.uploadCloud} size={13} className="flex-none text-u-text3" />
          Upload from this computer
        </button>
      </div>
    </div>
  );
}

/** The files held, each with its size and a remove, and what the server would refuse about them. */
export function AttachmentChips({
  attachments,
  limits,
  onChange,
}: {
  attachments: readonly ComposerAttachment[];
  limits: AttachmentLimits;
  onChange: (next: ComposerAttachment[]) => void;
}) {
  if (attachments.length === 0) return null;
  const problem = attachmentProblemOf(attachments, limits);
  return (
    <div className="mt-2">
      <ul aria-label="Attachments" className="flex flex-wrap gap-1.5">
        {attachments.map((attachment) => (
          <li
            key={attachment.key}
            className="inline-flex max-w-full items-center gap-1.5 rounded-u-chip border border-u-border bg-u-raised py-0.5 pe-1 ps-2 text-note text-u-text2"
          >
            <Icon d={ICONS.paperclip} size={12} className="flex-none text-u-text3" />
            <span className="min-w-0 truncate">{attachment.fileName}</span>
            <span className="flex-none font-mono text-meta text-u-text3">{formatBytes(attachment.sizeBytes)}</span>
            <button
              type="button"
              aria-label={`Remove ${attachment.fileName}`}
              className="flex-none rounded-u-control p-0.5 text-u-text3 hover:text-u-text"
              onClick={() => onChange(attachments.filter((held) => held.key !== attachment.key))}
            >
              <Icon d={ICONS.close} size={11} />
            </button>
          </li>
        ))}
      </ul>
      {problem ? (
        <p role="alert" className="mt-1 text-meta text-u-offlimits">
          {problem}
        </p>
      ) : (
        <p className="mt-1 text-meta text-u-text3">
          {formatBytes(totalBytesOf(attachments))} of {formatBytes(limits.maxTotalBytes)}
        </p>
      )}
    </div>
  );
}
