import { cn } from "../lib/cn";

export const CAPTURE_SUBJECTS = ["person", "company"] as const;
export type CaptureSubject = (typeof CAPTURE_SUBJECTS)[number];

/** The panel a tab drives, so the tab roles below describe something real to a screen reader. */
export function capturePanelId(subject: CaptureSubject): string {
  return `capture-panel-${subject}`;
}

interface CaptureTabsProps {
  active: CaptureSubject;
  onSelect: (subject: CaptureSubject) => void;
}

/**
 * Person | Company — the two things a page can be, and both capture.
 *
 * Which one opens is preselected from what the page turned out to be, and switching is free: the read
 * that filled both forms happened once, before either was rendered.
 */
export function CaptureTabs({ active, onSelect }: CaptureTabsProps) {
  return (
    <div className="flex gap-0.5 border-b border-line-soft px-3.5" role="tablist">
      {CAPTURE_SUBJECTS.map((subject) => (
        <button
          key={subject}
          role="tab"
          type="button"
          id={`capture-tab-${subject}`}
          aria-controls={capturePanelId(subject)}
          aria-selected={active === subject}
          onClick={() => onSelect(subject)}
          className={cn(
            "border-b-2 px-1 py-[9px] text-[12px] capitalize",
            subject === "person" && "mr-3.5",
            active === subject
              ? "border-amber-btn font-semibold text-text"
              : "border-transparent font-medium text-text3 hover:text-text2",
          )}
        >
          {subject}
        </button>
      ))}
    </div>
  );
}
