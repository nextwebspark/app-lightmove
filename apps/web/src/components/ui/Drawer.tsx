import { useRef, type ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useEscapeKey } from "../../lib/useEscapeKey";
import { useFocusTrap } from "../../lib/useFocusTrap";

/** The mockups' right slide-over: floating rounded panel, overlay and Escape to dismiss. */
export function Drawer({
  open,
  onClose,
  label,
  wide,
  children,
}: {
  open: boolean;
  onClose: () => void;
  /** Names the panel for screen readers — it is a modal, and a modal without a name is "dialog". */
  label: string;
  /**
   * A wider panel, for a form that genuinely holds two columns. A boolean rather than a `className`
   * override because a caller's unprefixed width would beat the unprefixed default and lose to the
   * `sm:` one — 500px on a phone and 420px on a desktop, the exact inverse of any caller's intent.
   */
  wide?: boolean;
  children: ReactNode;
}) {
  useEscapeKey(open, onClose);
  const panelRef = useRef<HTMLElement>(null);
  useFocusTrap(panelRef, open, { startAt: "panel" });

  if (!open) return null;

  return (
    <>
      <div className="fixed inset-0 z-[90] bg-u-scrim" onClick={onClose} />
      <aside
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        data-edge-panel={wide ? "drawer-wide" : "drawer"}
        className={cn(
          // A phone gets the whole screen, clear of the notch and the home bar; from `sm` it floats at the right.
          "fixed inset-0 z-[95] flex animate-fade-up flex-col pt-[env(safe-area-inset-top)] outline-none",
          "bg-u-surface shadow-u-e3 sm:inset-x-auto sm:bottom-2.5 sm:right-2.5 sm:top-2.5 sm:max-w-[92vw] sm:pt-0",
          "sm:rounded-[10px] sm:border sm:border-u-border-strong",
          wide ? "sm:w-[560px]" : "sm:w-[420px]",
        )}
      >
        {children}
      </aside>
    </>
  );
}
