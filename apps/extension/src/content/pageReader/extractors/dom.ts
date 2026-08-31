/** Reading a `Document`, shared by every extractor. Nothing here knows what is being looked for. */

export function textOf(document: Document, selector: string): string | null {
  return document.querySelector(selector)?.textContent ?? null;
}

export function hrefOf(document: Document, selector: string): string | null {
  return document.querySelector(selector)?.getAttribute("href") ?? null;
}
