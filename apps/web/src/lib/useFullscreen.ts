import { useCallback, useEffect, useRef, useState } from "react";

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
  const enteredNatively = useRef(false);

  useEffect(() => {
    // One-directional on purpose. Escape, F11 and the browser's own exit control announce themselves
    // only here, and without this the overlay would stay up over a window that is no longer full.
    const collapseOnNativeExit = () => {
      if (document.fullscreenElement) return;
      enteredNatively.current = false;
      setFullscreen(false);
    };
    document.addEventListener("fullscreenchange", collapseOnNativeExit);
    return () => document.removeEventListener("fullscreenchange", collapseOnNativeExit);
  }, []);

  // Leaving the screen has to hand the window back: the caller is keyed on the mandate, so switching
  // mandates unmounts it and would otherwise strand the browser in fullscreen.
  useEffect(
    () => () => {
      if (enteredNatively.current) void document.exitFullscreen?.().catch(() => {});
    },
    [],
  );

  const toggle = useCallback(() => {
    setFullscreen((wasFullscreen) => !wasFullscreen);
    // Outside the updater: StrictMode double-invokes one in development, and this is a request to the
    // browser rather than a state calculation.
    if (isFullscreen) {
      if (document.fullscreenElement) void document.exitFullscreen?.().catch(() => {});
      return;
    }
    void document.documentElement
      .requestFullscreen?.()
      .then(() => {
        enteredNatively.current = true;
      })
      .catch(() => {});
  }, [isFullscreen]);

  return [isFullscreen, toggle] as const;
}
