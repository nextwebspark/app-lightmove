import { useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { cn } from "../../lib/cn";
import { useEscapeKey } from "../../lib/useEscapeKey";
import { useFocusTrap } from "../../lib/useFocusTrap";

/** A sheet rising from the bottom edge, in reach of the thumb: the phone's menu of actions. */
export function BottomSheet({
  open,
  onClose,
  label,
  title,
  children,
  className,
}: {
  open: boolean;
  onClose: () => void;
  label: string;
  title?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  useEscapeKey(open, onClose);
  const panelRef = useRef<HTMLDivElement>(null);
  useFocusTrap(panelRef, open, { startAt: "panel" });

  if (!open) return null;

  return createPortal(
    <>
      <div className="fixed inset-0 z-[96] bg-u-scrim" onClick={onClose} />
      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        className={cn(
          "fixed inset-x-0 bottom-0 z-[97] mx-auto flex max-h-[85dvh] w-full max-w-md animate-fade-up flex-col outline-none",
          "rounded-t-[16px] border border-b-0 border-u-border-strong bg-u-surface shadow-u-e3",
          "pb-[max(0.75rem,env(safe-area-inset-bottom))]",
          className,
        )}
      >
        <span aria-hidden="true" className="mx-auto mt-2 h-1 w-10 flex-none rounded-full bg-u-border-strong" />
        {title && <div className="flex-none px-4 pb-1 pt-3 text-lead font-semibold text-u-text">{title}</div>}
        <div className="min-h-0 flex-1 overflow-y-auto px-2 pt-1">{children}</div>
      </div>
    </>,
    document.body,
  );
}

/** One row of a {@link BottomSheet}: a 48px target with a glyph, a label and an optional line under it. */
export function BottomSheetAction({
  icon,
  label,
  hint,
  onClick,
}: {
  icon: ReactNode;
  label: string;
  hint?: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-12 w-full items-center gap-3 rounded-[10px] px-3 py-2.5 text-left transition hover:bg-u-raised focus-visible:bg-u-raised focus-visible:outline-none"
    >
      <span className="grid size-9 flex-none place-items-center rounded-[9px] bg-u-raised text-u-accent">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-body font-medium text-u-text">{label}</span>
        {hint && <span className="block truncate text-meta text-u-text3">{hint}</span>}
      </span>
    </button>
  );
}
