const MAINTAINERS_HEADING = "\n## For Uncava maintainers";
const DOCUMENTED_ORIGIN = "https://beta.uncava.com";

/** A guide as its readers see it: the maintainers' notes left off, and every URL on the origin they are reading it at. */
export function publicGuide(markdown: string, origin: string): string {
  const cut = markdown.indexOf(MAINTAINERS_HEADING);
  const shown = cut === -1 ? markdown : markdown.slice(0, cut);
  return shown.replaceAll(DOCUMENTED_ORIGIN, origin.replace(/\/+$/, "")).trimEnd() + "\n";
}
