/** docs/glossary.md's "never on screen" list, which `copyGuard.test.ts` holds the code to. */
export const NEVER_ON_SCREEN_SOURCES: readonly string[] = ["\\bNylas\\b", "\\bthis deployment\\b", "\\btriage\\b"];

export const NEVER_ON_SCREEN = new RegExp(NEVER_ON_SCREEN_SOURCES.join("|"), "i");

/**
 * What an edited sentence may not bring in: the list above, and the names `errorCodes.coverage.test.ts` holds the shipped
 * error wording to. Wider than the code's guard because a super admin's edit has no review before it is live. The
 * catalogue file carries it to the server, which refuses an edit that brings one in.
 */
export const NEVER_IN_EDITED_TEXT_SOURCES: readonly string[] = [
  ...NEVER_ON_SCREEN_SOURCES,
  "\\bdeployment\\b",
  "\\bmandate\\b",
  "\\bcontactout\\b",
  "\\bbright ?data\\b",
  "\\bapollo\\b",
  "\\blightmove\\b",
];
