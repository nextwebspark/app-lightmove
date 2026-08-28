import { useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Spinner } from "../../../components/ui";
import { formatInstantDate } from "../../../lib/format";
import type { PositionDocument } from "../api/types";

/**
 * The position description attached to a brief.
 *
 * The mockup promises this file auto-fills the form. It does not: the document is stored so it stays
 * with the mandate, and every field on this screen is typed in. The copy says so rather than
 * advertising an extraction nobody has built.
 */
export function PositionDocumentDropzone({
  document,
  uploading,
  onAttach,
  onRemove,
  onDownload,
}: {
  document: PositionDocument | null;
  uploading: boolean;
  onAttach: (file: File) => void;
  onRemove: () => void;
  onDownload: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const choose = () => input.current?.click();
  const take = (files: FileList | null) => {
    const file = files?.[0];
    if (file) onAttach(file);
  };

  return (
    <div>
      <input
        ref={input}
        type="file"
        accept=".pdf,.doc,.docx,.txt"
        aria-label="Position description file"
        onChange={(event) => {
          take(event.target.files);
          // Cleared so choosing the same file twice in a row still fires a change event.
          event.target.value = "";
        }}
        className="hidden"
      />

      {document ? (
        <div className="flex flex-wrap items-center gap-3 rounded-[10px] border border-line-soft bg-panel2 px-[15px] py-[13px]">
          <span className="flex size-[34px] flex-none items-center justify-center rounded-[7px] bg-sky-dim text-sky">
            <Icon d={ICONS.file} size={17} />
          </span>
          <div className="min-w-0 flex-[1_1_140px]">
            {/* A button, not a link: the bytes are fetched with the access token attached, so there
                is no URL a navigation could follow. */}
            <button
              type="button"
              onClick={onDownload}
              className="block max-w-full truncate text-start text-[13px] font-semibold text-text hover:text-sky hover:underline"
            >
              {document.fileName}
            </button>
            <span className="mt-px block font-mono text-[11.5px] text-text3">
              {fileSizeOf(document.fileSize)} · added {formatInstantDate(document.uploadedAt)}
            </span>
          </div>
          <div className="ms-auto flex flex-none gap-1.5">
            <button
              type="button"
              onClick={choose}
              disabled={uploading}
              className="rounded-[7px] border border-line px-2.5 py-[5px] text-[11.5px] font-medium text-text2 transition hover:border-text3 hover:text-text disabled:opacity-50"
            >
              Replace
            </button>
            <button
              type="button"
              onClick={onRemove}
              disabled={uploading}
              className="rounded-[7px] border border-line px-2.5 py-[5px] text-[11.5px] font-medium text-red transition hover:border-red disabled:opacity-50"
            >
              Remove
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={choose}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            take(event.dataTransfer.files);
          }}
          className={`flex min-h-[140px] w-full flex-col items-center justify-center gap-3 rounded-xl border-[1.5px] border-dashed bg-panel2 p-6 text-center transition ${
            dragging ? "border-sky brightness-105" : "border-sky/70 hover:brightness-105"
          }`}
        >
          <span className="grid size-10 flex-none place-items-center rounded-full bg-sky-dim text-sky">
            <Icon d={ICONS.uploadCloud} size={20} />
          </span>
          <span className="flex flex-col gap-1">
            <span className="text-[15px] font-semibold text-sky">
              Attach the position description
            </span>
            <span className="text-xs text-text3">
              Kept with the mandate so the brief and the document it came from stay together (PDF,
              Word or text)
            </span>
          </span>
        </button>
      )}

      {uploading && (
        <span className="mt-2.5 flex items-center gap-[7px] font-mono text-[11.5px] text-text3">
          <Spinner />
          Uploading…
        </span>
      )}
    </div>
  );
}

const KILOBYTE = 1024;

function fileSizeOf(bytes: number): string {
  const kilobytes = bytes / KILOBYTE;
  return kilobytes < KILOBYTE
    ? `${Math.max(1, Math.round(kilobytes))} KB`
    : `${(kilobytes / KILOBYTE).toFixed(1)} MB`;
}
