export interface WhatsNewHighlight {
  title: string;
  body: string;
}

export interface WhatsNewRelease {
  version: string;
  date: string;
  highlights: readonly WhatsNewHighlight[];
}

/** Newest first, two to four highlights each. A version is what a reader's "seen" mark remembers, so it never changes once shipped. */
export const WHATS_NEW: readonly WhatsNewRelease[] = [
  {
    version: "v0.20.0",
    date: "2026-10-14",
    highlights: [
      {
        title: "Tasks, @mentions and notifications",
        body: "A next action is now a task with an owner and a due date, listed in My tasks. @mention a colleague in a note, and the bell tells you when a task is yours, falls due, or an executive replies.",
      },
      {
        title: "A smoother Pipeline board",
        body: "A card lands exactly where you drop it, cards reorder within a column, and the chosen stage is clearly marked.",
      },
      {
        title: "Better briefs from a position description",
        body: "Reading a description gives competency weights that add up to 100, and keeps a mandate reason only where the document states one.",
      },
      {
        title: "Clearer form errors",
        body: "When something can't be saved, the message now names the field to fix.",
      },
    ],
  },
  {
    version: "v0.19.0",
    date: "2026-10-13",
    highlights: [
      {
        title: "Tidier pipeline stages",
        body: "Settings → Pipeline stages folds each stage into one row that opens onto its statuses; drag stages and statuses by the handle to reorder them.",
      },
      {
        title: "A faster Strategy assistant",
        body: "The assistant answers noticeably quicker and no longer gives up on a long question.",
      },
    ],
  },
  {
    version: "v0.18.0",
    date: "2026-10-13",
    highlights: [
      {
        title: "Your own pipeline, on a board",
        body: "Set the stages and statuses your workspace uses, and work a position's executives on its new Pipeline page.",
      },
      {
        title: "Rank companies against the brief",
        body: "Ask the Strategy assistant to rank and tier the companies in your universe by how well they fit the role.",
      },
      {
        title: "Help, right where you are",
        body: "Press ? anywhere for articles about the page you're on, keyboard shortcuts and a way to reach us.",
      },
    ],
  },
];

const SEEN_KEY = "lm-whats-new-seen";

export function latestUnseen(): boolean {
  try {
    return localStorage.getItem(SEEN_KEY) !== WHATS_NEW[0].version;
  } catch {
    return false;
  }
}

export function markWhatsNewSeen(): void {
  try {
    localStorage.setItem(SEEN_KEY, WHATS_NEW[0].version);
  } catch {
    // A refused write only means the dot shows again next time.
  }
}
