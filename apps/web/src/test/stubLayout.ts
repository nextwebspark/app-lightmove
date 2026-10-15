import { vi } from "vitest";

/** jsdom lays nothing out: gives every element `rowWidth` of room and the width `widthOf` answers for it. */
export function stubLayout({ rowWidth, widthOf }: { rowWidth: number; widthOf: (element: Element) => number }) {
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(rowWidth);
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function (this: Element) {
    const width = widthOf(this);
    return { x: 0, y: 0, top: 0, left: 0, right: width, bottom: 0, width, height: 0, toJSON: () => ({}) };
  });
}

/** A pill `useFittingCount` measures. */
export const isFittingPill = (element: Element) => element.hasAttribute("data-fitting-pill");

/** Text a reader is shown: left out of the row means hidden from assistive technology too. */
export const SHOWN = { ignore: "script, style, [aria-hidden='true'], [aria-hidden='true'] *" };
