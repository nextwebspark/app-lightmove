import type { ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useEscapeKey } from "../../lib/useEscapeKey";

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

  if (!open) return null;

  return (
    <>
      <div className="fixed inset-0 z-[90] bg-u-scrim" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={label}
        className={cn(
          "fixed inset-x-2.5 bottom-2.5 top-14 z-[95] flex animate-fade-up flex-col rounded-[10px]",
          "border border-u-border-strong bg-u-surface shadow-u-e3 sm:inset-x-auto sm:right-2.5 sm:top-2.5 sm:max-w-[92vw]",
          wide ? "sm:w-[560px]" : "sm:w-[420px]",
        )}
      >
        {children}
      </aside>
    </>
  );
}
