import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button, Modal } from "../ui";

/**
 * Keeps a screen with unsaved edits from being left by accident: closing or reloading the tab asks
 * the browser's own question, and following a link to another page first saves, then goes — asking
 * only when that save is refused.
 *
 * <p>Links are caught on the document in the capture phase, before the router's own handler, because
 * the app runs on `<BrowserRouter>` and react-router's `useBlocker` needs a data router. The browser's
 * Back button is therefore not caught; the autosave's flush on unmount is the last attempt there.
 * A link within the same page (the brief's steps, Strategy's mode) is left alone: nothing unmounts.
 */
export function LeaveGuard({
  hasUnsavedChanges,
  flush,
}: {
  hasUnsavedChanges: boolean;
  /** Saves what is held; rejects when the save is refused. */
  flush: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [refusedTarget, setRefusedTarget] = useState<string | null>(null);
  const leaving = useRef(false);
  const flushRef = useRef(flush);
  flushRef.current = flush;

  useEffect(() => {
    if (!hasUnsavedChanges) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      // Chrome before 119 shows the prompt only when returnValue is set.
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [hasUnsavedChanges]);

  useEffect(() => {
    if (!hasUnsavedChanges) return;
    const handleClick = (event: MouseEvent) => {
      const target = leavingLinkTarget(event, pathname);
      if (target === null) return;
      event.preventDefault();
      event.stopPropagation();
      if (leaving.current) return;
      leaving.current = true;
      flushRef
        .current()
        .then(() => navigate(target))
        .catch(() => setRefusedTarget(target))
        .finally(() => {
          leaving.current = false;
        });
    };
    document.addEventListener("click", handleClick, true);
    return () => document.removeEventListener("click", handleClick, true);
  }, [hasUnsavedChanges, navigate, pathname]);

  const handleStay = () => setRefusedTarget(null);
  const handleLeave = () => {
    const target = refusedTarget;
    setRefusedTarget(null);
    if (target !== null) navigate(target);
  };

  return (
    <Modal
      open={refusedTarget !== null}
      onClose={handleStay}
      title="Leave without saving?"
      footer={
        <>
          <Button variant="secondary" onClick={handleLeave}>
            Leave anyway
          </Button>
          <Button onClick={handleStay}>Stay on this page</Button>
        </>
      }
    >
      <p className="text-body text-u-text2">
        Your last change hasn't been saved. If you leave now, it will be lost.
      </p>
    </Modal>
  );
}

/** The in-app path a plain left click on a link would take the user to, or null when it stays put. */
function leavingLinkTarget(event: MouseEvent, currentPathname: string): string | null {
  if (event.defaultPrevented || event.button !== 0) return null;
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return null;
  if (!(event.target instanceof Element)) return null;
  const anchor = event.target.closest("a[href]");
  if (!(anchor instanceof HTMLAnchorElement)) return null;
  if (anchor.hasAttribute("download")) return null;
  if (anchor.target && anchor.target !== "_self") return null;
  const url = new URL(anchor.href, window.location.href);
  if (url.origin !== window.location.origin) return null;
  if (url.pathname === currentPathname) return null;
  return `${url.pathname}${url.search}${url.hash}`;
}
