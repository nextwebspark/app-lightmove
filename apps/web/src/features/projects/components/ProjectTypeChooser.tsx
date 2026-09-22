import type { ProjectType } from "../api/types";

const CHOICES: { value: ProjectType; title: string; blurb: string }[] = [
  { value: "MAPPING", title: "Mapping", blurb: "A mapped executive universe" },
  { value: "EXECUTIVE_SEARCH", title: "Executive search", blurb: "Through to a shortlist" },
];

/**
 * What the mandate is engaged to deliver, which decides how many milestones it is measured against.
 * Two cards rather than a select: it is the one choice on this form that changes what the rest of it
 * asks for.
 */
export function ProjectTypeChooser({
  value,
  onChange,
}: {
  value: ProjectType;
  onChange: (projectType: ProjectType) => void;
}) {
  const move = (delta: number) => {
    const next = CHOICES[(CHOICES.findIndex((choice) => choice.value === value) + delta + CHOICES.length)
      % CHOICES.length];
    onChange(next.value);
  };

  return (
    <div
      role="radiogroup"
      aria-label="Project type"
      className="grid grid-cols-1 gap-2.5 sm:grid-cols-2"
      onKeyDown={(event) => {
        if (event.key === "ArrowRight" || event.key === "ArrowDown") {
          event.preventDefault();
          move(1);
        }
        if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
          event.preventDefault();
          move(-1);
        }
      }}
    >
      {CHOICES.map((choice) => {
        const selected = choice.value === value;
        return (
          <button
            key={choice.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(choice.value)}
            className={`rounded-[10px] border px-3 py-2.5 text-left transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky ${
              selected
                ? "border-amber bg-amber-dim"
                : "border-line bg-panel hover:border-text3 hover:bg-panel2"
            }`}
          >
            <span className="block text-[13px] font-semibold text-text">{choice.title}</span>
            <span className="mt-0.5 block text-[11.5px] leading-snug text-text3">{choice.blurb}</span>
          </button>
        );
      })}
    </div>
  );
}
