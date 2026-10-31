import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon } from "./Icon";

const COLLAPSE_GLYPH = "M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7";

/**
 * A view that fills the whole panel over what it covers, with a title, Collapse where Expand was, and a footer. The
 * panel is too narrow for a dialog, so the web app's larger views open as this instead. Escape closes it.
 */
export function PanelSheet({
  title,
  onClose,
  footer,
  children,
}: {
  title: string;
  onClose: () => void;
  footer?: ReactNode;
  children: ReactNode;
}) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  return createPortal(
    <div role="dialog" aria-modal="true" aria-label={title} className="fixed inset-0 z-50 flex flex-col bg-u-surface">
      <header className="flex flex-none items-center gap-2 border-b border-u-border px-3.5 py-2.5">
        <h2 className="min-w-0 flex-1 truncate text-body font-semibold text-u-text">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Collapse"
          title="Collapse"
          className="grid size-7 place-items-center rounded-u-control border border-u-border-strong text-u-text2 transition hover:text-u-text"
        >
          <Icon d={COLLAPSE_GLYPH} size={14} />
        </button>
      </header>
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto px-3.5 py-3">{children}</div>
      {footer && <footer className="flex flex-none justify-end border-t border-u-border px-3.5 py-3">{footer}</footer>}
    </div>,
    document.body,
  );
}
