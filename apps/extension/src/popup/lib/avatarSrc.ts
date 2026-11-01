import { workspaceOrigin } from "../../workspaceOrigin";

/**
 * A stored photo's link is relative (`/api/v1/avatars/…`), written for the SPA, which shares the API's origin. The
 * panel is a `chrome-extension://` page, so it would resolve there and miss: it is put on the workspace origin.
 * Absolute links (a candidate's data URL, a provider's link not yet copied) pass through.
 */
export function avatarSrc(src: string | null | undefined): string | null {
  if (!src) return null;
  return src.startsWith("/") && !src.startsWith("//") ? `${workspaceOrigin}${src}` : src;
}
