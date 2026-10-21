import { type CopyScreen, fillPlaceholders, keyOf, placeholdersOf } from "./defineScreen";

/** The bundle `GET /screen-text` answers: only the sentences a super admin changed. */
export interface ScreenTextBundle {
  locale: string;
  release: number;
  version: string;
  entries: Record<string, string>;
}

type Listener = () => void;

const STORAGE_KEY = "lm-screen-text";

let overrides: Readonly<Record<string, string>> = {};
let previewing = false;
let revision = 0;
const listeners = new Set<Listener>();

/**
 * The sentence for `local` on `screen`: the published override when there is one, else the default the code ships.
 * Never throws and never blanks — a missing or broken bundle leaves every screen on its defaults.
 */
export function screenText<Local extends string>(
  screen: CopyScreen<string, Local>,
  local: Local,
  values?: Readonly<Record<string, string | number>>,
): string {
  const fallback = screen.entries[local]?.text ?? local;
  const override = overrides[keyOf(screen, local)];
  return fillPlaceholders(usable(override, fallback) ? override : fallback, values);
}

/**
 * The server already drops an override whose placeholders no longer match, but a bundle remembered from before a
 * deploy has not been re-checked against this build's defaults — and storage is anyone's to edit.
 */
function usable(override: unknown, fallback: string): override is string {
  return typeof override === "string" && placeholdersOf(override).join() === placeholdersOf(fallback).join();
}

export function applyBundle(bundle: ScreenTextBundle, options: { preview?: boolean } = {}): void {
  overrides = Object.freeze({ ...bundle.entries });
  previewing = options.preview ?? false;
  revision += 1;
  if (!previewing) remember(bundle);
  listeners.forEach((listener) => listener());
}

export function isPreviewingScreenText(): boolean {
  return previewing;
}

export function subscribeScreenText(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function screenTextRevision(): number {
  return revision;
}

/** The bundle this browser last read, so the next boot draws edited words at once instead of after a round trip. */
export function rememberedBundle(): ScreenTextBundle | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) return null;
    const parsed = JSON.parse(stored) as Partial<ScreenTextBundle>;
    return parsed && typeof parsed.entries === "object" && parsed.entries !== null
      ? (parsed as ScreenTextBundle)
      : null;
  } catch {
    return null;
  }
}

function remember(bundle: ScreenTextBundle): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(bundle));
  } catch {
    // Private windows and full storage only cost the next boot its head start.
  }
}

/** For tests: back to the shipped defaults. */
export function resetScreenText(): void {
  overrides = {};
  previewing = false;
  revision += 1;
  listeners.forEach((listener) => listener());
}
