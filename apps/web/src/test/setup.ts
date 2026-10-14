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

afterEach(() => {
  cleanup();
  // A drawer remembers its last tab for the session; one test's choice must not open the next on it.
  sessionStorage.clear();
  vi.restoreAllMocks();
});
