import type { ActivePage } from "../hooks/useActivePage";
import { Icon, ICONS } from "./Icon";

/** What was read, and the way back to re-read it — shared by both capture tabs. */
export function SourceStrip({ page }: { page: ActivePage }) {
  return (
    <div className="flex items-center gap-2 border-b border-line-soft bg-sky-dim px-3.5 py-[9px]">
      <Icon d={ICONS.check} className="shrink-0 text-sky" />
      <span className="flex-1 truncate font-mono text-[11px] text-text2">
        {page.isReading ? "Reading this page…" : `Read from ${page.sourceUrl ?? "this page"}`}
      </span>
      <button
        type="button"
        onClick={() => void page.rescan()}
        className="text-[11px] font-medium text-sky hover:underline"
      >
        Re-scan
      </button>
    </div>
  );
}
