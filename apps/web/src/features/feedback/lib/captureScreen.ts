/**
 * Renders the page the tester is looking at into a PNG.
 *
 * This is a *likeness*, not a screenshot: html2canvas-pro re-draws the DOM onto a canvas rather than
 * asking the operating system for pixels. That is the deliberate trade — the browser's own capture
 * API would be exact but pops a "choose what to share" picker on every single report, and friction
 * repeated forty times during UAT is how bug reports stop being filed.
 *
 * The library is ~200KB and is loaded on first use only, so it never touches the initial bundle.
 */

/** Beyond this the file grows faster than the detail does. A 4K monitor lands well over it. */
const MAX_EDGE = 1600;

/** Above this a PNG is re-encoded as JPEG; a large screenshot is not worth a slow upload. */
const MAX_PNG_BYTES = 1_500_000;

/** Carries an element's scroll offsets across the clone, which `cloneNode` does not copy. */
const SCROLL_MARK = "data-lm-capture-scroll";

/**
 * Put this on anything that must never reach a bug report — it is blurred in the capture.
 *
 * Nothing carries it today. It exists because the alternative, remembering at each new screen, is the
 * kind of rule that is followed until the one screen where it matters.
 */
export const REDACT_ATTRIBUTE = "data-feedback-redact";

export async function captureScreen(): Promise<Blob | null> {
  try {
    const { default: html2canvas } = await import("html2canvas-pro");
    const clearMarks = markScrollPositions();

    try {
      const canvas = await html2canvas(document.body, {
        // The viewport, not the document: what the tester can see is what they are reporting on.
        x: window.scrollX,
        y: window.scrollY,
        width: window.innerWidth,
        height: window.innerHeight,
        windowWidth: window.innerWidth,
        windowHeight: window.innerHeight,
        // 1, never devicePixelRatio: a Retina capture is four times the bytes for detail nobody
        // reading a bug report will zoom in on.
        scale: 1,
        backgroundColor: getComputedStyle(document.body).backgroundColor || "#ffffff",
        logging: false,
        // An avatar served from another origin taints the canvas, and a tainted canvas throws on
        // toBlob — losing the whole screenshot over one image. Ask for CORS, and let the library
        // skip what it cannot get.
        useCORS: true,
        allowTaint: false,
        onclone: prepareClone,
      });

      return await encode(downscale(canvas));
    } finally {
      clearMarks();
    }
  } catch (error) {
    // Never fatal. A report with no screenshot is worth far more than no report, so the widget opens
    // regardless and simply says the capture did not work.
    console.warn("Screen capture failed", error);
    return null;
  }
}

/**
 * The clone html2canvas actually renders, fixed up in the two ways it cannot manage itself.
 *
 * **Scroll offsets**, because `cloneNode` does not copy them: without this every scrollable panel —
 * which in this app is the main content area and every data grid — is captured scrolled to the top,
 * so the screenshot shows a part of the page the tester was not looking at.
 *
 * **Passwords**, because html2canvas draws an input's `value`, not the dots the browser paints over
 * it. A capture of the login screen would otherwise carry the tester's password in plain text into a
 * public GitHub issue. This is the single most important line in the file.
 */
function prepareClone(clonedDocument: Document): void {
  clonedDocument.querySelectorAll<HTMLElement>(`[${SCROLL_MARK}]`).forEach((element) => {
    const [top, left] = (element.getAttribute(SCROLL_MARK) ?? "0,0").split(",").map(Number);
    element.scrollTop = top;
    element.scrollLeft = left;
  });

  clonedDocument.querySelectorAll<HTMLInputElement>('input[type="password"]').forEach((input) => {
    input.value = "•".repeat(Math.min(input.value.length, 16));
  });

  clonedDocument.querySelectorAll<HTMLElement>(`[${REDACT_ATTRIBUTE}]`).forEach((element) => {
    element.style.filter = "blur(8px)";
  });
}

/** Tags the live document, and hands back the undo — the marks must not outlive the capture. */
function markScrollPositions(): () => void {
  const marked: HTMLElement[] = [];

  document.querySelectorAll<HTMLElement>("*").forEach((element) => {
    if (element.scrollTop || element.scrollLeft) {
      element.setAttribute(SCROLL_MARK, `${element.scrollTop},${element.scrollLeft}`);
      marked.push(element);
    }
  });

  return () => marked.forEach((element) => element.removeAttribute(SCROLL_MARK));
}

function downscale(canvas: HTMLCanvasElement): HTMLCanvasElement {
  const longest = Math.max(canvas.width, canvas.height);
  if (longest <= MAX_EDGE) return canvas;

  const ratio = MAX_EDGE / longest;
  const smaller = document.createElement("canvas");
  smaller.width = Math.round(canvas.width * ratio);
  smaller.height = Math.round(canvas.height * ratio);

  const context = smaller.getContext("2d");
  if (!context) return canvas;

  context.imageSmoothingQuality = "high";
  context.drawImage(canvas, 0, 0, smaller.width, smaller.height);
  return smaller;
}

/** PNG for the crisp text a UI screenshot is mostly made of, JPEG when that gets expensive. */
async function encode(canvas: HTMLCanvasElement): Promise<Blob | null> {
  const png = await toBlob(canvas, "image/png");
  if (!png || png.size <= MAX_PNG_BYTES) return png;
  return (await toBlob(canvas, "image/jpeg", 0.82)) ?? png;
}

function toBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}
