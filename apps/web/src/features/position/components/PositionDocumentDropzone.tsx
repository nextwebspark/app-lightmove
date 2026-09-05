import { useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Spinner } from "../../../components/ui";
import { FileDropzone } from "../../../components/ui/FileDropzone";
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

  const choose = () => input.current?.click();

  return (
    <div>
      {/* The replace path keeps its own input: the dropzone below is not rendered once a document is
          attached, and Replace has to reach a file picker from a small button in the card. */}
      <input
        ref={input}
        type="file"
        accept=".pdf,.doc,.docx,.txt"
        aria-label="Replacement position description file"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onAttach(file);
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
        <FileDropzone
          accept=".pdf,.doc,.docx,.txt"
          label="Position description file"
          title="Attach the position description"
          hint="Kept with the mandate so the brief and the document it came from stay together (PDF, Word or text)"
          disabled={uploading}
          onFile={onAttach}
        />
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
