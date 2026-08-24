import { cn } from "../lib/cn";

export type CaptureSubject = "person" | "company";

interface CaptureTabsProps {
  active: CaptureSubject;
  onSelect: (subject: CaptureSubject) => void;
}

/**
 * Person | Company.
 *
 * Person is shown and selectable even though it captures nothing yet: it is in the design, and a tab
 * that is simply missing reads as a bug where one that says what is coming reads as a roadmap. What it
 * must not be is a dead control that looks live — selecting it says so plainly.
 */
export function CaptureTabs({ active, onSelect }: CaptureTabsProps) {
  return (
    <div className="flex gap-0.5 border-b border-line-soft px-3.5" role="tablist">
      {(["person", "company"] as const).map((subject) => (
        <button
          key={subject}
          role="tab"
          type="button"
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
