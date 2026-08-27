import { Select, TextArea } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import type { MandateContext, StrategicPriority } from "../../api/types";
import {
  HIRING_URGENCY_LABELS,
  MANDATE_REASON_LABELS,
  STRATEGIC_PRIORITY_LABELS,
} from "../../lib/labels";
import { SegmentedControl, StepField, SubCard, ToggleChip } from "../fields";

/** Step two: why the mandate exists. Internal throughout — no candidate ever reads any of it. */
export function MandateContextStep({
  context,
  onChange,
}: {
  context: MandateContext;
  onChange: (patch: Partial<MandateContext>, immediate?: boolean) => void;
}) {
  const togglePriority = (priority: StrategicPriority) => {
    const selected = context.strategicPriorities.includes(priority)
      ? context.strategicPriorities.filter((each) => each !== priority)
      : [...context.strategicPriorities, priority];
    onChange({ strategicPriorities: selected }, true);
  };

  return (
    <div className="flex flex-col gap-5">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-x-[18px]">
        <StepField label="Reason for hire">
          <Select
            value={context.mandateReason}
            onChange={(event) =>
              onChange(
                { mandateReason: event.target.value as MandateContext["mandateReason"] },
                true,
              )
            }
          >
            {Object.entries(MANDATE_REASON_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </StepField>
        <StepField label="Business driver">
          <TextArea
            value={context.businessDriver ?? ""}
            rows={2}
            onChange={(event) => onChange({ businessDriver: event.target.value || null })}
          />
        </StepField>
      </div>

      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Strategic priority alignment
        </span>
        <SubCard>
          <div className="flex flex-wrap gap-2">
            {Object.entries(STRATEGIC_PRIORITY_LABELS).map(([value, label]) => (
              <ToggleChip
                key={value}
                label={label}
                selected={context.strategicPriorities.includes(value as StrategicPriority)}
                onToggle={() => togglePriority(value as StrategicPriority)}
              />
            ))}
          </div>
        </SubCard>
      </div>

      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Hiring urgency
        </span>
        <SegmentedControl
          label="Hiring urgency"
          accent="amber"
          value={context.hiringUrgency}
          onChange={(hiringUrgency) => onChange({ hiringUrgency }, true)}
          options={Object.entries(HIRING_URGENCY_LABELS).map(([value, label]) => ({
            value: value as MandateContext["hiringUrgency"],
            label,
          }))}
        />
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
