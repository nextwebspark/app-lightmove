import { useCallback, useEffect, useState } from "react";

/** The folds on an executive's profile panel — the candidate drawer and the People preview — in order. */
export const PROFILE_SECTIONS = [
  "outreach",
  "company",
  "summary",
  "ai",
  "experience",
  "education",
  "compensation",
  "profile",
  "background",
  "certifications",
  "publications",
  "projects",
  "volunteering",
  "contact",
  "columns",
] as const;

export type ProfileSection = (typeof PROFILE_SECTIONS)[number];

type OpenState = Record<ProfileSection, boolean>;

/** What a first visit shows: who they are and where they have been, then how to reach them. */
const DEFAULTS: OpenState = {
  outreach: true,
  company: false,
  summary: true,
  ai: false,
  experience: true,
  education: false,
  compensation: false,
  profile: false,
  background: false,
  certifications: false,
  publications: false,
  projects: false,
  volunteering: false,
  contact: true,
  columns: false,
};

const STORAGE_KEY = "lm.candidate-profile.sections.v2";

/** Who they are and where they have been: open on every profile, whatever was folded on the last one. */
const OPEN_ON_EVERY_PROFILE: readonly ProfileSection[] = ["summary", "experience"];

/**
 * Which sections of an executive's profile are unfolded, remembered per viewer and per section
 * rather than per person: a reader who folds Compensation is saying what they read first, not
 * something about one candidate. Local, like column visibility — a fold is not worth an audit event.
 */
export function useProfileSections() {
  const [open, setOpen] = useState<OpenState>(read);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(open));
    } catch {
      // A blocked store costs the preference, not the panel.
    }
  }, [open]);

  const isOpen = useCallback((section: ProfileSection) => open[section], [open]);
  const toggle = useCallback(
    (section: ProfileSection) =>
      setOpen((current) => ({ ...current, [section]: !current[section] })),
    [],
  );
  const setAll = useCallback(
    (value: boolean, only: readonly ProfileSection[] = PROFILE_SECTIONS) =>
      setOpen((current) => ({
        ...current,
        ...Object.fromEntries(only.map((id) => [id, value])),
      })),
    [],
  );

  return { isOpen, toggle, setAll };
}

function read(): OpenState {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return DEFAULTS;
    const parsed: unknown = JSON.parse(stored);
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return DEFAULTS;
    const record = parsed as Record<string, unknown>;
    // Merged over the defaults so a section added later opens as declared, and a value that is not
    // a boolean — a truncated write from another release — falls back rather than folding it shut.
    return Object.fromEntries(
      PROFILE_SECTIONS.map((id) => [
        id,
        OPEN_ON_EVERY_PROFILE.includes(id) || typeof record[id] !== "boolean" ? DEFAULTS[id] : record[id],
      ]),
    ) as OpenState;
  } catch {
    return DEFAULTS;
  }
}
