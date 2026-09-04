/**
 * What the panel shows on the very first read of its life, and only then.
 *
 * Every later read shimmers the fields in place instead: swapping the form out on each navigation is
 * the layout jump the consultant reads as flicker, and it destroys a note being typed with the
 * subtree that held it.
 */
export function PanelLoading({ label }: { label: string }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 py-12" role="status" aria-live="polite">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-line border-t-amber-btn" />
      <p className="text-[11.5px] text-text3">{label}</p>
    </div>
  );
}
