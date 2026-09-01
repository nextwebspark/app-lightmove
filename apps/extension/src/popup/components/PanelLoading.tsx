/**
 * What the panel shows while it is reading a page it has just arrived on.
 *
 * It stands in for the fields rather than sitting above them: a form still holding the previous
 * profile's name is the flicker this exists to end, and an empty form reads as "nothing found"
 * rather than "not read yet".
 */
export function PanelLoading({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12" role="status" aria-live="polite">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-line border-t-amber-btn" />
      <p className="text-[11.5px] text-text3">{label}</p>
    </div>
  );
}
