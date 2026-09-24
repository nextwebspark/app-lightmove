import { useState } from "react";
import { Button, Input } from "../../../components/ui";
import type { Criterion, CriterionMode } from "../../position/api/types";
import { SectionHeading } from "./fields";

/** The candidate criteria list: inline edit, Required/Preferred segmented toggle, add and remove. */
export function CriteriaCard({
  criteria,
  onChange,
}: {
  criteria: Criterion[];
  onChange: (criteria: Criterion[]) => void;
}) {
  const [draft, setDraft] = useState("");

  const patch = (index: number, changes: Partial<Criterion>) =>
    onChange(criteria.map((c, i) => (i === index ? { ...c, ...changes } : c)));

  return (
    <div>
      <SectionHeading
        title="Screening criteria"
        aside="required narrows the field · preferred breaks ties"
      />
      <div className="rounded-[10px] border border-u-border bg-u-raised p-4">
        {criteria.map((criterion, index) => (
          <div
            key={index}
            className="flex items-center gap-2.5 border-b border-u-border px-1 py-[9px]"
          >
            <input
              value={criterion.text}
              aria-label={`Criterion ${index + 1}`}
              onChange={(e) => patch(index, { text: e.target.value })}
              className="min-w-0 flex-1 border-b border-transparent bg-transparent py-1 text-[13px] font-medium text-u-text outline-none transition hover:border-u-border-strong focus:border-u-accent"
            />
            {criterion.source === "TEMPLATE" && (
              <span className="flex-none rounded-[5px] border border-u-border-strong px-[7px] py-0.5 font-mono text-[9.5px] font-medium uppercase tracking-[0.04em] text-u-text3">
                From brief
              </span>
            )}
            <span className="flex flex-none overflow-hidden rounded-[7px] border border-u-border-strong">
              <ModeButton
                label="Required"
                active={criterion.mode === "REQUIRED"}
                activeClass="bg-u-offlimits-tint text-u-offlimits"
                onClick={() => patch(index, { mode: "REQUIRED" satisfies CriterionMode })}
              />
              <ModeButton
                label="Preferred"
                active={criterion.mode === "PREFERRED"}
                activeClass="bg-u-accent-tint text-u-accent"
                onClick={() => patch(index, { mode: "PREFERRED" satisfies CriterionMode })}
              />
            </span>
            <button
              type="button"
              aria-label={`Remove criterion ${index + 1}`}
              onClick={() => onChange(criteria.filter((_, i) => i !== index))}
              className="flex-none p-1 text-u-text3 transition hover:text-u-offlimits"
            >
              ✕
            </button>
          </div>
        ))}

        <div className="mt-3 flex gap-2">
          <Input
            value={draft}
            placeholder="Add a criterion…"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") add();
            }}
            className="bg-u-surface"
          />
          <Button variant="secondary" onClick={add} className="flex-none">
            Add
          </Button>
        </div>
      </div>
    </div>
  );

  function add() {
    const text = draft.trim();
    if (!text) return;
    onChange([...criteria, { text, mode: "REQUIRED", source: "MANUAL" }]);
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
        active ? activeClass : "bg-u-surface text-u-text3"
      }`}
    >
      {label}
    </button>
  );
}
