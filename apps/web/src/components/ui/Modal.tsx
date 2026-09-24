import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "../../lib/cn";
import { useEscapeKey } from "../../lib/useEscapeKey";

/**
 * The mockups' centered dialog: dim overlay, 440px card, Escape and overlay-click to close.
 *
 * The title and the `footer` stay put and only the body scrolls, so on a short screen the actions
 * are never scrolled out of reach. A hairline appears under the title or above the footer only while
 * content is hidden past it — the cue that there is more, and no line at all when everything fits.
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
  /** The dialog's actions, pinned beneath the scrolling body. */
  footer?: ReactNode;
  className?: string;
}) {
  // Through the shared stack rather than its own listener: a modal opened over a drawer or the
  // assistant panel must take Escape from it, not fire alongside it.
  useEscapeKey(open, onClose);
  const { bodyRef, hiddenAbove, hiddenBelow, measure } = useScrollEdges(open);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[100] grid place-items-center bg-u-scrim"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
        className={cn(
          "flex max-h-[90dvh] w-full max-w-[94vw] flex-col rounded-xl border border-u-border-strong bg-u-surface text-u-text shadow-u-e3",
          "animate-fade-up md:w-[440px]",
          className,
        )}
      >
        <div
          className={cn(
            "relative z-10 flex-none border-b px-5 pb-1 pt-5 text-base font-semibold transition-[border-color,box-shadow] md:px-[22px] md:pt-[22px]",
            hiddenAbove ? "border-u-border shadow-[0_12px_16px_-12px_var(--color-u-scrim)]" : "border-transparent",
          )}
        >
          {title}
        </div>
        <div
          ref={bodyRef}
          onScroll={measure}
          // pt-3 rather than the header's margin: callers pull a subtitle up under the title with -mt-2
          // or -mt-3, and a negative margin past a scroll box's padding is clipped.
          // The thumb is drawn rather than overlaid: an overlay scrollbar (macOS, headless Chromium) stays
          // hidden until the user scrolls, which is exactly when they needed telling there was more.
          className={cn(
            "min-h-0 flex-1 overflow-y-auto px-5 pt-3 [scrollbar-color:var(--color-u-border-strong)_transparent] [scrollbar-gutter:stable] [scrollbar-width:thin] md:px-[22px]",
            !footer && "pb-5 md:pb-[22px]",
          )}
        >
          {children}
        </div>
        {footer && (
          <div
            className={cn(
              "relative z-10 flex flex-none justify-end gap-2 border-t px-5 py-4 transition-[border-color,box-shadow] md:px-[22px]",
              hiddenBelow ? "border-u-border shadow-[0_-12px_16px_-12px_var(--color-u-scrim)]" : "border-transparent",
            )}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

/** Whether the body has content scrolled out of view above and below, kept current as it grows. */
function useScrollEdges(open: boolean) {
  const bodyRef = useRef<HTMLDivElement>(null);
  const [hiddenAbove, setHiddenAbove] = useState(false);
  const [hiddenBelow, setHiddenBelow] = useState(false);

  const measure = useCallback(() => {
    const body = bodyRef.current;
    if (!body) return;
    setHiddenAbove(body.scrollTop > 0);
    // A pixel of slack: fractional layout sizes leave a sub-pixel gap at the true bottom.
    setHiddenBelow(body.scrollTop + body.clientHeight < body.scrollHeight - 1);
  }, []);

  // The body changes height without scrolling — a field appears, the window is resized — so its size
  // and its content's size are watched too. Absent in jsdom, where there is no layout to watch.
  useEffect(() => {
    const body = bodyRef.current;
    if (!open || !body) return;
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(body);
    for (const child of Array.from(body.children)) observer.observe(child);
    const mutations = new MutationObserver(() => {
      for (const child of Array.from(body.children)) observer.observe(child);
      measure();
    });
    mutations.observe(body, { childList: true, subtree: true });
    return () => {
      observer.disconnect();
      mutations.disconnect();
    };
  }, [open, measure]);

  return { bodyRef, hiddenAbove, hiddenBelow, measure };
}
