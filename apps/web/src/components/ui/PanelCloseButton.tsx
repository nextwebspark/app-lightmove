import { Icon, ICONS } from "../layout/Icon";

/**
 * The dismiss control every panel puts in the same corner. Positioned absolutely, so the header it
 * sits in is the `relative` one — written once because every drawer and some modals draw it, and a
 * copy would drift.
 */
export function PanelCloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button
      type="button"
      onClick={onClose}
      aria-label="Close"
      className="absolute end-3.5 top-3.5 rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-text"
    >
      <Icon d={ICONS.close} size={16} />
    </button>
  );
}
