const PROVIDER_LABELS: Record<string, string> = { google: "Google", linkedin: "LinkedIn" };

/** A provider's own name; an id nobody wrote a label for reads as itself, capitalised. */
export function providerLabel(providerId: string): string {
  return PROVIDER_LABELS[providerId] ?? providerId.charAt(0).toUpperCase() + providerId.slice(1);
}

/** "A", "A and B", "A, B and C" — the way the mockup names the workspaces a person leaves. */
export function listOfNames(names: string[], conjunction: "and" | "or" = "and"): string {
  if (names.length <= 1) return names[0] ?? "";
  return `${names.slice(0, -1).join(", ")} ${conjunction} ${names[names.length - 1]}`;
}
