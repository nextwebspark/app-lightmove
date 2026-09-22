import { useState } from "react";
import { DateInput } from "../../../../components/ui";
import {
  NOTICE_PERIODS,
  noticePairLabel,
  noticePeriodOfPair,
  pairOfNoticePeriod,
} from "../../../../lib/noticePeriod";
import { SENIORITY_LABELS, SENIORITY_TIERS, type SeniorityTier } from "../../../../lib/seniority";
import type {
  EmploymentType,
  FieldSource,
  MandateContext,
  MandateReason,
  PositionDetails,
  PositionDocument,
  PositionTemplate,
  ReportingStructure,
} from "../../api/types";
import { fieldCountOf, type StepReceipt } from "../../lib/documentFill";
import {
  EMPLOYMENT_TYPE_LABELS,
  MANDATE_REASON_LABELS,
  OFFERED_EMPLOYMENT_TYPES,
} from "../../lib/labels";
import { ChipGroup, ChoiceCard, FieldBlock, TokenChip, withRecorded, type ChipOption } from "../BriefFields";
import { DocumentCard } from "../DocumentCard";
import { DocumentFillStrip } from "../DocumentFillStrip";
import { IdealProfileField } from "../IdealProfileField";
import { LocationFields } from "../LocationFields";
import { ProvenanceMarker } from "../ProvenanceMarker";
import { RoleTitleField } from "../RoleTitleField";
import { SuggestedTemplateBanner } from "../SuggestedTemplateBanner";

const EMPLOYMENT_OPTIONS: ChipOption<EmploymentType>[] = OFFERED_EMPLOYMENT_TYPES.map((value) => ({
  value,
  label: EMPLOYMENT_TYPE_LABELS[value],
}));

const SENIORITY_OPTIONS: ChipOption<SeniorityTier>[] = SENIORITY_TIERS.map((value) => ({
  value,
  label: SENIORITY_LABELS[value],
}));

const REASON_OPTIONS: ChipOption<MandateReason>[] = (
  Object.entries(MANDATE_REASON_LABELS) as [MandateReason, string][]
).map(([value, label]) => ({ value, label }));

/** The four the brief offers — "None" is the executive's claim, not a period a mandate plans for. */
const NOTICE_OPTIONS: ChipOption<string>[] = NOTICE_PERIODS.filter((period) => period.months > 0).map(
  (period) => ({ value: period.label, label: period.label }),
);

/**
 * Step one: what the role is and why it exists — the document, the title, where it sits, how it is
 * engaged, why the mandate opened, how visible it is, when it starts, and what it does.
 *
 * <p>Three of its fields are not the details record's: the reason and the confidentiality are the
 * mandate context's, the notice period is the reporting structure's, and the target date is the
 * project's own. Each writes through its own channel, so the screen is one page over four writes.
 */
export function RoleBriefStep({
  details,
  context,
  reporting,
  document,
  templates,
  applyingTemplate,
  uploading,
  extracting,
  savingTargetDate,
  receipt,
  stripError,
  suggestedTemplate,
  onChangeDetails,
  onChangeContext,
  onChangeReporting,
  onChangeTargetDate,
  onPickTemplate,
  onAttachDocument,
  onRemoveDocument,
  onDownloadDocument,
  onExtractDocument,
  onApplySuggestedTemplate,
  onDismissSuggestedTemplate,
  onUndoAll,
  onDismissStrip,
  onUndoDetail,
  onUndoContext,
  onUndoNotice,
  onUndoResponsibility,
}: {
  details: PositionDetails;
  context: MandateContext;
  reporting: ReportingStructure;
  document: PositionDocument | null;
  templates: PositionTemplate[];
  applyingTemplate: boolean;
  uploading: boolean;
  /** A "Read from document" is in flight — disables Extract with AI and lets the strip retry. */
  extracting: boolean;
  savingTargetDate: boolean;
  /** This session's Role Brief receipt — details, context and the reporting notice pair. */
  receipt?: StepReceipt;
  /** Set when details or context failed to read this session — shown on the strip instead of a count. */
  stripError?: string;
  suggestedTemplate?: PositionTemplate | null;
  onChangeDetails: (patch: Partial<PositionDetails>) => void;
  onChangeContext: (patch: Partial<MandateContext>, immediate?: boolean) => void;
  onChangeReporting: (patch: Partial<ReportingStructure>, immediate?: boolean) => void;
  onChangeTargetDate: (isoDate: string) => void;
  onPickTemplate: (template: PositionTemplate) => void;
  onAttachDocument: (file: File) => void;
  onRemoveDocument: () => void;
  onDownloadDocument: () => void;
  onExtractDocument: () => void;
  onApplySuggestedTemplate: () => void;
  onDismissSuggestedTemplate: () => void;
  onUndoAll: () => void;
  onDismissStrip: () => void;
  onUndoDetail: (fieldKey: string) => void;
  onUndoContext: (fieldKey: string) => void;
  onUndoNotice: () => void;
  onUndoResponsibility: (text: string) => void;
}) {
  const [responsibility, setResponsibility] = useState("");

  const addResponsibility = () => {
    const text = responsibility.trim();
    if (!text) return;
    onChangeDetails({ responsibilities: [...details.responsibilities, { text, source: "MANUAL" }] });
    setResponsibility("");
  };

  // A brief already stating a period nobody offers — ninety days, six weeks — keeps stating it until
  // somebody picks another: a group whose value matches no pill would otherwise read as blank.
  const noticePeriod = noticePeriodOfPair(reporting.noticeValue, reporting.noticeUnit);
  const noticeRecorded =
    !noticePeriod && reporting.noticeValue != null && reporting.noticeUnit != null
      ? noticePairLabel(reporting.noticeValue, reporting.noticeUnit)
      : null;
  const noticeValue = noticePeriod ?? noticeRecorded;
  const noticeOptions = withRecorded(NOTICE_OPTIONS, noticeValue, (label) => label);

  const changeNotice = (chosen: string | null) => {
    if (chosen === noticeRecorded) return;
    onChangeReporting(chosen === null ? { noticeValue: null, noticeUnit: null } : (pairOfNoticePeriod(chosen) ?? {}), true);
  };

  /** A scalar's marker: its glyph when its value came from a reading, backed by this screen's receipt
   *  for the document, the snippet and the Undo — degraded to the glyph alone only where the receipt
   *  is genuinely gone (the strip dismissed, or a tab that never held it). */
  const markerFor = (fieldSources: Record<string, FieldSource>, key: string, onUndo: () => void) => {
    const info = receipt?.scalars[key];
    return (
      <ProvenanceMarker
        source={fieldSources[key]}
        confidence={info?.confidence}
        snippet={info?.snippet}
        fileName={info && receipt?.fileName}
        onUndo={info ? onUndo : undefined}
      />
    );
  };
  const noticeInfo = receipt?.scalars.noticePeriod;

  return (
    <div className="flex flex-col gap-8">
      <DocumentFillStrip
        fileName={receipt?.fileName ?? ""}
        count={fieldCountOf(receipt)}
        error={stripError}
        onRetry={onExtractDocument}
        retrying={extracting}
        onUndoAll={onUndoAll}
        onDismiss={onDismissStrip}
      />

      <DocumentCard
        document={document}
        uploading={uploading}
        extracting={extracting}
        onAttach={onAttachDocument}
        onRemove={onRemoveDocument}
        onDownload={onDownloadDocument}
        onExtract={onExtractDocument}
      />

      {suggestedTemplate && (
        <SuggestedTemplateBanner
          template={suggestedTemplate}
          applying={applyingTemplate}
          onApply={onApplySuggestedTemplate}
          onDismiss={onDismissSuggestedTemplate}
        />
      )}

      <FieldBlock label="Role title">
        <RoleTitleField
          value={details.roleTitle}
          templates={templates}
          busy={applyingTemplate}
          onChange={(roleTitle) => onChangeDetails({ roleTitle })}
          onPick={onPickTemplate}
        />
      </FieldBlock>

      <LocationFields
        city={details.locationCity}
        country={details.locationCountry}
        cityMarker={markerFor(details.fieldSources, "locationCity", () => onUndoDetail("locationCity"))}
        countryMarker={markerFor(details.fieldSources, "locationCountry", () => onUndoDetail("locationCountry"))}
        onChange={onChangeDetails}
      />

      <FieldBlock label="Employment type" aside={markerFor(details.fieldSources, "employmentType", () => onUndoDetail("employmentType"))}>
        <ChipGroup
          label="Employment type"
          options={withRecorded(EMPLOYMENT_OPTIONS, details.employmentType, (value) => EMPLOYMENT_TYPE_LABELS[value])}
          value={details.employmentType}
          allowClear
          onChange={(employmentType) => onChangeDetails({ employmentType })}
        />
      </FieldBlock>

      <FieldBlock label="Seniority" aside={markerFor(details.fieldSources, "seniority", () => onUndoDetail("seniority"))}>
        <ChipGroup
          label="Seniority"
          options={SENIORITY_OPTIONS}
          value={details.seniority}
          allowClear
          onChange={(seniority) => onChangeDetails({ seniority })}
        />
      </FieldBlock>

      <FieldBlock label="Reason for hire" aside={markerFor(context.fieldSources, "mandateReason", () => onUndoContext("mandateReason"))}>
        <ChipGroup
          label="Reason for hire"
          options={REASON_OPTIONS}
          value={context.mandateReason}
          onChange={(mandateReason) => mandateReason && onChangeContext({ mandateReason }, true)}
        />
      </FieldBlock>

      <FieldBlock label="Confidentiality level">
        <div role="radiogroup" aria-label="Confidentiality level" className="grid max-w-[520px] grid-cols-1 gap-3 sm:grid-cols-2">
          <ChoiceCard
            title="Standard"
            body="Visible to the whole workspace"
            selected={!context.confidential}
            onSelect={() => onChangeContext({ confidential: false }, true)}
          />
          <ChoiceCard
            title="Confidential"
            body="Restricted until shortlist"
            selected={context.confidential}
            onSelect={() => onChangeContext({ confidential: true }, true)}
          />
        </div>
      </FieldBlock>

      <FieldBlock
        label="Target start"
        aside={savingTargetDate ? <span className="text-meta text-u-text3">Saving…</span> : undefined}
      >
        {/* The mandate's one target date, written to the project itself: the brief reads it back. */}
        <DateInput
          value={reporting.targetStart ?? ""}
          onChange={onChangeTargetDate}
          className="max-w-[280px] rounded-none border-0 border-b border-u-border bg-transparent px-0 py-2 font-u-num text-lead focus-within:border-u-accent"
        />
      </FieldBlock>

      <FieldBlock
        label="Notice period to plan for"
        aside={
          <ProvenanceMarker
            source={reporting.fieldSources.noticeValue}
            confidence={noticeInfo?.confidence}
            snippet={noticeInfo?.snippet}
            fileName={noticeInfo && receipt?.fileName}
            onUndo={noticeInfo ? onUndoNotice : undefined}
          />
        }
      >
        <ChipGroup label="Notice period" options={noticeOptions} value={noticeValue} allowClear onChange={changeNotice} />
      </FieldBlock>

      <FieldBlock label="Key responsibilities">
        {details.responsibilities.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-2">
            {details.responsibilities.map((responsibility, index) => {
              const info = receipt?.lists.responsibilities?.appended[responsibility.text];
              return (
                <TokenChip
                  key={`${responsibility.text}-${index}`}
                  label={responsibility.text}
                  marker={
                    <ProvenanceMarker
                      source={responsibility.source}
                      confidence={info?.confidence}
                      snippet={info?.snippet}
                      fileName={info && receipt?.fileName}
                      onUndo={info ? () => onUndoResponsibility(responsibility.text) : undefined}
                    />
                  }
                  onRemove={() =>
                    onChangeDetails({ responsibilities: details.responsibilities.filter((_, i) => i !== index) })
                  }
                />
              );
            })}
          </div>
        )}
        <input
          value={responsibility}
          aria-label="Add a responsibility"
          placeholder="Add a responsibility…"
          onChange={(event) => setResponsibility(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            addResponsibility();
          }}
          className="w-full max-w-[280px] rounded-[8px] bg-u-raised px-4 py-2.5 text-body text-u-text outline-none transition placeholder:text-u-text3 focus:ring-1 focus:ring-u-accent-ring"
        />
      </FieldBlock>

      <IdealProfileField
        value={details.narrative}
        marker={markerFor(details.fieldSources, "narrative", () => onUndoDetail("narrative"))}
        onChange={(narrative) => onChangeDetails({ narrative })}
      />
    </div>
  );
}
