import { useState } from "react";
import { ChoiceCardGroup, DateInput } from "../../../../components/ui";
import {
  noticePairLabel,
  noticePeriodOfPair,
  pairOfNoticePeriod,
} from "../../../../lib/noticePeriod";
import type {
  FieldSource,
  MandateContext,
  PositionDetails,
  PositionDocument,
  PositionTemplate,
  ReportingStructure,
} from "../../api/types";
import type { StepReceipt } from "../../lib/documentFill";
import { EMPLOYMENT_TYPE_LABELS } from "../../lib/labels";
import { ChipGroup, FieldBlock, TokenChip, UnderlineField, withRecorded } from "../BriefFields";
import {
  CONFIDENTIALITY_OPTIONS,
  EMPLOYMENT_OPTIONS,
  NOTICE_OPTIONS,
  REASON_OPTIONS,
  SENIORITY_OPTIONS,
} from "../briefOptions";
import { DocumentCard } from "../DocumentCard";
import { IdealProfileField } from "../IdealProfileField";
import { LocationFields } from "../LocationFields";
import { ProvenanceMarker } from "../ProvenanceMarker";
import { RoleTitleField } from "../RoleTitleField";

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
  onChangeDetails,
  onChangeContext,
  onChangeReporting,
  onChangeTargetDate,
  onPickTemplate,
  onAttachDocument,
  onRemoveDocument,
  onDownloadDocument,
  onExtractDocument,
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
  onChangeDetails: (patch: Partial<PositionDetails>) => void;
  onChangeContext: (patch: Partial<MandateContext>, immediate?: boolean) => void;
  onChangeReporting: (patch: Partial<ReportingStructure>, immediate?: boolean) => void;
  onChangeTargetDate: (isoDate: string) => void;
  onPickTemplate: (template: PositionTemplate) => void;
  onAttachDocument: (file: File) => void;
  onRemoveDocument: () => void;
  onDownloadDocument: () => void;
  onExtractDocument: () => void;
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
      <DocumentCard
        document={document}
        uploading={uploading}
        extracting={extracting}
        onAttach={onAttachDocument}
        onRemove={onRemoveDocument}
        onDownload={onDownloadDocument}
        onExtract={onExtractDocument}
      />


      <div className="grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-2">
        <FieldBlock label="Role title">
          <RoleTitleField
            value={details.roleTitle}
            templates={templates}
            busy={applyingTemplate}
            onChange={(roleTitle) => onChangeDetails({ roleTitle })}
            onPick={onPickTemplate}
          />
        </FieldBlock>

        <FieldBlock label="Department" aside={markerFor(details.fieldSources, "department", () => onUndoDetail("department"))}>
          <UnderlineField
            aria-label="Department"
            value={details.department ?? ""}
            placeholder="e.g. Group Finance"
            onChange={(event) => onChangeDetails({ department: event.target.value || null })}
          />
        </FieldBlock>
      </div>

      <LocationFields
        city={details.locationCity}
        country={details.locationCountry}
        cityMarker={markerFor(details.fieldSources, "locationCity", () => onUndoDetail("locationCity"))}
        countryMarker={markerFor(details.fieldSources, "locationCountry", () => onUndoDetail("locationCountry"))}
        onChange={onChangeDetails}
      />

      <div className="grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-2">
        <FieldBlock label="Employment type" aside={markerFor(details.fieldSources, "employmentType", () => onUndoDetail("employmentType"))}>
          <ChipGroup
            size="sm"
            label="Employment type"
            options={withRecorded(EMPLOYMENT_OPTIONS, details.employmentType, (value) => EMPLOYMENT_TYPE_LABELS[value])}
            value={details.employmentType}
            allowClear
            onChange={(employmentType) => onChangeDetails({ employmentType })}
          />
        </FieldBlock>

        <FieldBlock label="Seniority" aside={markerFor(details.fieldSources, "seniority", () => onUndoDetail("seniority"))}>
          <ChipGroup
            size="sm"
            label="Seniority"
            options={SENIORITY_OPTIONS}
            value={details.seniority}
            allowClear
            onChange={(seniority) => onChangeDetails({ seniority })}
          />
        </FieldBlock>
      </div>

      <FieldBlock label="Reason for hire" aside={markerFor(context.fieldSources, "mandateReason", () => onUndoContext("mandateReason"))}>
        <ChipGroup
          size="sm"
          label="Reason for hire"
          options={REASON_OPTIONS}
          value={context.mandateReason}
          onChange={(mandateReason) => mandateReason && onChangeContext({ mandateReason }, true)}
        />
      </FieldBlock>

      <FieldBlock label="Confidentiality level">
        <ChoiceCardGroup
          label="Confidentiality level"
          options={CONFIDENTIALITY_OPTIONS}
          value={context.confidential ? "confidential" : "standard"}
          onChange={(level) => onChangeContext({ confidential: level === "confidential" }, true)}
          className="max-w-[520px]"
        />
      </FieldBlock>

      <div className="grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-2">
        <FieldBlock
          label="Target start"
          aside={savingTargetDate ? <span className="text-meta text-u-text3">Saving…</span> : undefined}
        >
          {/* The mandate's one target date, written to the project itself: the brief reads it back. */}
          <DateInput
            value={reporting.targetStart ?? ""}
            onChange={onChangeTargetDate}
            className="rounded-[8px] border border-u-border bg-u-sunken px-3 py-2.5 font-u-num text-body focus-within:border-u-accent"
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
          <ChipGroup size="sm" label="Notice period" options={noticeOptions} value={noticeValue} allowClear onChange={changeNotice} />
        </FieldBlock>
      </div>

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
        <UnderlineField
          value={responsibility}
          aria-label="Add a responsibility"
          placeholder="Add a responsibility…"
          onChange={(event) => setResponsibility(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            addResponsibility();
          }}
          className="max-w-[280px]"
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
