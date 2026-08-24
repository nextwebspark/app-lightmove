/** A 16px stroke icon from a mockup path. All nav/menu glyphs are single-path SVGs there. */
export function Icon({ d, size = 16, className }: { d: string; size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className={className}
      aria-hidden="true"
    >
      <path d={d} />
    </svg>
  );
}

/** Mockup glyphs, named. One place so two screens cannot draw "team" differently. */
export const ICONS = {
  myProjects: "M12 2a10 10 0 1 0 .01 0M12 8a4 4 0 1 0 .01 0",
  allProjects: "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z",
  clients: "M3 21h18M5 21V7l7-4 7 4v14M9 9h.01M9 13h.01M9 17h.01M15 9h.01M15 13h.01M15 17h.01",
  team: "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75",
  settings:
    "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.01a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z",
  back: "M19 12H5M12 19l-7-7 7-7",
  /** One person, for the caller's own settings — "members" is the same glyph with a second figure. */
  profile: "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  signOut: "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9",
  members: "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75",
  moon: "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z",
  sun: "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
  collapse: "m11 17-5-5 5-5M18 17l-5-5 5-5",
  expand: "m13 17 5-5-5-5M6 17l5-5-5-5",
  plus: "M12 5v14M5 12h14",
  lock: "M19 11H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2ZM7 11V7a5 5 0 0 1 10 0v4",
  info: "M12 16v-4M12 8h.01M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z",
  laptop: "M2 4h20v12H2zM8 20h8M12 16v4",
  phone: "M7 2h10v20H7zM11 19h2",
  trash: "M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m2 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M10 11v6M14 11v6",
  calendar:
    "M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z",
  search: "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm10 18-4.3-4.3",
  globe: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM3.5 9h17M3.5 15h17M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18Z",
  // Stroked like every other glyph here rather than the filled brand mark, which would be the only
  // filled icon in the app and would need its own component to hold it.
  linkedin: "M16 8.5a5.5 5.5 0 0 1 5.5 5.5V21h-3.6v-7a1.9 1.9 0 0 0-3.8 0v7h-3.6v-12h3.6v1.4M3 9.5h3.6V21H3zM4.8 4a1.6 1.6 0 1 0 .01 0",
  briefcase: "M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2ZM16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2",
  facebook: "M15 3h-2.5A3.5 3.5 0 0 0 9 6.5V9H6.5v3.5H9V21h3.5v-8.5H15L15.5 9h-3V6.5a1 1 0 0 1 1-1H15Z",
  x: "m4 4 16 16M20 4 4 20",
  chevronDown: "m6 9 6 6 6-6",
  chevronRight: "m9 18 6-6-6-6",
  check: "m5 13 4 4L19 7",
  /** Three vertical tracks — the Columns menu, where a track is a column of the table. */
  columns: "M3 4h18v16H3zM9 4v16M15 4v16",
  position: "M12 2 3 7l9 5 9-5-9-5ZM3 12l9 5 9-5M3 17l9 5 9-5",
  strategy: "M12 20V10M18 20V4M6 20v-4",
  /** Triage: two decided rows and two undecided ones — the screen is a sorting job, not a search. */
  triage: "m3 6 2 2 3-3M3 15l2 2 3-3M13 7h8M13 17h8",
  /** The three triage stages, in the order the sidebar lists them. Globe is also the universe. */
  star: "m12 3 2.6 5.6 6 .8-4.4 4.2 1.1 6-5.3-2.9-5.3 2.9 1.1-6L3.4 9.4l6-.8L12 3Z",
  close: "M18 6 6 18M6 6l12 12",
  warning: "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0ZM12 9v4M12 17h.01",
  candidates: "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z",
  outreach: "M22 2 11 13M22 2l-7 20-4-9-9-4 20-7Z",
  reports: "M9 12h6m-6 4h6M9 8h1M5 21h14a2 2 0 0 0 2-2V7l-5-5H5a2 2 0 0 0-2 2v15a2 2 0 0 0 2 2Z",
} as const;
