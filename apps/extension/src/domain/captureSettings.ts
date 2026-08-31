/**
 * What the popup remembers about how the consultant wants it to behave.
 *
 * Extension-local and nothing else: none of this reaches the workspace, because none of it is a fact
 * about a mandate. Three settings rather than the design's four, because the rest gate features that
 * do not exist — a toggle for a behaviour nothing implements is a dead control that looks live.
 *
 * Here rather than beside its hook because the service worker owns the storage and must not import
 * anything the popup's React tree drags with it.
 */
export interface CaptureSettings {
  /** The mandate to preselect, or null to keep offering the last one used. */
  defaultProjectId: string | null;
  /** Whether the Person/Company tab follows what the page turned out to be. */
  isPageTypeDetected: boolean;
  /** Whether the popup closes itself once a capture lands. */
  closesAfterSave: boolean;
}

export const DEFAULT_CAPTURE_SETTINGS: CaptureSettings = {
  defaultProjectId: null,
  isPageTypeDetected: true,
  closesAfterSave: false,
};
