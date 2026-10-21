/**
 * docs/glossary.md's "never on screen" list. Shared by `copyGuard.test.ts`, which holds the code to it, and by the
 * server, which refuses an edited sentence that brings one in (the catalogue file carries these sources).
 */
export const NEVER_ON_SCREEN_SOURCES: readonly string[] = ["\\bNylas\\b", "\\bthis deployment\\b", "\\btriage\\b"];

export const NEVER_ON_SCREEN = new RegExp(NEVER_ON_SCREEN_SOURCES.join("|"), "i");
