/** What a sentence is on screen, so the editor can say where it shows and how it should read. */
export type CopyKind = "title" | "label" | "help" | "button" | "toast" | "error" | "empty";

export interface CopyEntry {
  readonly text: string;
  /** Where the sentence shows, in words a super admin recognises on the screen. */
  readonly where: string;
  readonly kind: CopyKind;
  readonly maxLength?: number;
}

export interface CopyScreen<Id extends string = string, Local extends string = string> {
  readonly id: Id;
  readonly title: string;
  readonly entries: Readonly<Record<Local, CopyEntry>>;
}

export const DEFAULT_MAX_LENGTH = 300;

/**
 * One screen group's editable sentences, with their defaults. A screen imports its own group, so its words travel in
 * its own chunk; the key a super admin edits is `${id}.${local}`.
 */
export function defineScreen<const Id extends string, const Local extends string>(
  id: Id,
  title: string,
  entries: Record<Local, CopyEntry>,
): CopyScreen<Id, Local> {
  return Object.freeze({ id, title, entries: Object.freeze(entries) });
}

export function keyOf(screen: CopyScreen, local: string): string {
  return `${screen.id}.${local}`;
}

const PLACEHOLDER = /\{([A-Za-z][A-Za-z0-9]*)\}/g;

export function placeholdersOf(text: string): string[] {
  return [...new Set([...text.matchAll(PLACEHOLDER)].map((match) => match[1]))].sort();
}

export function fillPlaceholders(text: string, values: Readonly<Record<string, string | number>> = {}): string {
  return text.replace(PLACEHOLDER, (whole, name: string) => (name in values ? String(values[name]) : whole));
}
