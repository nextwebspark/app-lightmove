import { useState } from "react";
import { Button, Input } from "../../../components/ui";
import type { Criterion, CriterionMode } from "../api/types";
import { SectionHeading } from "./fields";

/** The candidate criteria list: inline edit, Required/Preferred segmented toggle, add and remove. */
export function CriteriaCard({
  criteria,
  disabled,
  onChange,
}: {
  criteria: Criterion[];
  disabled: boolean;
  onChange: (criteria: Criterion[]) => void;
}) {
  const [draft, setDraft] = useState("");

  const patch = (index: number, changes: Partial<Criterion>) =>
    onChange(criteria.map((c, i) => (i === index ? { ...c, ...changes } : c)));

  return (
    <div className="mb-[22px]">
      <SectionHeading
        title="Candidate Criteria"
        aside="Required narrows the field · Preferred breaks ties"
      />
      <div className="rounded-[10px] border border-line-soft bg-panel2 p-4">
        {criteria.map((criterion, index) => (
          <div
            key={index}
            className="flex items-center gap-2.5 border-b border-line-soft px-1 py-[9px]"
          >
            <input
              value={criterion.text}
              aria-label={`Criterion ${index + 1}`}
              onChange={(e) => patch(index, { text: e.target.value })}
              className="min-w-0 flex-1 border-b border-transparent bg-transparent py-1 text-[13px] font-medium text-text outline-none transition hover:border-line focus:border-sky disabled:hover:border-transparent"
            />
            {criterion.fromBrief && (
              <span className="flex-none rounded-[5px] border border-line px-[7px] py-0.5 font-mono text-[9.5px] font-medium uppercase tracking-[0.04em] text-text3">
                From brief
              </span>
            )}
            <span className="flex flex-none overflow-hidden rounded-[7px] border border-line">
              <ModeButton
                label="Required"
                active={criterion.mode === "REQUIRED"}
                activeClass="bg-red-dim text-red"
                onClick={() => patch(index, { mode: "REQUIRED" satisfies CriterionMode })}
              />
              <ModeButton
                label="Preferred"
                active={criterion.mode === "PREFERRED"}
                activeClass="bg-sky-dim text-sky"
                onClick={() => patch(index, { mode: "PREFERRED" satisfies CriterionMode })}
              />
            </span>
            <button
              type="button"
              aria-label={`Remove criterion ${index + 1}`}
              onClick={() => onChange(criteria.filter((_, i) => i !== index))}
              className="flex-none p-1 text-text3 transition hover:text-red"
            >
              ✕
            </button>
          </div>
        ))}

        {!disabled && (
          <div className="mt-3 flex gap-2">
            <Input
              value={draft}
              placeholder="Add a criterion…"
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") add();
              }}
              className="bg-panel"
            />
            <Button variant="secondary" onClick={add} className="flex-none">
              Add
            </Button>
          </div>
        )}
      </div>
    </div>
  );

  function add() {
    const text = draft.trim();
    if (!text) return;
    onChange([...criteria, { text, mode: "REQUIRED", fromBrief: false }]);
    setDraft("");
  }
}

function ModeButton({
  label,
  active,
  activeClass,
  onClick,
}: {
  label: string;
  active: boolean;
  activeClass: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`px-[11px] py-[5px] font-mono text-[11px] font-semibold transition ${
        active ? activeClass : "bg-panel text-text3"
      }`}
    >
      {label}
    </button>
  );
}
