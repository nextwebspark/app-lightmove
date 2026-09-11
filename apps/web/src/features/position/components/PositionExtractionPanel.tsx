import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input } from "../../../components/ui";
import { DetailPill } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import type { PositionExtraction, ProposedField } from "../api/types";
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
}: {
  extraction: PositionExtraction;
  onAccept: (field: ProposedField, value: string) => void;
  onDismiss: (field: ProposedField) => void;
  onAcceptAll: () => void;
}) {
  if (extraction.fields.length === 0) {
    return (
      <div className="rounded-[10px] border border-line-soft bg-panel2 px-[15px] py-[13px] font-mono text-[12px] text-text3">
        {EXTRACTION_SOURCE_LABELS[extraction.extractionSource]} — nothing was found to propose.
      </div>
    );
  }

  const degraded = extraction.extractionSource === "documentHeadings";

  return (
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
          onClick={onAcceptAll}
          className="px-2.5 py-1.5 text-[11.5px]"
        >
          Accept all
        </Button>
      </div>

      <div className="flex flex-col gap-2">
        {extraction.fields.map((field, index) => (
          <ProposalRow
            key={`${field.fieldKey}-${index}`}
            field={field}
            onAccept={(value) => onAccept(field, value)}
            onDismiss={() => onDismiss(field)}
          />
        ))}
      </div>
    </div>
  );
}

function ProposalRow({
  field,
  onAccept,
  onDismiss,
}: {
  field: ProposedField;
  onAccept: (value: string) => void;
  onDismiss: () => void;
}) {
  const [value, setValue] = useState(field.value);
  const [showSnippet, setShowSnippet] = useState(false);
  const style = CONFIDENCE_STYLES[field.confidence];

  return (
    <div className="rounded-lg border border-line bg-panel px-3 py-2.5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="w-[112px] flex-none font-mono text-[10.5px] font-semibold uppercase tracking-[0.05em] text-text3">
          {EXTRACTION_FIELD_LABELS[field.fieldKey] ?? field.fieldKey}
        </span>
        <Input
          value={value}
          onChange={(event) => setValue(event.target.value)}
          className="min-w-[160px] flex-1 bg-panel2"
        />
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
