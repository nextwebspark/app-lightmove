import { useEffect, useState } from "react";
import { cn } from "../../../lib/cn";
import { useAssistant } from "../AssistantProvider";
import { AssistantPanel } from "./AssistantPanel";

/**
 * The slot the assistant occupies beside the page, and the thing that actually animates.
 *
 * <p>The panel used to mount straight into {@link AppShell}'s row and slide itself in on a
 * `transform`. That meant `<main>` lost its 410px in a single unanimated frame while the panel was
 * still arriving over the next 240ms — the layout finished before the panel did, and the centred
 * page inside `<main>` snapped sideways as it re-centred. Animating the *slot* instead keeps the two
 * on one curve: `<main>` narrows exactly as fast as the panel appears.
 *
 * <p>The slide comes for free and needs no transform. This slot's end edge is pinned to the row's, so
 * a widening slot grows leftward, and a fixed-width child anchored to its start edge travels with it
 * and is clipped by `overflow-hidden` — which reads as a panel arriving from off-screen. Fixed-width
 * is also why the panel's own contents never reflow while it moves.
 */
export function AssistantDock({
  contextLabel,
  projectId,
}: {
  contextLabel: string;
  projectId: string;
}) {
  const { isOpenFor } = useAssistant();
  const open = isOpenFor(projectId);
  const [mounted, setMounted] = useState(open);

  // Kept mounted through the collapse, or closing would blank the panel and then shut an empty gap.
  // A timer rather than `transitionend`: reduced motion kills transitions with `!important`, so the
  // event would never fire and the panel would stay mounted for the rest of the session.
  useEffect(() => {
    if (open) {
      setMounted(true);
      return;
    }
    const collapsed = window.setTimeout(() => setMounted(false), COLLAPSE_MS);
    return () => window.clearTimeout(collapsed);
  }, [open]);

  return (
    <div
      aria-hidden={!open}
      inert={!open}
      className={cn(
        "hidden flex-none overflow-hidden lg:block",
        // `ease-in-out`, not the `slide-in-end` curve the panel used to slide on. That one is heavily
        // front-loaded, which reads as energy on a transform over the top of a page and as a lurch
        // when the page itself is what moves: measured, it put 114px of the 410 into the first frame.
        // This one leaves rest at 4px and peaks at 49, so the eye follows it instead of catching it.
        "transition-[width] duration-[240ms] ease-in-out motion-reduce:transition-none",
        // 410 is the panel's own 400 plus the 10px gutter its `ms-2.5` puts between it and `<main>`.
        open ? "w-[410px]" : "w-0",
      )}
    >
      {mounted && <AssistantPanel contextLabel={contextLabel} projectId={projectId} />}
    </div>
  );
}

/** Must not be shorter than the `duration-[240ms]` above, or the panel vanishes mid-collapse. */
const COLLAPSE_MS = 240;
