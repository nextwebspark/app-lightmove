import { type ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useEscapeKey } from "../../lib/useEscapeKey";

/**
 * The mockups' centered dialog: dim overlay, 440px card, Escape and overlay-click to close.
 * Content is the caller's; this owns only the chrome and the dismissal contract.
 */
export function Modal({
  open,
  onClose,
  title,
  children,
  footer,
  className,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  /**
   * The dialog's actions. Passed here rather than rendered at the end of {@code children} so they are
   * pinned: a form that outgrows the viewport scrolls its own body, and the buttons that finish it
   * stay where they were. A dialog whose Create button scrolls out of reach reads as broken.
   */
  footer?: ReactNode;
  className?: string;
}) {
  // Through the shared stack rather than its own listener: a modal opened over a drawer or the
  // assistant panel must take Escape from it, not fire alongside it.
  useEscapeKey(open, onClose);

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
          "flex max-h-[90dvh] w-full max-w-[94vw] flex-col rounded-xl border border-line bg-panel p-5 shadow-panel",
          "animate-fade-up md:w-[440px] md:p-[22px]",
          className,
        )}
      >
        <div className="mb-4 text-base font-semibold">{title}</div>

        {/* min-h-0 is what lets a flex child shrink below its content and actually scroll. */}
        <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>

        {footer && (
          <div className="mt-4 flex justify-end gap-2 border-t border-line-soft pt-4">{footer}</div>
        )}
      </div>
    </div>
  );
}
