/** `ShippedPrompts.PLACEHOLDER`'s pattern: the `{name}` template variables the server fills a prompt with. */
const PLACEHOLDER = /\{([A-Za-z_][A-Za-z0-9_]*)\}/g;

export function placeholdersIn(text: string): string[] {
  return [...new Set([...text.matchAll(PLACEHOLDER)].map((match) => match[1]))].sort();
}

/** What a draft dropped of the shipped placeholders, and what it added that nothing fills. */
export function placeholderChange(shipped: readonly string[], draft: string): { missing: string[]; added: string[] } {
  const found = placeholdersIn(draft);
  return {
    missing: shipped.filter((name) => !found.includes(name)),
    added: found.filter((name) => !shipped.includes(name)),
  };
}
