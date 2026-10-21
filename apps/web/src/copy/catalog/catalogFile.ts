import { type CopyScreen, DEFAULT_MAX_LENGTH, keyOf, placeholdersOf } from "../defineScreen";
import { NEVER_ON_SCREEN_SOURCES } from "../neverOnScreen";

/**
 * `apps/api/src/main/resources/screen-text/catalog.json`: what the server checks an edit against, so it never stores
 * a key the code does not have or a sentence that drops a placeholder.
 */
export function catalogFileOf(screens: readonly CopyScreen[]): string {
  const file = {
    format: "lightmove.screen-text-catalog",
    formatVersion: 1,
    neverOnScreen: NEVER_ON_SCREEN_SOURCES,
    screens: screens.map((screen) => ({ id: screen.id, title: screen.title })),
    entries: screens.flatMap((screen) =>
      Object.entries(screen.entries).map(([local, entry]) => ({
        key: keyOf(screen, local),
        screen: screen.id,
        kind: entry.kind,
        where: entry.where,
        defaultText: entry.text,
        placeholders: placeholdersOf(entry.text),
        maxLength: Math.max(entry.maxLength ?? DEFAULT_MAX_LENGTH, entry.text.length),
      })),
    ),
  };
  return `${JSON.stringify(file, null, 2)}\n`;
}
