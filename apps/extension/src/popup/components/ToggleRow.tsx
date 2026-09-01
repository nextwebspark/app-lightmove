import { cn } from "../lib/cn";

interface ToggleRowProps {
  label: string;
  hint: string;
  isOn: boolean;
  onToggle: (isOn: boolean) => void;
}

/** One switch and what it does, per the design's behaviour list. */
export function ToggleRow({ label, hint, isOn, onToggle }: ToggleRowProps) {
  return (
    <div className="flex items-center gap-3 px-2.5 py-2">
      <span className="flex-1">
        <span className="block text-[12.5px] font-medium text-text">{label}</span>
        <span className="block text-[10.5px] text-text3">{hint}</span>
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={isOn}
        aria-label={label}
        onClick={() => onToggle(!isOn)}
        className={cn(
          "relative h-[18px] w-8 shrink-0 rounded-full transition-colors",
          isOn ? "bg-amber-dim" : "bg-panel2 border border-line",
        )}
      >
        <span
          aria-hidden
          className={cn(
            "absolute top-[2px] h-3 w-3 rounded-full transition-all",
            isOn ? "left-[17px] bg-amber" : "left-[3px] bg-text3",
          )}
        />
      </button>
    </div>
  );
}
