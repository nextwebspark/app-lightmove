import type { DateTimePreferences } from "../../../lib/datetime";
import { previewOf } from "../lib/dateTimeOptions";

/** The moment it is now, as the choice being made would draw it — so a format is judged by its look, not its name. */
export function DateTimePreview({ preferences, note }: { preferences: DateTimePreferences; note?: string }) {
  return (
    <div className="mt-4 rounded-u-chip border border-u-border bg-u-surface px-3 py-2.5">
      <div className="type-micro-label text-u-text3">Preview</div>
      <div className="mt-1 font-mono text-body font-medium text-u-text" aria-live="polite">
        {previewOf(preferences)}
      </div>
      {note && <div className="mt-1 text-meta text-u-text3">{note}</div>}
    </div>
  );
}
