import { useState } from "react";
import { Input, Select, TextArea } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import type { MandateContext, StrategicPriority } from "../../api/types";
import { MANDATE_REASON_LABELS } from "../../lib/labels";
import { AddRowButton, RemoveRowButton, StepField } from "../fields";

/** Step two: why the mandate exists. Internal throughout — no candidate ever reads any of it. */
export function MandateContextStep({
  context,
  onChange,
}: {
  context: MandateContext;
  onChange: (patch: Partial<MandateContext>, immediate?: boolean) => void;
}) {
  const [draft, setDraft] = useState<string | null>(null);

  const replacePriorities = (priorities: StrategicPriority[]) =>
    onChange({ strategicPriorities: priorities }, true);

  const addPriority = () => {
    const name = draft?.trim();
    setDraft(null);
    if (!name) return;
    // The same priority twice says nothing twice, and the two chips would be indistinguishable.
    const known = context.strategicPriorities.some(
      (each) => each.name.toLowerCase() === name.toLowerCase(),
    );
    if (known) return;
    replacePriorities([...context.strategicPriorities, { name, selected: true }]);
  };

  return (
    <div className="flex flex-col gap-5">
      <StepField label="Business driver">
        <TextArea
          value={context.businessDriver ?? ""}
          rows={3}
          placeholder="What has to change in the business for this hire to have been worth making…"
          onChange={(event) => onChange({ businessDriver: event.target.value || null })}
        />
      </StepField>

      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Strategic priority alignment
        </span>
        <div className="flex flex-wrap gap-2">
          {context.strategicPriorities.map((priority, index) => (
            <PriorityChip
              key={`${priority.name}-${index}`}
              priority={priority}
              onToggle={() =>
                replacePriorities(
                  context.strategicPriorities.map((each, at) =>
                    at === index ? { ...each, selected: !each.selected } : each,
                  ),
                )
              }
              onRemove={() =>
                replacePriorities(context.strategicPriorities.filter((_, at) => at !== index))
              }
            />
          ))}
        </div>
        <div className="mt-2.5">
          {draft === null ? (
            <AddRowButton onClick={() => setDraft("")}>+ Add priority</AddRowButton>
          ) : (
            <div className="flex max-w-[360px] gap-2">
              <Input
                autoFocus
                value={draft}
                aria-label="Name the priority"
                placeholder="Name the priority…"
                onChange={(event) => setDraft(event.target.value)}
                onBlur={addPriority}
                onKeyDown={(event) => {
                  if (event.key === "Escape") return setDraft(null);
                  if (event.key !== "Enter") return;
                  event.preventDefault();
                  addPriority();
                }}
                className="flex-1 bg-panel"
              />
              <AddRowButton onClick={addPriority} className="flex-none">
                Add
              </AddRowButton>
            </div>
          )}
        </div>
      </div>

      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Confidentiality level
        </span>
        <div role="radiogroup" aria-label="Confidentiality level" className="grid gap-3 sm:grid-cols-2">
          <ConfidentialityCard
            title="Standard"
            body="Visible to the whole workspace"
            selected={!context.confidential}
            onSelect={() => onChange({ confidential: false }, true)}
          />
          <ConfidentialityCard
            title="Confidential"
            body="Restricted until shortlist stage"
            tone="red"
            selected={context.confidential}
            onSelect={() => onChange({ confidential: true }, true)}
          />
        </div>
      </div>

      <StepField label="Additional context — internal only">
        <TextArea
          value={context.internalContext ?? ""}
          rows={6}
          placeholder="Culture, stakeholder dynamics, onboarding timelines, anything sensitive the hiring manager shared…"
          onChange={(event) => onChange({ internalContext: event.target.value || null })}
          className="border-line-soft px-4 py-3.5 font-sans text-[13.5px] leading-[1.65] text-text2"
        />
      </StepField>
    </div>
  );
}

/**
 * One priority: click the name to light it, the ✕ to drop it from the palette altogether.
 *
 * Two buttons rather than one — a chip that both toggles and deletes cannot be a single control, and
 * a button inside a button is not markup a browser will draw. The border carries the chip.
 */
function PriorityChip({
  priority,
  onToggle,
  onRemove,
}: {
  priority: StrategicPriority;
  onToggle: () => void;
  onRemove: () => void;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-lg border py-1.5 pe-1.5 ps-3 text-xs font-semibold transition",
        priority.selected
          ? "border-sky bg-sky-dim text-sky"
          : "border-line bg-panel text-text3 hover:border-text3 hover:text-text2",
      )}
    >
      <button type="button" aria-pressed={priority.selected} onClick={onToggle}>
        {priority.name}
      </button>
      <RemoveRowButton label={`Remove ${priority.name}`} onClick={onRemove} />
    </span>
  );
}

/**
 * Reason for hire, drawn beside the step's own heading rather than inside the form.
 *
 * It answers the question the heading asks — why this mandate exists — in one word, and it is the
 * only field on the step short enough to sit on that line. Below it, every field gets the full width.
 */
export function MandateReasonField({
  value,
  onChange,
}: {
  value: MandateContext["mandateReason"];
  onChange: (mandateReason: MandateContext["mandateReason"]) => void;
}) {
  return (
    <StepField label="Reason for hire" className="w-full sm:w-[240px]">
      <Select
        value={value}
        onChange={(event) => onChange(event.target.value as MandateContext["mandateReason"])}
      >
        {Object.entries(MANDATE_REASON_LABELS).map(([reason, label]) => (
          <option key={reason} value={reason}>
            {label}
          </option>
        ))}
      </Select>
    </StepField>
  );
}

function ConfidentialityCard({
  title,
  body,
  tone = "sky",
  selected,
  onSelect,
}: {
  title: string;
  body: string;
  tone?: "sky" | "red";
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      className={cn(
        "rounded-[10px] border px-[18px] py-3.5 text-start transition",
        selected
          ? tone === "red"
            ? "border-red bg-red-dim"
            : "border-sky bg-sky-dim"
          : "border-line bg-panel2 hover:border-text3",
      )}
    >
      <span
        className={cn(
          "block text-[13px] font-semibold",
          selected && tone === "red" ? "text-red" : selected ? "text-sky" : "text-text",
        )}
      >
        {title}
      </span>
      <span className="mt-1 block font-mono text-[11.5px] text-text3">{body}</span>
    </button>
  );
}
