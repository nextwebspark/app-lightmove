import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

/**
 * jsdom lays nothing out, so its `matchMedia` answers `false` to every query — which would tell the
 * app it is on a phone and collapse every rail a test then goes looking for. Components render at
 * the desktop width unless a test says otherwise.
 */
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
