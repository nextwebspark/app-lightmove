import { vi } from "vitest";

/**
 * jsdom implements no part of the Fullscreen API — `requestFullscreen`, `exitFullscreen` and
 * `fullscreenElement` are all absent, so `vi.spyOn` throws and `defineProperty` is the only way in.
 * The fake announces every transition through `fullscreenchange`, which is the signal the whole
 * feature hangs off.
 */
export function stubFullscreenApi() {
  let element: Element | null = null;
  const announce = () => document.dispatchEvent(new Event("fullscreenchange"));
  const requestFullscreen = vi.fn(function (this: Element) {
    element = this;
    announce();
    return Promise.resolve();
  });
  const exitFullscreen = vi.fn(() => {
    element = null;
    announce();
    return Promise.resolve();
  });

  Object.defineProperty(Element.prototype, "requestFullscreen", {
    value: requestFullscreen,
    configurable: true,
    writable: true,
  });
  Object.defineProperty(document, "exitFullscreen", {
    value: exitFullscreen,
    configurable: true,
    writable: true,
  });
  Object.defineProperty(document, "fullscreenElement", { get: () => element, configurable: true });

  return {
    requestFullscreen,
    exitFullscreen,
    /** The browser acting on its own — F11 in, Escape or its own exit control back out. */
    setElement: (next: Element | null) => {
      element = next;
      announce();
    },
    restore: () => {
      Reflect.deleteProperty(Element.prototype, "requestFullscreen");
      Reflect.deleteProperty(document, "exitFullscreen");
      Reflect.deleteProperty(document, "fullscreenElement");
    },
  };
}
