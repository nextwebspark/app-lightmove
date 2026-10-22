/**
 * The only links the bot opens. A join link comes from a calendar event, which anyone invited can write, and the
 * browser runs without Chromium's sandbox inside a container that holds the run's token — so it goes to Meet and
 * nowhere else.
 */
export function isMeetLink(joinUrl: string): boolean {
  try {
    const url = new URL(joinUrl);
    return url.protocol === "https:" && url.hostname === "meet.google.com" && url.port === ""
      && url.username === "" && url.password === "";
  } catch {
    return false;
  }
}

/** Never reachable from the page: the cloud's metadata server hands out the container's credentials. */
export const BLOCKED_HOSTS = new Set(["metadata.google.internal", "metadata", "169.254.169.254"]);

/** Where Meet's own page may take the call's tab (a guest sign-in step lands on accounts.google.com). */
export function isAllowedNavigation(url: string): boolean {
  try {
    const { protocol, hostname } = new URL(url);
    if (protocol !== "https:") {
      return protocol === "about:" || protocol === "data:" || protocol === "blob:";
    }
    return hostname === "meet.google.com" || hostname === "accounts.google.com" || hostname === "www.google.com";
  } catch {
    return false;
  }
}
