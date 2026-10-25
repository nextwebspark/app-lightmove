import { createElement, useSyncExternalStore } from "react";
import { createRoot } from "react-dom/client";
import { CopilotPanel } from "../components/copilot/CopilotPanel";

/** Chrome's and Edge's floating windows (Document Picture-in-Picture); no other browser has them yet. */
interface DocumentPictureInPicture {
  requestWindow(options: { width: number; height: number }): Promise<Window>;
}

const WIDTH = 380;
const HEIGHT = 620;
/** A popup says nothing when it closes; it is asked this often. */
const POPUP_CLOSED_CHECK_MS = 1_000;

export interface CopilotTarget {
  projectId: string;
  candidateId: string;
  recordingId: string;
}

/** Where the copilot opened: a window that stays on top, a plain popup, or nowhere (the browser refused both). */
export type CopilotOpened = "floating" | "popup" | "blocked";

/** The copilot windows this tab opened, by recording: the drawer draws the copilot only where none is open. */
const openWindows = new Map<string, Window>();
const listeners = new Set<() => void>();

function track(recordingId: string, opened: Window, onClosed: (notify: () => void) => void): void {
  openWindows.set(recordingId, opened);
  listeners.forEach((listener) => listener());
  onClosed(() => {
    if (openWindows.get(recordingId) !== opened) return;
    openWindows.delete(recordingId);
    listeners.forEach((listener) => listener());
  });
}

/** Whether this call's copilot is in a window of its own right now. */
export function useCopilotWindowOpen(recordingId: string): boolean {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => openWindows.has(recordingId),
  );
}

export function closeCopilotWindow(recordingId: string): void {
  openWindows.get(recordingId)?.close();
}

/**
 * Opens a call's copilot in a small window the consultant drags beside the call. Call it straight from the click: both
 * kinds of window need the user's gesture, and the floating one must be the first thing awaited.
 *
 * <p>The floating window shares this page — its session, its token, its module state — so it needs no sign-in of its
 * own; the popup is a page of its own that boots its session the ordinary way. `onShowInUncava` is the floating
 * window's way back into the drawer.
 */
export async function openCopilotWindow(target: CopilotTarget, onShowInUncava?: () => void): Promise<CopilotOpened> {
  const floating = (window as Window & { documentPictureInPicture?: DocumentPictureInPicture })
    .documentPictureInPicture;
  if (floating) {
    try {
      const opened = await floating.requestWindow({ width: WIDTH, height: HEIGHT });
      mountInto(opened, target, onShowInUncava);
      track(target.recordingId, opened, (notify) => opened.addEventListener("pagehide", notify, { once: true }));
      return "floating";
    } catch {
      // Refused or unavailable here: a popup still opens when the gesture allows it.
    }
  }
  const popup = window.open(copilotPagePath(target), `uncava-copilot-${target.recordingId}`, popupFeatures());
  if (!popup) return "blocked";
  track(target.recordingId, popup, (notify) => {
    const check = window.setInterval(() => {
      if (!popup.closed) return;
      window.clearInterval(check);
      notify();
    }, POPUP_CLOSED_CHECK_MS);
  });
  return "popup";
}

export function copilotPagePath({ projectId, candidateId, recordingId }: CopilotTarget): string {
  return `/copilot/${projectId}/${candidateId}/${recordingId}`;
}

/** The floating window is a blank document: it takes this page's styles and theme, then the panel. */
function mountInto(floating: Window, target: CopilotTarget, onShowInUncava?: () => void): void {
  const { document: doc } = floating;
  doc.title = "Interview copilot";
  for (const node of document.head.querySelectorAll('link[rel="stylesheet"], style')) {
    doc.head.appendChild(node.cloneNode(true));
  }
  doc.documentElement.className = document.documentElement.className;
  doc.body.className = document.body.className;
  doc.body.style.margin = "0";
  const container = doc.createElement("div");
  container.style.height = "100vh";
  doc.body.appendChild(container);
  const root = createRoot(container);
  root.render(
    createElement(CopilotPanel, {
      ...target,
      variant: "window",
      onClose: () => floating.close(),
      onShowInUncava: onShowInUncava
        ? () => {
            floating.close();
            onShowInUncava();
          }
        : undefined,
    }),
  );
  floating.addEventListener("pagehide", () => root.unmount(), { once: true });
}

function popupFeatures(): string {
  const left = Math.round(window.screenX + Math.max(0, window.outerWidth - WIDTH - 40));
  const top = Math.round(window.screenY + 80);
  return `popup=yes,width=${WIDTH},height=${HEIGHT},left=${left},top=${top}`;
}
