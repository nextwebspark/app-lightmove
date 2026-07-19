const CHECK = "M20 6L9 17l-5-5";
const PLUS = "M12 5v14M5 12h14";

/**
 * A single toggleable pill — the shared chip across Sector Scope and Company Size. Selected shows a check
 * in sky; deselected a plus in muted grey at reduced opacity, staying visible and re-selectable.
 */
export function Pill({
  label,
  selected,
  onToggle,
}: {
  label: string;
  selected: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onToggle}
      className={`inline-flex items-center gap-[7px] rounded-full border bg-panel px-[13px] py-[7px] font-sans text-[13px] font-medium transition ${
        selected ? "border-sky text-text" : "border-line text-text2 opacity-[.65]"
      }`}
    >
      <svg
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.4}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`flex-none ${selected ? "text-sky" : "text-text3"}`}
        aria-hidden="true"
      >
        <path d={selected ? CHECK : PLUS} />
      </svg>
      {label}
    </button>
  );
}
