import { useEffect, type RefObject } from "react";

/** Closes a popover on a press outside it or on Escape, as every popover in the panel does. */
export function useDismiss(ref: RefObject<HTMLElement | null>, isOpen: boolean, onClose: () => void) {
  useEffect(() => {
    if (!isOpen) return;
    const pressOutside = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) onClose();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("mousedown", pressOutside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", pressOutside);
      document.removeEventListener("keydown", escape);
    };
  }, [ref, isOpen, onClose]);
}
