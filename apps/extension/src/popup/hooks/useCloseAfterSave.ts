import { useEffect } from "react";

/** Long enough to read the receipt, short enough not to be in the way. */
const RECEIPT_LINGER_MS = 4000;

/**
 * Closes the popup once a capture has landed, when the consultant asked for that in Settings.
 *
 * Delayed rather than immediate: the receipt names where the row went, and a popup that vanishes on
 * save leaves a consultant unsure whether it did. Cleared on unmount, so switching tabs or capturing
 * another cancels it.
 */
export function useCloseAfterSave(hasSaved: boolean, closesAfterSave: boolean) {
  useEffect(() => {
    if (!hasSaved || !closesAfterSave) {
      return;
    }
    const timer = setTimeout(() => window.close(), RECEIPT_LINGER_MS);
    return () => clearTimeout(timer);
  }, [hasSaved, closesAfterSave]);
}
