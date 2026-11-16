const PROFILE_URL = /^(?:https?:\/\/)?(?:[\w-]+\.)*linkedin\.com\/in\/([^/?#\s]+)/i;

/** The `/in/<slug>` a member profile link names, lower-cased as the server keys it; null when it is not one. */
export function profileSlugOf(value: string | null | undefined): string | null {
  const match = PROFILE_URL.exec(value?.trim() ?? "");
  return match ? match[1].toLowerCase() : null;
}

/** A member's profile on a LinkedIn host, `https://` optional — never a company or search page. */
export function isLinkedInProfileUrl(value: string | null | undefined): boolean {
  return profileSlugOf(value) !== null;
}
