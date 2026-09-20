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
  MandateContext,
  MandateReason,
  PositionDetails,
  PositionDocument,
  PositionTemplate,
  ReportingStructure,
} from "../../api/types";
import {
  EMPLOYMENT_TYPE_LABELS,
  MANDATE_REASON_LABELS,
  OFFERED_EMPLOYMENT_TYPES,
} from "../../lib/labels";
import { ChipGroup, ChoiceCard, FieldBlock, TokenChip, withRecorded, type ChipOption } from "../BriefFields";
import { DocumentCard } from "../DocumentCard";
import { IdealProfileField } from "../IdealProfileField";
import { LocationFields } from "../LocationFields";
import { RoleTitleField } from "../RoleTitleField";

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
  savingTargetDate,
  onChangeDetails,
  onChangeContext,
  onChangeReporting,
  onChangeTargetDate,
  onPickTemplate,
  onAttachDocument,
  onRemoveDocument,
  onDownloadDocument,
}: {
  details: PositionDetails;
  context: MandateContext;
  reporting: ReportingStructure;
  document: PositionDocument | null;
  templates: PositionTemplate[];
  applyingTemplate: boolean;
  uploading: boolean;
  savingTargetDate: boolean;
  onChangeDetails: (patch: Partial<PositionDetails>) => void;
  onChangeContext: (patch: Partial<MandateContext>, immediate?: boolean) => void;
  onChangeReporting: (patch: Partial<ReportingStructure>, immediate?: boolean) => void;
  onChangeTargetDate: (isoDate: string) => void;
  onPickTemplate: (template: PositionTemplate) => void;
  onAttachDocument: (file: File) => void;
  onRemoveDocument: () => void;
  onDownloadDocument: () => void;
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

  return (
    <div className="flex flex-col gap-8">
      <DocumentCard
        document={document}
        uploading={uploading}
        onAttach={onAttachDocument}
        onRemove={onRemoveDocument}
        onDownload={onDownloadDocument}
      />

      <FieldBlock label="Role title">
        <RoleTitleField
          value={details.roleTitle}
          templates={templates}
          busy={applyingTemplate}
          onChange={(roleTitle) => onChangeDetails({ roleTitle })}
          onPick={onPickTemplate}
        />
      </FieldBlock>

      <LocationFields city={details.locationCity} country={details.locationCountry} onChange={onChangeDetails} />

      <FieldBlock label="Employment type">
        <ChipGroup
          label="Employment type"
          options={withRecorded(EMPLOYMENT_OPTIONS, details.employmentType, (value) => EMPLOYMENT_TYPE_LABELS[value])}
          value={details.employmentType}
          allowClear
          onChange={(employmentType) => onChangeDetails({ employmentType })}
        />
      </FieldBlock>

      <FieldBlock label="Seniority">
        <ChipGroup
          label="Seniority"
          options={SENIORITY_OPTIONS}
          value={details.seniority}
          allowClear
          onChange={(seniority) => onChangeDetails({ seniority })}
        />
      </FieldBlock>

      <FieldBlock label="Reason for hire">
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
        aside={savingTargetDate ? <span className="text-[11px] text-u-text3">Saving…</span> : undefined}
      >
        {/* The mandate's one target date, written to the project itself: the brief reads it back. */}
        <DateInput
          value={reporting.targetStart ?? ""}
          onChange={onChangeTargetDate}
          className="max-w-[280px] rounded-none border-0 border-b border-u-border bg-transparent px-0 py-2 font-u-num text-[15px] focus-within:border-u-accent"
        />
      </FieldBlock>

      <FieldBlock label="Notice period to plan for">
        <ChipGroup label="Notice period" options={noticeOptions} value={noticeValue} allowClear onChange={changeNotice} />
      </FieldBlock>

      <FieldBlock label="Key responsibilities">
        {details.responsibilities.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-2">
            {details.responsibilities.map((responsibility, index) => (
              <TokenChip
                key={`${responsibility.text}-${index}`}
                label={responsibility.text}
                onRemove={() =>
                  onChangeDetails({ responsibilities: details.responsibilities.filter((_, i) => i !== index) })
                }
              />
            ))}
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
          className="w-full max-w-[280px] rounded-[8px] bg-u-raised px-4 py-2.5 text-[13px] text-u-text outline-none transition placeholder:text-u-text3 focus:ring-1 focus:ring-u-accent-ring"
        />
      </FieldBlock>

      <IdealProfileField value={details.narrative} onChange={(narrative) => onChangeDetails({ narrative })} />
    </div>
  );
}
