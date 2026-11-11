import { useCallback, useEffect, useRef, useState } from "react";

/**
 * What a screen pins itself to while full screen. z-96 clears the mobile nav rail, a sibling outside this stacking
 * context — below 95 a keyboard user who tabbed past the nav scrim left it floating over full screen.
 */
export const FULLSCREEN_PANEL = "fixed inset-0 z-[96] bg-u-surface";

/**
 * A screen that fills the window: the boolean pins `fixed inset-0` (works everywhere); native fullscreen is asked on top
 * and may fail (iOS Safari, no gesture, iframes). Requested on `documentElement` so body-portalled toasts and tooltips
 * still paint.
 */
export function useFullscreen() {
  const [isFullscreen, setFullscreen] = useState(false);
  // Set when the request is *made*, not when it resolves. Flipping it in the promise's `then` left a
  // window in which unmounting skipped the exit below and stranded the browser in fullscreen — a
  // mandate switch right after the click is exactly that window.
  const askedForFullscreen = useRef(false);

  useEffect(() => {
    // One-directional on purpose. Escape, F11 and the browser's own exit control announce themselves
    // only here, and without this the overlay would stay up over a window that is no longer full.
    const collapseOnNativeExit = () => {
      if (document.fullscreenElement) return;
      askedForFullscreen.current = false;
      setFullscreen(false);
    };
    document.addEventListener("fullscreenchange", collapseOnNativeExit);
    return () => document.removeEventListener("fullscreenchange", collapseOnNativeExit);
  }, []);

  // Leaving the screen has to hand the window back: the caller is keyed on the mandate, so switching
  // mandates unmounts it and would otherwise strand the browser in fullscreen.
  useEffect(
    () => () => {
      // Both halves: the ref so an F11 fullscreen this screen never asked for is not taken away from
      // the user, and the live read because the request may still have been refused.
      if (askedForFullscreen.current && document.fullscreenElement) {
        void document.exitFullscreen?.().catch(() => {});
      }
    },
    [],
  );

  const toggle = useCallback(() => {
    setFullscreen((wasFullscreen) => !wasFullscreen);
    // Outside the updater: StrictMode double-invokes one in development, and this is a request to the
    // browser rather than a state calculation.
    if (isFullscreen) {
      askedForFullscreen.current = false;
      if (document.fullscreenElement) void document.exitFullscreen?.().catch(() => {});
      return;
    }
    askedForFullscreen.current = true;
    void document.documentElement.requestFullscreen?.().catch(() => {});
  }, [isFullscreen]);

  return [isFullscreen, toggle] as const;
}
