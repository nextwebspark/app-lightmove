import type { ActivePage } from "../hooks/useActivePage";
import { ICONS } from "../lib/icons";
import { Icon } from "./Icon";

/** What was read, and the way back to re-read it — shared by both capture tabs. */
export function SourceStrip({ page }: { page: ActivePage }) {
  return (
    <div className="flex items-center gap-2 border-b border-u-border bg-u-accent-tint px-3.5 py-[9px]">
      <Icon d={ICONS.check} className="shrink-0 text-u-accent" />
      <span className="flex-1 truncate font-mono text-[11px] text-u-text2">
        {page.isReading ? "Reading this page…" : `Read from ${page.sourceUrl ?? "this page"}`}
      </span>
      <button
        type="button"
        onClick={() => void page.rescan()}
        className="text-[11px] font-medium text-u-accent hover:underline"
      >
        Re-scan
      </button>
    </div>
  );
}
