/**
 * What the popup remembers about how it should behave. Extension-local; none of it reaches the
 * workspace. Two settings rather than the design's four — the rest would gate features that do not
 * exist. Here rather than beside its hook so the worker can read it without importing React.
 */
export interface CaptureSettings {
  /** The mandate to preselect, or null to keep offering the last one used. */
  defaultProjectId: string | null;
  /** Whether the popup closes itself once a capture lands. */
  closesAfterSave: boolean;
}

export const DEFAULT_CAPTURE_SETTINGS: CaptureSettings = {
  defaultProjectId: null,
  closesAfterSave: false,
};
