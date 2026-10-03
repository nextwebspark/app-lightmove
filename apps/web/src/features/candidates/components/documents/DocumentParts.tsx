import { cn } from "../../../../lib/cn";
import type { PersonDocumentCategory } from "../../api/types";
import { categoryOf, extensionOf } from "../../lib/documents";

const WORD_FILES = new Set(["doc", "docx", "odt", "rtf"]);

/** The file's type as a small page tile: red for a PDF, accent for a word-processor file, green otherwise. */
export function FileTypeBadge({ fileName, compact = false }: { fileName: string; compact?: boolean }) {
  const extension = extensionOf(fileName);
  return (
    <span
      aria-hidden="true"
      className={cn(
        "grid flex-none place-items-end justify-center rounded-[5px] border border-u-border font-mono text-[8.5px] font-bold uppercase",
        compact ? "h-[34px] w-7 pb-1" : "h-[38px] w-8 pb-[5px]",
        extension === "pdf"
          ? "bg-u-offlimits-tint text-u-offlimits"
          : WORD_FILES.has(extension)
            ? "bg-u-accent-tint text-u-accent"
            : "bg-u-direct-tint text-u-direct",
      )}
    >
      {extension || "file"}
    </span>
  );
}

export function CategoryPill({ category }: { category: PersonDocumentCategory }) {
  const { label, className } = categoryOf(category);
  return (
    <span
      className={cn(
        "inline-flex flex-none items-center rounded-[4px] px-1.5 py-px font-mono text-[9.5px] font-bold uppercase tracking-[0.05em]",
        className,
      )}
    >
      {label}
    </span>
  );
}

export function VersionPill({ versionNo }: { versionNo: number }) {
  return (
    <span className="flex-none rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[10px] font-semibold text-u-text2">
      v{versionNo}
    </span>
  );
}
