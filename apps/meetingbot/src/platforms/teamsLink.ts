/**
 * The Teams links the bot opens, the server's own rule (`NotetakerPlatform.hostingAt`): an https link on Teams' work or
 * personal host and nothing else, for the reason `meetLink.ts` gives — a calendar invite's link is anyone's to write.
 */
const TEAMS_LINK_HOSTS = new Set(["teams.microsoft.com", "teams.live.com"]);

/** Where Teams' web client may take the call's tab: its own hosts, the one it moved to among them. */
const TEAMS_PAGE_HOSTS = new Set([...TEAMS_LINK_HOSTS, "teams.cloud.microsoft"]);

export function isTeamsLink(joinUrl: string): boolean {
  try {
    const url = new URL(joinUrl);
    return url.protocol === "https:" && TEAMS_LINK_HOSTS.has(url.hostname) && url.port === ""
      && url.username === "" && url.password === "";
  } catch {
    return false;
  }
}

/**
 * Never a sign-in page: the bot joins as a guest, and a meeting that sends it to sign in is one whose organiser's
 * tenant does not let guests in — the page is held where it is and read as blocked.
 */
export function isAllowedTeamsNavigation(url: string): boolean {
  try {
    const { protocol, hostname } = new URL(url);
    if (protocol !== "https:") {
      return protocol === "about:" || protocol === "data:" || protocol === "blob:";
    }
    return TEAMS_PAGE_HOSTS.has(hostname);
  } catch {
    return false;
  }
}
