/**
 * Reading schema.org JSON-LD off a page, shared by the company and person extractors.
 *
 * Nothing here knows what is being looked for: it walks the blocks, hands back the nodes of the types
 * asked for, and coerces schema.org's several spellings of a scalar into text.
 */

export type JsonLdNode = Record<string, unknown>;

/**
 * Every node of the given `@type`s, in document order, across every JSON-LD block on the page.
 *
 * All of them rather than the first: a page commonly carries a publisher or `WebSite` stub — name and
 * logo, nothing else — ahead of the real node, and because the merge is first-non-empty that one bad
 * early answer would beat every good later one.
 */
export function nodesOfType(document: Document, types: ReadonlySet<string>): JsonLdNode[] {
  const found: JsonLdNode[] = [];
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(script.textContent ?? "");
    } catch {
      // A malformed block on one page must not stop the others being read.
      continue;
    }
    for (const node of flattenGraph(parsed)) {
      if (isOfType(node, types)) {
        found.push(node);
      }
    }
  }
  return found;
}

/**
 * Walks every JSON-LD block, including the `@graph` array pages commonly wrap everything in — the node
 * wanted is very often the second or third there, behind a WebSite and a WebPage.
 */
function flattenGraph(value: unknown, depth = 0): JsonLdNode[] {
  if (depth > 4 || value === null || typeof value !== "object") {
    return [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((entry) => flattenGraph(entry, depth + 1));
  }
  const node = value as JsonLdNode;
  return [node, ...flattenGraph(node["@graph"], depth + 1)];
}

function isOfType(node: JsonLdNode, types: ReadonlySet<string>): boolean {
  const type = node["@type"];
  if (typeof type === "string") {
    return types.has(type);
  }
  return Array.isArray(type) && type.some((entry) => typeof entry === "string" && types.has(entry));
}

export function asText(value: unknown): string | null {
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number") {
    return String(value);
  }
  // schema.org lets almost anything be an object with a `name`, e.g. addressCountry as a Country.
  const record = asRecord(value);
  return record && typeof record["name"] === "string" ? record["name"] : null;
}

export function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

export function metaContent(document: Document, name: string): string | null {
  const tag = document.querySelector(`meta[property="${name}"], meta[name="${name}"]`);
  return tag?.getAttribute("content") ?? null;
}

export function canonicalHref(document: Document): string | null {
  return document.querySelector('link[rel="canonical"]')?.getAttribute("href") ?? null;
}
