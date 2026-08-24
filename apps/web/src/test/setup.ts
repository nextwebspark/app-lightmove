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

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
