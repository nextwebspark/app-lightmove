import ts from "typescript";
import { describe, expect, it } from "vitest";
import baseline from "./styleRatchet.baseline.json";

/**
 * The visual system's ratchet: outside `components/ui`, no file may gain an arbitrary type size, an
 * arbitrary radius or an `!important` override of a primitive. The scale's steps (`text-note`,
 * `text-body`, …) and `Button`'s `size` are the way in; a mockup size the scale truly lacks belongs in
 * `components/ui` or `styles/tokens.css`.
 *
 * Counts only go down. When a file loses some, the baseline is rewritten to match:
 *   UPDATE_STYLE_BASELINE=1 npx vitest run src/lib/styleRatchet.test.ts
 */
const KINDS = {
  arbitraryText: /^(?:[\w-]+:)*!?text-\[\d+(?:\.\d+)?px\]$/,
  arbitraryRadius: /^(?:[\w-]+:)*!?rounded(?:-[a-z]{1,2})?-\[\d+(?:\.\d+)?px\]$/,
  // Both spellings: Tailwind v3's `!px-3` and v4's `px-3!`; prose such as "Done!" is neither.
  important: /^(?:[\w-]+:)*!-?[a-z]|^(?:[\w-]+:)*[a-z]+-[\w[\].\-/%#]+!$/,
} as const;

type Kind = keyof typeof KINDS;
type Counts = Partial<Record<Kind, number>>;

const sources = import.meta.glob<string>(
  ["../**/*.{ts,tsx}", "!../**/*.test.{ts,tsx}", "!../components/ui/**", "!../test/**"],
  { query: "?raw", import: "default", eager: true },
);

function classTokensOf(path: string, source: string): string[] {
  const file = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const tokens: string[] = [];
  const visit = (node: ts.Node) => {
    if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) return;
    if (
      ts.isStringLiteral(node) ||
      ts.isNoSubstitutionTemplateLiteral(node) ||
      ts.isTemplateHead(node) ||
      ts.isTemplateMiddle(node) ||
      ts.isTemplateTail(node)
    ) {
      tokens.push(...node.text.split(/\s+/).filter(Boolean));
    }
    ts.forEachChild(node, visit);
  };
  visit(file);
  return tokens;
}

function countsOf(path: string, source: string): Counts {
  const counts: Counts = {};
  for (const token of classTokensOf(path, source)) {
    for (const kind of Object.keys(KINDS) as Kind[]) {
      if (KINDS[kind].test(token)) counts[kind] = (counts[kind] ?? 0) + 1;
    }
  }
  return counts;
}

const current: Record<string, Counts> = Object.fromEntries(
  Object.entries(sources)
    .map(([path, source]) => [path.replace(/^\.\.\//, ""), countsOf(path, source)] as const)
    .filter(([, counts]) => Object.keys(counts).length > 0)
    .sort(([a], [b]) => a.localeCompare(b)),
);

const updating = Boolean(import.meta.env.UPDATE_STYLE_BASELINE);

if (updating) {
  // Loaded only here: the app's tsconfig carries no Node types, and jsdom gives import.meta.url an http
  // scheme, so the path is relative to apps/web, where vitest runs.
  const fs = (await import(/* @vite-ignore */ `node:${"fs"}`)) as {
    writeFileSync: (path: string, data: string) => void;
  };
  fs.writeFileSync("src/lib/styleRatchet.baseline.json", `${JSON.stringify(current, null, 2)}\n`);
}

describe("style ratchet", () => {
  const allowed = updating ? current : (baseline as Record<string, Counts>);

  it("lets no file gain an arbitrary size, an arbitrary radius or an !important override", () => {
    const grown = Object.entries(current).flatMap(([path, counts]) =>
      (Object.keys(counts) as Kind[])
        .filter((kind) => (counts[kind] ?? 0) > (allowed[path]?.[kind] ?? 0))
        .map((kind) => `${path}: ${kind} ${allowed[path]?.[kind] ?? 0} → ${counts[kind]}`),
    );

    expect(grown).toEqual([]);
  });

  it("holds the baseline to what the code still has, so a removal cannot be spent again", () => {
    const slack = Object.entries(allowed).flatMap(([path, counts]) =>
      (Object.keys(counts) as Kind[])
        .filter((kind) => (current[path]?.[kind] ?? 0) < (counts[kind] ?? 0))
        .map((kind) => `${path}: ${kind} ${counts[kind]} → ${current[path]?.[kind] ?? 0}`),
    );

    expect(slack, "rerun with UPDATE_STYLE_BASELINE=1 to lower the baseline").toEqual([]);
  });

  it("reads the screens", () => {
    expect(Object.keys(sources).length).toBeGreaterThan(100);
  });
});
