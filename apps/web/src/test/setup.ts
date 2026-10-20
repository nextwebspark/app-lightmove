import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

/** jsdom answers `false` to every media query, which would collapse rails a test goes looking for. */
window.matchMedia = (query: string): MediaQueryList =>
  ({
    matches: /min-width/.test(query),
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }) as unknown as MediaQueryList;

/** jsdom has no layout engine, so React Flow's own ResizeObserver usage needs a stand-in to mount at all. */
class NoopResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = NoopResizeObserver as unknown as typeof ResizeObserver;

/**
 * jsdom lays nothing out, and ProseMirror (the note editor) asks where the caret is on every selection change; an
 * empty box at the origin is enough for it to carry on.
 */
const NO_RECT = { x: 0, y: 0, top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0, toJSON: () => ({}) };
document.elementFromPoint ??= () => null;
Range.prototype.getClientRects ??= () => ({ length: 0, item: () => null, [Symbol.iterator]: [][Symbol.iterator] }) as unknown as DOMRectList;
Range.prototype.getBoundingClientRect ??= () => NO_RECT as DOMRect;
Element.prototype.getClientRects ??= () => ({ length: 0, item: () => null, [Symbol.iterator]: [][Symbol.iterator] }) as unknown as DOMRectList;

afterEach(() => {
  cleanup();
  // A drawer remembers its last tab for the session; one test's choice must not open the next on it.
  sessionStorage.clear();
  vi.restoreAllMocks();
});
