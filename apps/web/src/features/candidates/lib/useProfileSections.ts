import { useCallback, useEffect, useState } from "react";

/** The folds on the profile panel, in the order they appear. */
export const PROFILE_SECTIONS = [
  "summary",
  "experience",
  "education",
  "compensation",
  "background",
  "contact",
  "columns",
  "note",
] as const;

export type ProfileSection = (typeof PROFILE_SECTIONS)[number];

type OpenState = Record<ProfileSection, boolean>;

/** What a first visit shows: the three things a consultant reads before deciding to call. */
const DEFAULTS: OpenState = {
  summary: true,
  experience: true,
  education: false,
  compensation: true,
  background: false,
  contact: false,
  columns: false,
  note: false,
};

const STORAGE_KEY = "lm.candidate-profile.sections";

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
    (value: boolean) =>
      setOpen(Object.fromEntries(PROFILE_SECTIONS.map((id) => [id, value])) as OpenState),
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
        typeof record[id] === "boolean" ? record[id] : DEFAULTS[id],
      ]),
    ) as OpenState;
  } catch {
    return DEFAULTS;
  }
}
