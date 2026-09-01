/** The two things a page can be about, and the panel each tab drives. */
export const CAPTURE_SUBJECTS = ["person", "company"] as const;

export type CaptureSubject = (typeof CAPTURE_SUBJECTS)[number];

/** The panel a tab drives, so the tab roles describe something real to a screen reader. */
export function capturePanelId(subject: CaptureSubject): string {
  return `capture-panel-${subject}`;
}
