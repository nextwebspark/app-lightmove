import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button, Modal } from "../ui";

/**
 * Guards unsaved edits: a tab close asks the browser's question, a link saves first and asks only if refused. Links are
 * caught in the capture phase (`useBlocker` needs a data router), so Back is not; same-page links are left alone.
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
