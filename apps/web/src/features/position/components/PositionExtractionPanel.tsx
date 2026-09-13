import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input } from "../../../components/ui";
import { DetailPill } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import type { PositionExtraction, PositionTemplate, ProposedField } from "../api/types";
import {
  CONFIDENCE_STYLES,
  EXTRACTION_FIELD_LABELS,
  EXTRACTION_SOURCE_LABELS,
} from "../lib/extractionVocabulary";

/**
 * The review panel behind "Read from document": one row per proposed field, its confidence, its
 * source sentence behind a disclosure, and Accept/Dismiss. Nothing here writes anything — accepting
 * hands the caller the (possibly edited) value, and the caller is the one place a write happens, via
 * the same autosave channel every other edit on this step already goes through.
 *
 * Rows are matched back to the caller's state by object identity, never by array index — dismissing or
 * accepting one row must not shift which row a still-open disclosure belongs to.
 */
export function PositionExtractionPanel({
  extraction,
  onAccept,
  onDismiss,
  onAcceptAll,
  onApplySuggestedTemplate,
  applyingSuggestedTemplate,
}: {
  extraction: PositionExtraction;
  onAccept: (field: ProposedField, value: string) => void;
  onDismiss: (field: ProposedField) => void;
  onAcceptAll: (edits: Record<number, string>) => void;
  /** Present only on step one's response — every other section's `suggestedTemplate` is always null. */
  onApplySuggestedTemplate?: (template: PositionTemplate) => void;
  applyingSuggestedTemplate?: boolean;
}) {
  // Every row's edited value, lifted here rather than left in each row's own state: "Accept all"
  // reads this map, so it writes what the user typed rather than the original proposed value.
  const [edits, setEdits] = useState<Record<number, string>>({});
  const valueOf = (field: ProposedField) => edits[field.id] ?? field.value;

  const suggestion = extraction.suggestedTemplate && onApplySuggestedTemplate && (
    <SuggestedTemplateOffer
      template={extraction.suggestedTemplate}
      applying={Boolean(applyingSuggestedTemplate)}
      onApply={onApplySuggestedTemplate}
    />
  );

  if (extraction.fields.length === 0) {
    return (
      <div className="flex flex-col gap-2.5">
        {suggestion}
        <div className="rounded-[10px] border border-line-soft bg-panel2 px-[15px] py-[13px] font-mono text-[12px] text-text3">
          {extraction.extractionSource === "documentHeadings"
            ? "The assistant could not be reached, and nothing in the document's own headings could be proposed."
            : "Nothing was found to propose."}
        </div>
      </div>
    );
  }

  const degraded = extraction.extractionSource === "documentHeadings";

  return (
    <div className="flex flex-col gap-2.5">
      {suggestion}
      <div className="flex flex-col gap-3 rounded-[10px] border border-line-soft bg-panel2 p-[15px]">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div
            className={cn(
              "flex items-center gap-1.5 font-mono text-[11.5px]",
              degraded ? "text-amber" : "text-text3",
            )}
          >
            {degraded && <Icon d={ICONS.warning} size={13} className="shrink-0" />}
            {EXTRACTION_SOURCE_LABELS[extraction.extractionSource]}
          </div>
          <Button
            type="button"
            variant="secondary"
            onClick={() => onAcceptAll(edits)}
            className="px-2.5 py-1.5 text-[11.5px]"
          >
            Accept all
          </Button>
        </div>

        <div className="flex flex-col gap-2">
          {extraction.fields.map((field) => (
            <ProposalRow
              key={field.id}
              field={field}
              value={valueOf(field)}
              onValueChange={(value) => setEdits((current) => ({ ...current, [field.id]: value }))}
              onAccept={(value) => onAccept(field, value)}
              onDismiss={() => onDismiss(field)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * "Draft the brief from that template too?" — unchecked by default, and checked only while the
 * apply is in flight, so a failure reverts it rather than leaving it stuck in a checked-but-not-
 * applied state. A success clears the whole extraction (this offer included), so there is no
 * "applied" state to render here at all.
 */
function SuggestedTemplateOffer({
  template,
  applying,
  onApply,
}: {
  template: PositionTemplate;
  applying: boolean;
  onApply: (template: PositionTemplate) => void;
}) {
  return (
    <label className="flex items-center gap-2.5 rounded-lg border border-line bg-panel px-3 py-2.5 text-[12.5px] text-text2">
      <input
        type="checkbox"
        checked={applying}
        disabled={applying}
        onChange={() => onApply(template)}
        className="size-4 flex-none accent-sky"
      />
      This reads like a <span className="font-semibold text-text">{template.title}</span> mandate —
      draft the brief from that template too?
    </label>
  );
}

function ProposalRow({
  field,
  value,
  onValueChange,
  onAccept,
  onDismiss,
}: {
  field: ProposedField;
  value: string;
  onValueChange: (value: string) => void;
  onAccept: (value: string) => void;
  onDismiss: () => void;
}) {
  const [showSnippet, setShowSnippet] = useState(false);
  const style = CONFIDENCE_STYLES[field.confidence];
  const label = EXTRACTION_FIELD_LABELS[field.fieldKey] ?? field.fieldKey;

  return (
    <div className="rounded-lg border border-line bg-panel px-3 py-2.5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="w-[112px] flex-none font-mono text-[10.5px] font-semibold uppercase tracking-[0.05em] text-text3">
          {label}
        </span>
        <Input
          aria-label={label}
          value={value}
          onChange={(event) => onValueChange(event.target.value)}
          className="min-w-[160px] flex-1 bg-panel2"
        />
        {field.origin === "template" && (
          <DetailPill label="From template" className="text-text3 bg-line-soft" />
        )}
        <DetailPill label={style.label} className={style.className} />
        <div className="ms-auto flex flex-none gap-1.5">
          <Button
            type="button"
            variant="secondary"
            onClick={() => onAccept(value)}
            className="gap-1 px-2.5 py-1.5 text-[11.5px]"
          >
            <Icon d={ICONS.check} size={13} />
            Accept
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={onDismiss}
            className="px-2.5 py-1.5 text-[11.5px]"
          >
            Dismiss
          </Button>
        </div>
      </div>
      {field.snippet && (
        <button
          type="button"
          aria-expanded={showSnippet}
          onClick={() => setShowSnippet((open) => !open)}
          className="mt-1.5 flex items-center gap-1 font-mono text-[10.5px] text-text3 hover:text-text2"
        >
          <Icon d={showSnippet ? ICONS.chevronDown : ICONS.chevronRight} size={11} />
          Source
        </button>
      )}
      {showSnippet && field.snippet && (
        <p className="mt-1.5 rounded border border-line-soft bg-panel2 px-2.5 py-2 font-mono text-[11.5px] italic text-text3">
          "{field.snippet}"
        </p>
      )}
    </div>
  );
}
