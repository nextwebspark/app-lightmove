import { Icon, ICONS } from "../../../components/layout/Icon";
import { BriefButton, BriefPanel } from "./BriefFields";

/**
 * One strip per screen a "Read from document" reading touched — never a panel per field. Reports what
 * the last reading did to this screen and offers to undo all of it in one press; a section that failed
 * to read shows its own message and Read again as the retry, in place of the count.
 */
export function DocumentFillStrip({
  fileName,
  count,
  onUndoAll,
  onDismiss,
  error,
  onRetry,
  retrying,
}: {
  fileName: string;
  count: number;
  onUndoAll: () => void;
  onDismiss: () => void;
  /** Set when this screen's own section failed to read — shown instead of the count. */
  error?: string;
  onRetry?: () => void;
  retrying?: boolean;
}) {
  if (count === 0 && !error) return null;

  return (
    <BriefPanel className="mb-6 flex flex-wrap items-center gap-3 border border-u-inferred/25 bg-u-inferred-tint px-4 py-3 text-[13px] text-u-text">
      <Icon d={ICONS.sparkle} size={14} className="flex-none text-u-inferred" />
      <span className="min-w-0 flex-1">
        {error ? (
          <span className="text-u-text2">{error}</span>
        ) : (
          <>
            <b className="font-semibold">
              {count} field{count === 1 ? "" : "s"}
            </b>{" "}
            read from <span className="font-medium">{fileName}</span>
          </>
        )}
      </span>
      {error ? (
        <BriefButton variant="link" onClick={onRetry} loading={retrying} className="px-0 text-u-inferred">
          Read again
        </BriefButton>
      ) : (
        <button type="button" onClick={onUndoAll} className="text-[12.5px] font-semibold text-u-inferred hover:underline">
          Undo all
        </button>
      )}
      <button
        type="button"
        aria-label="Dismiss"
        onClick={onDismiss}
        className="flex-none text-u-text3 transition hover:text-u-text"
      >
        <Icon d={ICONS.close} size={13} />
      </button>
    </BriefPanel>
  );
}
