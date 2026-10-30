import { workspaceOrigin } from "../../workspaceOrigin";
import { cn } from "../lib/cn";
import { ICONS } from "../lib/icons";
import { Icon } from "./Icon";

/**
 * What the record on screen is: just filed here (green, with Undo) or already on the position (accent: who filed it
 * and when, nothing to undo, and a way to open it in Uncava).
 */
export function RecordBanner({
  variant,
  title,
  detail,
  projectId,
  onUndo,
  isUndoing = false,
}: {
  variant: "added" | "held";
  title: string;
  detail: string;
  projectId: string;
  onUndo?: () => void;
  isUndoing?: boolean;
}) {
  const isAdded = variant === "added";
  return (
    <div
      role="status"
      className={cn(
        "flex flex-none items-center gap-2.5 border-b border-u-border px-3.5 py-[9px]",
        isAdded ? "bg-u-direct-tint" : "bg-u-accent-tint",
      )}
    >
      <span
        aria-hidden
        className={cn(
          "grid size-[22px] flex-none place-items-center rounded-full",
          isAdded ? "bg-u-direct text-[#06150e]" : "bg-u-accent-solid text-white",
        )}
      >
        <Icon d={isAdded ? ICONS.check : ICONS.userCheck} size={12} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[12.5px] font-semibold text-u-text">{title}</span>
        <span className="block truncate text-[11px] text-u-text2">{detail}</span>
      </span>
      {isAdded && onUndo ? (
        <button
          type="button"
          onClick={onUndo}
          disabled={isUndoing}
          className="flex-none rounded-u-control border border-u-direct/40 px-2.5 py-1 text-[11.5px] font-semibold text-u-direct transition hover:bg-u-direct/10 disabled:opacity-60"
        >
          {isUndoing ? "Undoing…" : "Undo"}
        </button>
      ) : (
        <button
          type="button"
          onClick={() => void chrome.tabs.create({ url: `${workspaceOrigin}/projects/${projectId}/companies/universe` })}
          className="inline-flex flex-none items-center gap-1 text-[11.5px] font-medium text-u-accent hover:underline"
        >
          Open
          <Icon d={ICONS.externalLink} size={11} />
        </button>
      )}
    </div>
  );
}
