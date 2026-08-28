import { useState } from "react";
import { Input, Select, TextArea } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import type { MandateContext } from "../../api/types";
import { MANDATE_REASON_LABELS } from "../../lib/labels";
import { AddRowButton, RemoveRowButton, StepField, SubCard } from "../fields";

/** Step two: why the mandate exists. Internal throughout — no candidate ever reads any of it. */
export function MandateContextStep({
  context,
  onChange,
}: {
  context: MandateContext;
  onChange: (patch: Partial<MandateContext>, immediate?: boolean) => void;
}) {
  const [draft, setDraft] = useState("");

  const addPriority = () => {
    const priority = draft.trim();
    if (!priority) return;
    // Same priority twice says nothing twice, and the chips would be indistinguishable.
    const duplicate = context.strategicPriorities.some(
      (each) => each.toLowerCase() === priority.toLowerCase(),
    );
    if (!duplicate) {
      onChange({ strategicPriorities: [...context.strategicPriorities, priority] }, true);
    }
    setDraft("");
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
        <SubCard>
          {context.strategicPriorities.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {context.strategicPriorities.map((priority, index) => (
                <span
                  key={`${priority}-${index}`}
                  className="inline-flex items-center gap-[7px] rounded border border-line bg-panel py-1.5 pe-2 ps-2.5 text-xs font-semibold text-text2"
                >
                  {priority}
                  <RemoveRowButton
                    label={`Remove ${priority}`}
                    onClick={() =>
                      onChange(
                        {
                          strategicPriorities: context.strategicPriorities.filter(
                            (_, at) => at !== index,
                          ),
                        },
                        true,
                      )
                    }
                  />
                </span>
              ))}
            </div>
          )}
          <div className="mt-3.5 flex gap-2">
            <Input
              value={draft}
              aria-label="Add a strategic priority"
              placeholder="Add a strategic priority…"
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== "Enter") return;
                event.preventDefault();
                addPriority();
              }}
              className="flex-1 bg-panel"
            />
            <AddRowButton onClick={addPriority} className="flex-none">
              + Add priority
            </AddRowButton>
          </div>
        </SubCard>
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
