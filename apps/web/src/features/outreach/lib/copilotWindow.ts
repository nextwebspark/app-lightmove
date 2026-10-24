import { createElement } from "react";
import { createRoot } from "react-dom/client";
import { CopilotPanel } from "../components/copilot/CopilotPanel";

/** Chrome's and Edge's floating windows (Document Picture-in-Picture); no other browser has them yet. */
interface DocumentPictureInPicture {
  requestWindow(options: { width: number; height: number }): Promise<Window>;
}

const WIDTH = 380;
const HEIGHT = 620;

export interface CopilotTarget {
  projectId: string;
  candidateId: string;
  recordingId: string;
}

/** Where the copilot opened: a window that stays on top, a plain popup, or nowhere (the browser refused both). */
export type CopilotOpened = "floating" | "popup" | "blocked";

/**
 * Opens a call's copilot in a small window the consultant drags beside the call. Call it straight from the click: both
 * kinds of window need the user's gesture, and the floating one must be the first thing awaited.
 *
 * <p>The floating window shares this page — its session, its token, its module state — so it needs no sign-in of its
 * own; the popup is a page of its own that boots its session the ordinary way.
 */
export async function openCopilotWindow(target: CopilotTarget, onOpenInDrawer?: () => void): Promise<CopilotOpened> {
  const floating = (window as Window & { documentPictureInPicture?: DocumentPictureInPicture })
    .documentPictureInPicture;
  if (floating) {
    try {
      mountInto(await floating.requestWindow({ width: WIDTH, height: HEIGHT }), target, onOpenInDrawer);
      return "floating";
    } catch {
      // Refused or unavailable here: a popup still opens when the gesture allows it.
    }
  }
  const popup = window.open(copilotPagePath(target), `uncava-copilot-${target.recordingId}`, popupFeatures());
  return popup ? "popup" : "blocked";
}

export function copilotPagePath({ projectId, candidateId, recordingId }: CopilotTarget): string {
  return `/copilot/${projectId}/${candidateId}/${recordingId}`;
}

/** The floating window is a blank document: it takes this page's styles and theme, then the panel. */
function mountInto(floating: Window, target: CopilotTarget, onOpenInDrawer?: () => void): void {
  const { document: doc } = floating;
  doc.title = "Interview copilot";
  for (const node of document.head.querySelectorAll('link[rel="stylesheet"], style')) {
    doc.head.appendChild(node.cloneNode(true));
  }
  doc.documentElement.className = document.documentElement.className;
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
      onOpenInDrawer: onOpenInDrawer
        ? () => {
            floating.close();
            onOpenInDrawer();
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
