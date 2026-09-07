import { Icon, ICONS } from "../layout/Icon";

/**
 * The pagination row's full-screen switch, drawn in {@code PaginationBar}'s square-button vocabulary
 * because it stands in that row beside the pagers.
 *
 * <p>The name does not flip with the state — `aria-pressed` already announces which way the switch
 * is, and a label that flips as well makes a screen reader say it twice.
 */
export function FullscreenButton({ active, onToggle }: { active: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      aria-label="Full screen"
      aria-pressed={active}
      onClick={onToggle}
      className="grid size-10 place-items-center rounded-[6px] border border-line-soft text-text3 transition hover:border-line hover:text-text lg:size-8"
    >
      <Icon d={active ? ICONS.fullscreenExit : ICONS.fullscreen} size={14} />
    </button>
  );
}
