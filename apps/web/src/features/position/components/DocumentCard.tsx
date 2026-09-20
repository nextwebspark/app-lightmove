import { useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Spinner } from "../../../components/ui";
import { FileDropzone } from "../../../components/ui/FileDropzone";
import { formatInstantDate } from "../../../lib/format";
import type { PositionDocument } from "../api/types";
import { BriefButton } from "./BriefFields";

const ACCEPT = ".pdf,.doc,.docx,.txt";

/**
 * The position description attached to a brief: a card naming the file once one is held, a dropzone
 * until then. The file is kept with the mandate and read back by its own download; nothing here reads
 * it into the fields — that is #393's, and lands on this card as its own button.
 */
export function DocumentCard({
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

  return (
    <div>
      {/* Replace keeps its own input: the dropzone is not drawn once a document is held, and Replace
          has to reach a file picker from a small button in the card. */}
      <input
        ref={input}
        type="file"
        accept={ACCEPT}
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
        <div className="flex flex-wrap items-center gap-3.5 rounded-[11px] bg-u-surface px-4 py-3.5 shadow-u-e1">
          <span className="grid size-9 flex-none place-items-center rounded-[8px] bg-u-accent-tint text-u-accent">
            <Icon d={ICONS.file} size={17} />
          </span>
          <div className="min-w-0 flex-[1_1_160px]">
            {/* A button, not a link: the bytes are fetched with the access token attached, so there is
                no URL a navigation could follow. */}
            <button
              type="button"
              onClick={onDownload}
              className="block max-w-full truncate text-start text-[13.5px] font-semibold text-u-text hover:text-u-accent hover:underline"
            >
              {document.fileName}
            </button>
            <span className="mt-0.5 block text-[11.5px] text-u-text3">
              {fileSizeOf(document.fileSize)} · added {formatInstantDate(document.uploadedAt)}
            </span>
          </div>
          <div className="ms-auto flex flex-none items-center gap-1">
            <BriefButton variant="link" onClick={() => input.current?.click()} disabled={uploading} className="text-u-text2">
              Replace
            </BriefButton>
            <BriefButton variant="link" onClick={onRemove} disabled={uploading} className="text-u-text2 hover:text-u-offlimits">
              Remove
            </BriefButton>
          </div>
        </div>
      ) : (
        <FileDropzone
          variant="uncava"
          accept={ACCEPT}
          label="Position description file"
          title="Attach the position description"
          hint="Kept with the mandate, so the brief and the document it came from stay together."
          disabled={uploading}
          onFile={onAttach}
        />
      )}

      {uploading && (
        <span className="mt-2.5 flex items-center gap-2 text-[11.5px] text-u-text3">
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
