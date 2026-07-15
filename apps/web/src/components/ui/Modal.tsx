import { useEffect, type ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * The mockups' centered dialog: dim overlay, 440px card, Escape and overlay-click to close.
 * Content is the caller's; this owns only the chrome and the dismissal contract.
 */
export function Modal({
  open,
  onClose,
  title,
  children,
  className,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  className?: string;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[100] grid place-items-center bg-[rgba(15,20,30,0.4)]"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
        className={cn(
          "w-[440px] max-w-[94vw] animate-fade-up rounded-xl border border-line bg-panel p-[22px] shadow-panel",
          className,
        )}
      >
        <div className="mb-4 text-base font-semibold">{title}</div>
        {children}
      </div>
    </div>
  );
}
