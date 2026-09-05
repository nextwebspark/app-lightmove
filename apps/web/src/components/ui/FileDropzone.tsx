import { useRef, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";

/**
 * Pick a file, or drop one on it.
 *
 * <p>Extracted from the position-description uploader once a second screen needed the same thing.
 * The part worth having once is small and easy to get subtly wrong: a hidden input driven by a
 * button, a drag state that clears on leave *and* on drop, and — the one that is invisible until it
 * bites — clearing `event.target.value` after a change, so choosing the same file twice in a row
 * still fires. A user who picks the wrong file, closes the dialog and picks the right one from the
 * same folder hits that on their second attempt.
 *
 * <p>Presentation only: it hands a `File` to its caller and holds none of its own.
 */
export function FileDropzone({
  accept,
  label,
  title,
  hint,
  disabled = false,
  onFile,
}: {
  /** The input's `accept` list. A filter in the picker, never a check — the server decides. */
  accept: string;
  /** The input's accessible name. */
  label: string;
  title: ReactNode;
  hint: ReactNode;
  disabled?: boolean;
  onFile: (file: File) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const take = (files: FileList | null) => {
    const file = files?.[0];
    if (file) onFile(file);
  };

  return (
    <>
      <input
        ref={input}
        type="file"
        accept={accept}
        aria-label={label}
        onChange={(event) => {
          take(event.target.files);
          // Cleared so choosing the same file twice in a row still fires a change event.
          event.target.value = "";
        }}
        className="hidden"
      />
      <button
        type="button"
        disabled={disabled}
        onClick={() => input.current?.click()}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          if (!disabled) take(event.dataTransfer.files);
        }}
        className={`flex min-h-[140px] w-full flex-col items-center justify-center gap-3 rounded-xl border-[1.5px] border-dashed bg-panel2 p-6 text-center transition disabled:opacity-50 ${
          dragging ? "border-sky brightness-105" : "border-sky/70 hover:brightness-105"
        }`}
      >
        <span className="grid size-10 flex-none place-items-center rounded-full bg-sky-dim text-sky">
          <Icon d={ICONS.uploadCloud} size={20} />
        </span>
        <span className="flex flex-col gap-1">
          <span className="text-[15px] font-semibold text-sky">{title}</span>
          <span className="text-xs text-text3">{hint}</span>
        </span>
      </button>
    </>
  );
}
