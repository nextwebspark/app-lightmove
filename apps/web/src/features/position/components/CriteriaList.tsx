import { useState } from "react";
import type { Criterion, CriterionMode } from "../api/types";
import type { StepReceipt } from "../lib/documentFill";
import { BriefButton, ChipGroup, RemoveDot, type ChipOption } from "./BriefFields";
import { ProvenanceMarker } from "./ProvenanceMarker";

const MODE_OPTIONS: ChipOption<CriterionMode>[] = [
  { value: "REQUIRED", label: "Required", tone: "offlimits" },
  { value: "PREFERRED", label: "Preferred" },
];

/**
 * The screening criteria: one line each, Required or Preferred, and where it came from. A line the
 * template drafted wears "From brief", which is also what lets a later template redraft replace it
 * while leaving what a person wrote alone.
 */
export function CriteriaList({
  criteria,
  receipt,
  onChange,
  onUndo,
}: {
  criteria: Criterion[];
  /** This session's assessment-screen receipt, for a filled row's snippet and Undo. */
  receipt?: StepReceipt;
  onChange: (criteria: Criterion[]) => void;
  onUndo?: (text: string) => void;
}) {
  const [draft, setDraft] = useState("");

  // A hand edit always makes the row theirs — a corrected DOCUMENT/TEMPLATE row must stop wearing that
  // sparkle, or a later "Read again" would silently overwrite what was just typed.
  const patch = (index: number, changes: Partial<Criterion>) =>
    onChange(
      criteria.map((criterion, i) => (i === index ? { ...criterion, ...changes, source: "MANUAL" } : criterion)),
    );

  const add = () => {
    const text = draft.trim();
    if (!text) return;
    onChange([...criteria, { text, mode: "REQUIRED", source: "MANUAL" }]);
    setDraft("");
  };

  return (
    <div className="rounded-[11px] bg-u-surface shadow-u-e1">
      {criteria.map((criterion, index) => (
        <div key={index} className="flex flex-wrap items-center gap-3 border-b border-u-border px-4 py-3">
          <input
            value={criterion.text}
            aria-label={`Criterion ${index + 1}`}
            onChange={(event) => patch(index, { text: event.target.value })}
            className="min-w-[160px] flex-1 bg-transparent text-body text-u-text outline-none"
          />
          {criterion.source === "TEMPLATE" && (
            <span className="flex-none rounded-[4px] bg-u-accent-tint px-1.5 py-0.5 type-tag text-u-accent">
              From brief
            </span>
          )}
          <ProvenanceMarker
            source={criterion.source}
            confidence={receipt?.lists.criteria?.appended[criterion.text]?.confidence}
            snippet={receipt?.lists.criteria?.appended[criterion.text]?.snippet}
            fileName={receipt?.lists.criteria?.appended[criterion.text] && receipt.fileName}
            onUndo={onUndo ? () => onUndo(criterion.text) : undefined}
          />
          <ChipGroup
            size="sm"
            label={`Criterion ${index + 1} mode`}
            options={MODE_OPTIONS}
            value={criterion.mode}
            onChange={(mode) => mode && patch(index, { mode })}
            className="flex-nowrap"
          />
          <RemoveDot
            label={`Remove criterion ${index + 1}`}
            onClick={() => onChange(criteria.filter((_, i) => i !== index))}
          />
        </div>
      ))}

      <div className="flex items-center gap-3 px-4 py-3">
        <input
          value={draft}
          aria-label="Add a criterion"
          placeholder="+ Add a criterion…"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            add();
          }}
          className="min-w-0 flex-1 bg-transparent text-body text-u-text outline-none placeholder:text-u-text3"
        />
        {draft.trim() && (
          <BriefButton variant="link" onClick={add} className="px-0">
            Add
          </BriefButton>
        )}
      </div>
    </div>
  );
}
