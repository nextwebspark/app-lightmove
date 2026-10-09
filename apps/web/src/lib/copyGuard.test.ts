import ts from "typescript";
import { describe, expect, it } from "vitest";

/**
 * docs/glossary.md's "never on screen" list, held over the SPA's prose: JSX text and any string or
 * template piece with a space in it. A one-word string is a query key, a route or an id, never copy.
 */
const FORBIDDEN = /\bNylas\b|\bthis deployment\b|\btriage\b/i;

/** Copy a mockup still draws — the mockup wins until it changes (docs/glossary.md). */
const MOCKUP_BACKED = new Set([
  "A position holds one open role end to end — brief, company universe, triage and candidates.",
]);

const sources = import.meta.glob<string>(["../**/*.tsx", "!../**/*.test.tsx"], {
  query: "?raw",
  import: "default",
  eager: true,
});

function proseOf(path: string, source: string): string[] {
  const file = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const prose: string[] = [];
  const visit = (node: ts.Node) => {
    if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) return;
    if (ts.isJsxText(node)) {
      const text = node.text.replace(/\s+/g, " ").trim();
      if (text) prose.push(text);
    } else if (
      ts.isStringLiteral(node) ||
      ts.isNoSubstitutionTemplateLiteral(node) ||
      ts.isTemplateHead(node) ||
      ts.isTemplateMiddle(node) ||
      ts.isTemplateTail(node)
    ) {
      if (/\s/.test(node.text.trim())) prose.push(node.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(file);
  return prose;
}

describe("on-screen copy", () => {
  it("never names a vendor, the deployment or the internal word triage", () => {
    const offenders = Object.entries(sources).flatMap(([path, source]) =>
      proseOf(path, source)
        .filter((text) => FORBIDDEN.test(text) && !MOCKUP_BACKED.has(text))
        .map((text) => `${path}: ${text}`),
    );

    expect(offenders).toEqual([]);
  });

  it("reads every screen", () => {
    expect(Object.keys(sources).length).toBeGreaterThan(100);
  });
});
