import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as positionApi from "../api/positionApi";
import type { BriefDocument } from "../api/types";

const ACCEPTED_TYPES = ".pdf,.doc,.docx,.txt";

/**
 * The Position Description upload — Project.dc.html's mockup widget (dashed dropzone → file chip →
 * parsing spinner), wired to a real upload instead of a `setTimeout`. Owns its own mutation: there is
 * no draft state to lift, only a one-shot "upload, extraction lands on the brief" action.
 */
export function BriefDocumentCard({
  projectId,
  briefDocument,
  disabled,
}: {
  projectId: string;
  briefDocument: BriefDocument | null;
  disabled: boolean;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  // The server has nothing to say about the file until parsing finishes — this is what lets the chip
  // and spinner appear the instant a file is chosen, rather than only once the upload resolves.
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const key = positionApi.POSITION_KEY(projectId);

  const upload = useMutation({
    mutationFn: (file: File) => positionApi.uploadBriefDocument(projectId, file),
    onSuccess: (position) => {
      queryClient.setQueryData(key, position);
      toast("Parsed — details extracted to help fill this page");
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: () => setPendingFile(null),
  });

  const remove = useMutation({
    mutationFn: () => positionApi.removeBriefDocument(projectId),
    onSuccess: (position) => queryClient.setQueryData(key, position),
    onError: (error) => toast(messageFor(error)),
  });

  const chooseFile = (file: File | undefined) => {
    if (!file) return;
    setPendingFile(file);
    upload.mutate(file);
  };

  const displayedName = upload.isPending ? pendingFile?.name : briefDocument?.fileName;
  const displayedMeta = upload.isPending
    ? null
    : briefDocument
      ? `${formatFileSize(briefDocument.fileSize)} · added ${formatUploadedAt(briefDocument.uploadedAt)}`
      : null;

  return (
    <div className="mb-[22px]">
      <div className="mb-3 flex items-baseline gap-2">
        <span className="text-[15px] font-semibold text-text">Position Description</span>
        <span className="font-mono text-[11.5px] text-text3">
          source document — parsed automatically to help fill this page
        </span>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPTED_TYPES}
        aria-label="Upload position description"
        className="hidden"
        onChange={(e) => {
          chooseFile(e.target.files?.[0]);
          e.target.value = "";
        }}
      />

      {!displayedName ? (
        <div
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-disabled={disabled}
          onClick={() => !disabled && inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            if (!disabled) setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            if (!disabled) chooseFile(e.dataTransfer.files?.[0]);
          }}
          className={`rounded-[10px] border-[1.5px] border-dashed p-6 text-center font-mono text-[12.5px] transition ${
            disabled
              ? "cursor-not-allowed border-line text-text3"
              : dragOver
                ? "cursor-pointer border-sky bg-sky-dim text-sky"
                : "cursor-pointer border-line text-text3 hover:border-sky hover:bg-sky-dim hover:text-sky"
          }`}
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            className="mx-auto mb-1.5"
            aria-hidden="true"
          >
            <path d="M12 3v12m0 0-4-4m4 4 4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
          </svg>
          <div>Drop a PD here, or click to browse</div>
          <div className="mt-0.5 text-[11px]">
            PDF, Word, or text — parsed automatically to help fill this page
          </div>
        </div>
      ) : (
        <div>
          <div className="flex items-center gap-3 rounded-[10px] border border-line-soft bg-panel2 px-3.5 py-3">
            <div className="flex size-[34px] flex-none items-center justify-center rounded-[7px] bg-sky-dim text-sky">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6" />
              </svg>
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-semibold text-text">{displayedName}</div>
              {displayedMeta && (
                <div className="mt-px font-mono text-[11.5px] text-text3">{displayedMeta}</div>
              )}
            </div>
            {!disabled && !upload.isPending && (
              <div className="flex flex-none gap-1.5">
                <button
                  type="button"
                  onClick={() => inputRef.current?.click()}
                  disabled={remove.isPending}
                  className="rounded-md border border-line px-2.5 py-1.5 font-mono text-[11.5px] text-text2 transition hover:border-text3 hover:text-text disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Replace
                </button>
                <button
                  type="button"
                  onClick={() => remove.mutate()}
                  disabled={remove.isPending}
                  className="rounded-md border border-line px-2.5 py-1.5 font-mono text-[11.5px] text-text2 transition hover:border-red hover:text-red disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Remove
                </button>
              </div>
            )}
          </div>
          {upload.isPending && (
            <div className="mt-2.5 flex items-center gap-1.5 font-mono text-[11.5px] text-text3">
              <Spinner />
              Parsing document and extracting details…
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function formatFileSize(bytes: number): string {
  const kb = bytes / 1024;
  return kb < 1024 ? `${Math.round(kb)} KB` : `${(kb / 1024).toFixed(1)} MB`;
}

function formatUploadedAt(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}
