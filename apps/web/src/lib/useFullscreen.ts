import { useCallback, useEffect, useRef, useState } from "react";

/**
 * What a screen pins itself to while full screen. Its own background and no corners, because full
 * screen has no edges and `main`'s rounded panel is no longer behind it.
 *
 * <p>96 clears the mobile nav rail. That rail is a sibling rendered by AppShell rather than a
 * descendant, so it does not order inside this stacking context — at anything below 95 a keyboard
 * user who tabbed past the nav scrim left it floating over "full screen".
 */
export const FULLSCREEN_PANEL = "fixed inset-0 z-[96] bg-panel";

/**
 * A screen that fills the window, and asks the browser to drop its own chrome as well.
 *
 * <p>Two mechanisms behind one switch. The boolean is what a caller pins to `fixed inset-0` — that is
 * the half that hides the app shell, and it works everywhere. Native fullscreen is asked for on top
 * and is allowed to fail: iOS Safari has no element fullscreen at all, and a request from outside a
 * user gesture or inside an unpermitted iframe is refused. A refusal costs the browser's toolbar,
 * not the feature.
 *
 * <p>The request goes to `documentElement` rather than to a panel, so everything portalled to
 * `document.body` — the toast, the grid's truncation tooltips — is inside the fullscreen element and
 * still paints.
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
