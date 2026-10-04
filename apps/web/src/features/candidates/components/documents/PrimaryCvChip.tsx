import { Icon, ICONS } from "../../../../components/layout/Icon";
import type { PersonDocument, PersonDocumentVersion } from "../../api/types";
import { primaryCvOf } from "../../lib/documents";
import type { PersonDocuments } from "../../lib/usePersonDocuments";

const PART = "flex items-center gap-1.5 text-u-accent hover:bg-u-accent/15";

/** The primary CV in a drawer's header, one click from Preview or Download: opening it is the commonest act. */
export function PrimaryCvChip({
  documents,
  onPreview,
}: {
  documents: PersonDocuments;
  onPreview: (document: PersonDocument, version: PersonDocumentVersion) => void;
}) {
  const cv = primaryCvOf(documents.documents.data ?? []);
  if (!cv) return null;
  const latest = cv.versions[0];
  const label = `CV · v${latest.versionNo} · ${new Date(latest.uploadedAt).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
  })}`;

  return (
    <span className="inline-flex items-stretch overflow-hidden rounded-[6px] border border-u-accent/45 bg-u-accent-tint">
      {latest.previewable ? (
        <button
          type="button"
          title={`Preview ${cv.title} — ${latest.fileName}`}
          onClick={() => onPreview(cv, latest)}
          className={`${PART} px-2 py-[3px] font-mono text-[11.5px] font-semibold`}
        >
          <Icon d={ICONS.fileText} size={12} />
          {label}
        </button>
      ) : (
        <span className="flex items-center gap-1.5 px-2 py-[3px] font-mono text-[11.5px] font-semibold text-u-accent">
          <Icon d={ICONS.fileText} size={12} />
          {label}
        </span>
      )}
      <button
        type="button"
        aria-label="Download the CV"
        title="Download"
        onClick={() => void documents.download(cv, latest)}
        className={`${PART} border-s border-u-accent/35 px-1.5`}
      >
        <Icon d={ICONS.importInto} size={12} />
      </button>
    </span>
  );
}
