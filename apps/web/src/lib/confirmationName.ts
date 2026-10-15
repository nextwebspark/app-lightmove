/**
 * Whether a name typed to confirm a destructive act is the name on screen: case, spacing and dashes forgiven,
 * since "Group CFO – Energy" carries an en dash no keyboard offers. The server's `ConfirmationName` applies the
 * same rule, so the enabled button and the server's answer never disagree.
 */
export function confirmationNameMatches(expected: string, typed: string): boolean {
  return typed.trim() !== "" && comparable(expected) === comparable(typed);
}

function comparable(name: string): string {
  return name
    .normalize("NFKC")
    .replace(/[‐-―−]/g, "-")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}
