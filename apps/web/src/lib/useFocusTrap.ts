import { useEffect, type RefObject } from "react";

const TABBABLE = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type=hidden])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "iframe",
  "[contenteditable=true]",
  "[tabindex]",
].join(",");

const FIELD = "input:not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([disabled])";

/** Open overlays, oldest first. Ordered by when each opened, so a re-render never reorders them. */
const layers: HTMLElement[] = [];

function isShown(element: HTMLElement): boolean {
  if (element.closest("[hidden], [inert]")) return false;
  // jsdom has no layout and no checkVisibility; there everything rendered counts as shown.
  return typeof element.checkVisibility === "function" ? element.checkVisibility() : true;
}

function tabbablesIn(panel: HTMLElement): HTMLElement[] {
  return Array.from(panel.querySelectorAll<HTMLElement>(TABBABLE)).filter(
    (element) => element.tabIndex >= 0 && isShown(element),
  );
}

export interface FocusTrapOptions {
  /** Where focus lands on open, ahead of the default. */
  initialFocusRef?: RefObject<HTMLElement | null>;
  /**
   * `field` (a dialog): the first field, else the first control. `panel` (a drawer): the panel itself,
   * so opening a record never lands in one of its edit boxes and the next Tab starts at its top.
   */
  startAt?: "field" | "panel";
}

function initialTarget(panel: HTMLElement, { initialFocusRef, startAt = "field" }: FocusTrapOptions): HTMLElement {
  if (initialFocusRef?.current) return initialFocusRef.current;
  if (startAt === "panel") return panel;
  const field = Array.from(panel.querySelectorAll<HTMLElement>(FIELD)).find(
    (element) => element.tabIndex >= 0 && isShown(element),
  );
  return field ?? tabbablesIn(panel)[0] ?? panel;
}

function keepTabInTopLayer(event: KeyboardEvent) {
  if (event.key !== "Tab" || event.defaultPrevented) return;
  const panel = layers.at(-1);
  if (!panel) return;
  const tabbables = tabbablesIn(panel);
  const active = document.activeElement;
  if (tabbables.length === 0) {
    event.preventDefault();
    panel.focus();
    return;
  }
  const first = tabbables[0];
  const last = tabbables[tabbables.length - 1];
  // Focus parked on the body (a click on the panel's padding) re-enters the panel at its edge. Focus in
  // some other element outside it is a portalled popover the panel opened, and is left to that popover.
  if (!active || active === document.body) {
    event.preventDefault();
    (event.shiftKey ? last : first).focus();
  } else if (!panel.contains(active)) {
    return;
  } else if (event.shiftKey && (active === first || active === panel)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}

/**
 * Keeps Tab and Shift-Tab inside the topmost open overlay, focuses it on open and hands focus back to
 * whatever opened it on close. The panel needs `tabIndex={-1}` so it can hold focus when it has nothing
 * else to offer.
 */
export function useFocusTrap(
  panelRef: RefObject<HTMLElement | null>,
  active: boolean,
  options: FocusTrapOptions = {},
) {
  useEffect(() => {
    const panel = panelRef.current;
    if (!active || !panel) return;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    layers.push(panel);
    if (layers.length === 1) document.addEventListener("keydown", keepTabInTopLayer);
    // A field the dialog focused itself (autoFocus) already says where the user starts.
    if (!panel.contains(document.activeElement)) initialTarget(panel, options).focus({ preventScroll: true });

    return () => {
      const index = layers.lastIndexOf(panel);
      if (index >= 0) layers.splice(index, 1);
      if (layers.length === 0) document.removeEventListener("keydown", keepTabInTopLayer);
      if (opener?.isConnected) opener.focus({ preventScroll: true });
    };
    // The options are read once, when the layer opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, panelRef]);
}
