/** Newest first. The id is what a reader's "seen" mark remembers, so it never changes once shipped. */
export const WHATS_NEW = [
  {
    id: "2026-10-help",
    date: "2026-10-09",
    title: "Help, right where you are",
    body: "Press ? anywhere for articles about the page you're on, keyboard shortcuts and a way to reach us.",
  },
  {
    id: "2026-10-getting-started",
    date: "2026-10-09",
    title: "Get your first map in 30 minutes",
    body: "A checklist on My positions takes a new workspace from its first position to its first outreach.",
  },
  {
    id: "2026-10-menus",
    date: "2026-10-09",
    title: "Your workspace on the left, you on the right",
    body: "Switch workspaces from the workspace's name; your profile, theme and sign out are on your avatar.",
  },
] as const;

const SEEN_KEY = "lm-whats-new-seen";

export function latestUnseen(): boolean {
  try {
    return localStorage.getItem(SEEN_KEY) !== WHATS_NEW[0].id;
  } catch {
    return false;
  }
}

export function markWhatsNewSeen(): void {
  try {
    localStorage.setItem(SEEN_KEY, WHATS_NEW[0].id);
  } catch {
    // A refused write only means the dot shows again next time.
  }
}
