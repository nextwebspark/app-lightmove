import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState, type ReactNode } from "react";
import { Button, Select, useToast } from "../../../components/ui";
import { CollapsibleSection } from "../../../components/ui/CollapsibleSection";
import { DetailGrid, DetailPill, DetailTile } from "../../../components/ui/DetailList";
import { PanelCloseButton } from "../../../components/ui/PanelCloseButton";
import { TabList } from "../../../components/ui/TabList";
import { tabPanelProps } from "../../../components/ui/tabPanelProps";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate, formatNumber } from "../../../lib/format";
import { noticeSummaryOf } from "../../../lib/noticePeriod";
import { toBrowsableUrl } from "../../../lib/url";
import * as contactLookupApi from "../../contactlookup/api/contactLookupApi";
import { ContactPanel } from "../../contactlookup/components/ContactPanel";
import type { CustomColumn, CustomFieldValues } from "../../customcolumns/api/types";
import { CustomFieldsFieldset } from "../../customcolumns/components/CustomFieldsFieldset";
import { AddToSequenceButton } from "../../outreach/components/AddToSequenceButton";
import { OutreachSection } from "../../outreach/components/OutreachSection";
import { MeetingsSection } from "../../outreach/components/MeetingsSection";
import * as candidatesApi from "../api/candidatesApi";
import * as personCrmApi from "../api/personCrmApi";
import * as poolApi from "../api/poolApi";
import type { DocumentScope } from "../api/documentsApi";
import type {
  Candidate,
  CandidateStatus,
  PersonDocument,
  PersonDocumentVersion,
  SaveCandidatePayload,
} from "../api/types";
import { replayOf, type ProfileFormSection } from "../lib/candidateForm";
import {
  candidateGenderLabel,
  candidateStatusStyle,
  CANDIDATE_SOURCE_STYLES,
  CANDIDATE_STATUSES,
} from "../lib/candidateVocabulary";
import { careerSummary } from "../lib/careerTimeline";
import { packageOf } from "../lib/compensation";
import { useAiEnrichment } from "../lib/useAiEnrichment";
import { useChangeCandidateStatus } from "../lib/useChangeCandidateStatus";
import { usePersonDocuments } from "../lib/usePersonDocuments";
import { useProfileSections, type ProfileSection } from "../lib/useProfileSections";
import { CandidateAvatar } from "./CandidateAvatar";
import {
  AiInferredBadge,
  BackgroundFields,
  CareerFields,
  CompensationFields,
  IdentityFields,
  SummaryFields,
} from "./CandidateFieldGroups";
import { CareerTimeline } from "./CareerTimeline";
import { ClampedText, EducationList, FoldAllButton, HeaderProfileLink, PillRow } from "./ProfileParts";
import { CompensationSummary } from "./CompensationSummary";
import { DocumentPreviewSheet, type PreviewTarget } from "./documents/DocumentPreviewSheet";
import { PrimaryCvChip } from "./documents/PrimaryCvChip";
import {
  DoNotContactStrip,
  DocumentsSection,
  NotesSection,
  TimelineFeed,
  usePositions,
} from "./PersonSections";
import { PositionsSection } from "./PersonPositions";
import { PersonTagsSection } from "./PersonTagsSection";
import { ProfileSectionForm, SectionEditButton, SectionEditor } from "./ProfileSectionForm";
import {
  AiAssessmentBody,
  AiEnrichButton,
  aiAssessmentSummary,
  NationalitySuggestion,
} from "./AiAssessmentSection";

/** What a pencil opens: one of the form's sections, or the mandate's own columns. */
type EditableSection = Exclude<ProfileFormSection, "note"> | "columns";

type ProfileTab = "profile" | "contact" | "records" | "timeline";

const STAFF_TABS: readonly { value: ProfileTab; label: string }[] = [
  { value: "profile", label: "Profile" },
  { value: "contact", label: "Contact & outreach" },
  { value: "records", label: "Records" },
  { value: "timeline", label: "Timeline" },
];

const CLIENT_TABS: readonly { value: ProfileTab; label: string }[] = [
  { value: "profile", label: "Profile" },
  { value: "contact", label: "Contact" },
];

const PROFILE_TAB_FOLDS: readonly ProfileSection[] = [
  "summary",
  "ai",
  "experience",
  "education",
  "compensation",
  "background",
  "columns",
];

const TAB_ID_PREFIX = "executive-drawer";

/**
 * One executive, read first and corrected a section at a time.
 *
 * <p><b>Clicking a name opens a profile, not a form.</b> A consultant looking someone up is reading,
 * and a form that opens over the grid on every click is a form you dismiss without looking at. The
 * pencil on each section is the deliberate second step, and it edits that section in place: the rest
 * of the profile stays readable, a save puts the section back as it now reads, and the panel stays
 * open — the reader had not finished, and the next thing they do is usually write the note.
 *
 * <p><b>Every save is still a full replace on the wire.</b> The server takes the whole profile, so a
 * section's save is the stored profile with that section written over it, and nothing outside the
 * section can change. That is what lets six small forms be safer than one big one: a form that has
 * been open for a while can only overwrite the fields it was opened for.
 *
 * <p><b>Status stays live in the header</b>, with a write of its own — it is the field that changes
 * most often and the one a researcher came to flick while reading.
 *
 * <p>Tabs split reading the person from working them — Profile, Contact &amp; outreach, Records (tags,
 * notes, documents, other positions) and Timeline; a client seat gets Profile and Contact alone.
 * Every panel stays mounted while hidden, so a half-typed note survives a look at another tab.
 *
 * <p>Keyed by the caller on the candidate's id: the open tab and section are state about this person,
 * and moving to the next profile must start it fresh, on Profile.
 */
export function CandidateProfile({
  projectId,
  candidate,
  customColumns,
  canWrite,
  briefCurrency,
  onClose,
  onSaved,
  onRemove,
}: {
  projectId: string;
  candidate: Candidate;
  customColumns: readonly CustomColumn[];
  /** False for a client representative, who reads a mandate's people and changes nothing about them. */
  canWrite: boolean;
  /** The brief's currency, which the Compensation editor follows until somebody overrides it. */
  briefCurrency?: string | null;
  onClose: () => void;
  /** Every write's answer — the caller keeps showing what the server now holds. */
  onSaved: (saved: Candidate) => void;
  /** Absent for a reader who may not write. */
  onRemove?: (candidate: Candidate) => void;
}) {
  const sections = useProfileSections();
  /**
   * Whether this deployment looks contacts up. A deployment fact, not a row one, so it is read once
   * and kept — and a refused or failed read means no buttons rather than buttons that cannot work.
   */
  const lookupConfig = useQuery({
    queryKey: contactLookupApi.CONTACT_LOOKUP_CONFIG_KEY,
    queryFn: ({ signal }) => contactLookupApi.getContactLookupConfig(signal),
    staleTime: Infinity,
  });
  const body = useRef<HTMLDivElement>(null);
  const [editing, setEditing] = useState<EditableSection | null>(null);
  const [tab, setTab] = useState<ProfileTab>("profile");
  const queryClient = useQueryClient();
  const positions = usePositions(projectId, candidate.id, canWrite);
  const otherPositions = (positions.data ?? []).filter((position) => position.projectId !== projectId);
  const personRecord = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(candidate.personId),
    queryFn: ({ signal }) => poolApi.getPerson(candidate.personId, signal),
    enabled: canWrite,
  });

  const replace = (patch: Partial<SaveCandidatePayload>) =>
    candidatesApi.updateCandidate(projectId, candidate.id, { ...replayOf(candidate), ...patch });

  const finish = (saved: Candidate) => {
    setEditing(null);
    onSaved(saved);
  };

  const changeStatus = useChangeCandidateStatus(projectId, onSaved);
  const documentScope: DocumentScope = { kind: "position", projectId, candidateId: candidate.id };
  const documents = usePersonDocuments(documentScope, canWrite);
  const [preview, setPreview] = useState<PreviewTarget | null>(null);
  const openPreview = (document: PersonDocument, version: PersonDocumentVersion) =>
    setPreview({ documentId: document.id, versionId: version.id });
  const closePreview = useCallback(() => setPreview(null), []);
  const aiEnrichment = useAiEnrichment(projectId, candidate.id, canWrite);
  const toast = useToast();
  // Accepting is the researcher recording the value, so it lands unflagged and confirms nothing else.
  const acceptNationality = useMutation({
    mutationFn: (group: string) => replace({ nationality: group }),
    onSuccess: (saved) => {
      toast("Nationality saved");
      onSaved(saved);
    },
    onError: (error) => toast(messageFor(error)),
  });
  const nationalityReading = canWrite && !candidate.nationality ? aiEnrichment.nationalityReading : null;

  const startEditing = (section: EditableSection) => {
    setEditing(section);
    if (section !== "identity") return;
    setTab("profile");
    if (body.current) body.current.scrollTop = 0;
  };

  const pencil = (section: EditableSection, label: string) =>
    canWrite && editing !== section ? (
      <SectionEditButton
        label={label}
        disabled={editing !== null}
        onClick={() => startEditing(section)}
      />
    ) : undefined;

  const foldProps = (id: ProfileSection & EditableSection) => ({
    id,
    open: editing === id || sections.isOpen(id),
    onToggle: () => sections.toggle(id),
  });

  const { compensation } = candidate;
  const { total } = packageOf(compensation);
  const currency = compensation.currency ?? "";
  const capturedFrom = toBrowsableUrl(candidate.sourceUrl);
  const visibleColumns = customColumns.filter((column) => !column.hidden);
  const employerLabel = candidate.companyName ?? undefined;
  const panelProps = (value: ProfileTab) => ({ ...tabPanelProps(TAB_ID_PREFIX, value), hidden: tab !== value });

  return (
    <>
      <div className="relative flex-none border-b border-u-border px-5 py-4">
        <PanelCloseButton onClose={onClose} />
        <div className="group flex items-start gap-3 pe-8">
          <CandidateAvatar
            projectId={projectId}
            candidate={candidate}
            className="size-[44px] rounded-[10px] border border-u-border-strong text-sm"
          />
          <div className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5">
              <h2 className="font-sans text-base font-semibold">{candidate.fullName}</h2>
              <HeaderProfileLink linkedinUrl={candidate.linkedinUrl} />
              {pencil("identity", "details")}
            </span>
            <p className="mt-0.5 font-mono text-[12.5px] text-u-text2">{candidate.title ?? "—"}</p>
            <p className="mt-1 font-mono text-[11.5px] text-u-text3">
              {[employerLabel, candidate.locationCity, candidate.locationCountry]
                .filter(Boolean)
                .join(" · ") || "No employer or location recorded"}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {candidate.seniority && (
                <span className="inline-flex items-center gap-1">
                  <DetailPill label={candidate.seniority} />
                  {candidate.aiInferredFields.includes("seniority") && <AiInferredBadge />}
                </span>
              )}
              <DetailPill
                label={CANDIDATE_SOURCE_STYLES[candidate.source].label}
                className={CANDIDATE_SOURCE_STYLES[candidate.source].className}
              />
              {canWrite ? (
                <Select
                  value={candidate.status}
                  aria-label="Status"
                  disabled={changeStatus.isPending}
                  onChange={(event) =>
                    changeStatus.mutate({
                      candidateId: candidate.id,
                      status: event.target.value as CandidateStatus,
                    })
                  }
                  className="w-auto px-2 py-1 text-[12px]"
                >
                  {CANDIDATE_STATUSES.map((status) => (
                    <option key={status.value} value={status.value}>
                      {status.label}
                    </option>
                  ))}
                </Select>
              ) : (
                <DetailPill
                  label={candidateStatusStyle(candidate.status).label}
                  className={candidateStatusStyle(candidate.status).className}
                />
              )}
              {canWrite && <PrimaryCvChip documents={documents} onPreview={openPreview} />}
              {canWrite && <AiEnrichButton enrichment={aiEnrichment} />}
            </div>
          </div>
        </div>
        {canWrite && <DoNotContactStrip personId={candidate.personId} />}
        <TabList
          label="Executive sections"
          idPrefix={TAB_ID_PREFIX}
          className="-mb-4 mt-4 flex-wrap gap-y-2"
          value={tab}
          onChange={setTab}
          tabs={canWrite ? STAFF_TABS : CLIENT_TABS}
        />
      </div>

      <div ref={body} className="min-h-0 flex-1 overflow-y-auto px-5">
        <div {...panelProps("profile")}>
          {editing === "identity" ? (
            <section className="border-b border-u-border py-4">
              <SectionHeading>Details</SectionHeading>
              <SectionEditor
                section="identity"
                candidate={candidate}
                save={replace}
                doneMessage="Details saved"
                onDone={finish}
                onCancel={() => setEditing(null)}
              >
                {(form) => (
                  <IdentityFields
                    register={form.register}
                    errors={form.formState.errors}
                    control={form.control}
                    employerLocked={candidate.triageCompanyId !== null}
                    aiInferred={new Set(candidate.aiInferredFields)}
                  />
                )}
              </SectionEditor>
            </section>
          ) : (
            <div className="-mb-1 flex justify-end gap-3 pt-2.5">
              <FoldAllButton label="Expand all" onClick={() => sections.setAll(true, PROFILE_TAB_FOLDS)} />
              <FoldAllButton label="Collapse all" onClick={() => sections.setAll(false, PROFILE_TAB_FOLDS)} />
            </div>
          )}

          <CollapsibleSection
            {...foldProps("summary")}
            title="Summary"
            summary={firstLine(candidate.summary)}
            action={pencil("summary", "summary")}
          >
            {editing === "summary" ? (
              <SectionEditor
                section="summary"
                candidate={candidate}
                save={replace}
                doneMessage="Summary saved"
                onDone={finish}
                onCancel={() => setEditing(null)}
              >
                {(form) => (
                  <SummaryFields register={form.register} errors={form.formState.errors} />
                )}
              </SectionEditor>
            ) : (
              <ClampedText
                text={candidate.summary ?? "No summary written yet."}
                className="text-[13px]/[1.6] text-u-text2"
              />
            )}
          </CollapsibleSection>

          <CollapsibleSection
            {...foldProps("experience")}
            title="Experience"
            count={candidate.career.length > 0 ? candidate.career.length : undefined}
            summary={careerSummary(candidate.career)}
            action={pencil("experience", "experience")}
          >
            {editing === "experience" ? (
              <SectionEditor
                section="experience"
                candidate={candidate}
                save={replace}
                doneMessage="Experience saved"
                onDone={finish}
                onCancel={() => setEditing(null)}
              >
                {(form) => (
                  <CareerFields
                    control={form.control}
                    register={form.register}
                    errors={form.formState.errors}
                  />
                )}
              </SectionEditor>
            ) : (
              <CareerTimeline career={candidate.career} />
            )}
          </CollapsibleSection>

          {candidate.education.length > 0 && (
            <CollapsibleSection
              id="education"
              open={sections.isOpen("education")}
              onToggle={() => sections.toggle("education")}
              title="Education"
              count={candidate.education.length}
              summary={candidate.education[0].school ?? candidate.education[0].degree}
            >
              <EducationList education={candidate.education} />
            </CollapsibleSection>
          )}

          <CollapsibleSection
            {...foldProps("compensation")}
            title="Compensation"
            summary={joinFacts([
              total > 0 ? `${currency} ${formatNumber(total)}`.trim() : null,
              noticeSummaryOf(compensation.noticePeriod),
            ])}
            action={pencil("compensation", "compensation")}
          >
            {editing === "compensation" ? (
              <SectionEditor
                section="compensation"
                candidate={candidate}
                save={replace}
                doneMessage="Compensation updated"
                onDone={finish}
                onCancel={() => setEditing(null)}
                footer="plain"
              >
                {(form) => (
                  <CompensationFields
                    register={form.register}
                    errors={form.formState.errors}
                    control={form.control}
                    watch={form.watch}
                    setValue={form.setValue}
                    briefCurrency={briefCurrency}
                    storedCurrency={compensation.currency}
                    storedNoticePeriod={compensation.noticePeriod}
                  />
                )}
              </SectionEditor>
            ) : (
              <CompensationSummary compensation={compensation} />
            )}
          </CollapsibleSection>

          <CollapsibleSection
            {...foldProps("background")}
            title="Background"
            summary={joinFacts([
              candidate.nationality ??
                (nationalityReading && nationalityReading.category !== "Unknown"
                  ? `AI suggests ${nationalityReading.category}`
                  : null),
              candidate.yearsExperience ? `${candidate.yearsExperience} yrs` : null,
              candidate.languages.length > 0 ? countOf(candidate.languages.length, "language") : null,
            ])}
            action={pencil("background", "background")}
          >
            {editing === "background" ? (
              <SectionEditor
                section="background"
                candidate={candidate}
                save={replace}
                doneMessage="Background saved"
                onDone={finish}
                onCancel={() => setEditing(null)}
              >
                {(form) => (
                  <BackgroundFields
                    register={form.register}
                    errors={form.formState.errors}
                    storedNationality={candidate.nationality}
                    aiInferred={new Set(candidate.aiInferredFields)}
                  />
                )}
              </SectionEditor>
            ) : (
              <>
                <DetailGrid>
                  <DetailTile
                    label="Nationality"
                    value={candidate.nationality}
                    badge={candidate.aiInferredFields.includes("nationality") ? <AiInferredBadge /> : undefined}
                  />
                  <DetailTile
                    label="Gender"
                    value={candidateGenderLabel(candidate.gender)}
                    badge={candidate.aiInferredFields.includes("gender") ? <AiInferredBadge /> : undefined}
                  />
                  <DetailTile
                    label="Experience"
                    value={candidate.yearsExperience ? `${candidate.yearsExperience} years` : null}
                    badge={
                      candidate.aiInferredFields.includes("yearsExperience") ? <AiInferredBadge /> : undefined
                    }
                  />
                </DetailGrid>
                {nationalityReading && (
                  <NationalitySuggestion
                    reading={nationalityReading}
                    onAccept={(group) => acceptNationality.mutate(group)}
                    isAccepting={acceptNationality.isPending}
                  />
                )}
                <PillRow label="Languages" values={candidate.languages} empty="No languages recorded." />
                {candidate.skills.length > 0 && <PillRow label="Skills" values={candidate.skills} />}
              </>
            )}
          </CollapsibleSection>

          {canWrite && (
            <CollapsibleSection
              id="ai"
              open={sections.isOpen("ai")}
              onToggle={() => sections.toggle("ai")}
              title="AI assessment"
              summary={aiAssessmentSummary(aiEnrichment)}
            >
              <AiAssessmentBody enrichment={aiEnrichment} />
            </CollapsibleSection>
          )}

          {visibleColumns.length > 0 && (
            <CollapsibleSection
              {...foldProps("columns")}
              title="Your columns"
              count={visibleColumns.length}
              summary={joinFacts(
                visibleColumns.map((column) => customValueOf(column, candidate.customFields)),
              )}
              action={pencil("columns", "your columns")}
            >
              {editing === "columns" ? (
                <ColumnsEditor
                  columns={visibleColumns}
                  candidate={candidate}
                  save={replace}
                  onDone={finish}
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <DetailGrid>
                  {visibleColumns.map((column) => (
                    <DetailTile
                      key={column.id}
                      label={column.label}
                      value={customValueOf(column, candidate.customFields)}
                    />
                  ))}
                </DetailGrid>
              )}
            </CollapsibleSection>
          )}

          <p className="py-4 font-mono text-[11px] text-u-text3">
            Added {formatInstantDate(candidate.addedAt)}
            {candidate.enrichedAt && ` · Researched ${formatInstantDate(candidate.enrichedAt)}`}
            {capturedFrom && (
              <>
                {" · "}
                <a
                  href={capturedFrom}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="hover:text-u-text hover:underline"
                >
                  Captured from {new URL(capturedFrom).hostname}
                </a>
              </>
            )}
          </p>
        </div>

        <div {...panelProps("contact")}>
          {canWrite && (
            <div className="flex justify-end pt-3 empty:hidden">
              <AddToSequenceButton projectId={projectId} candidateId={candidate.id} fullName={candidate.fullName} />
            </div>
          )}
          <CollapsibleSection
            {...foldProps("contact")}
            title="Contact"
            summary={joinFacts([
              candidate.contacts.emails[0]?.address,
              candidate.contacts.phones[0]?.number,
              candidate.contacts.emails.some((entry) => entry.verified) ? "verified" : null,
            ])}
            action={pencil("contact", "contact")}
          >
            <ContactPanel
              projectId={projectId}
              candidate={candidate}
              canWrite={canWrite}
              lookupOffered={lookupConfig.data?.enabled === true}
              onSaved={onSaved}
              editing={editing === "contact"}
              onDone={finish}
              onCancel={() => setEditing(null)}
            />
          </CollapsibleSection>
          {canWrite && (
            <OutreachSection
              projectId={projectId}
              candidateId={candidate.id}
              firstName={candidate.fullName.trim().split(/\s+/)[0]}
              candidateStatus={candidate.status}
              open={sections.isOpen("outreach")}
              onToggle={() => sections.toggle("outreach")}
              isSettingStatus={changeStatus.isPending}
              onSetStatus={(status) => changeStatus.mutate({ candidateId: candidate.id, status })}
            />
          )}
          {canWrite && (
            <MeetingsSection
              projectId={projectId}
              candidateId={candidate.id}
              personId={candidate.personId}
              fullName={candidate.fullName}
              candidateStatus={candidate.status}
              emails={candidate.contacts.emails}
            />
          )}
        </div>

        {canWrite && (
          <>
            <div {...panelProps("records")} className="pt-4">
              {personRecord.data && <PersonTagsSection person={personRecord.data} />}
              <NotesSection projectId={projectId} candidateId={candidate.id} />
              <DocumentsSection
                scope={documentScope}
                documents={documents}
                personName={candidate.fullName.split(" ")[0]}
                onPreview={openPreview}
              />
              {otherPositions.length > 0 && (
                <PositionsSection
                  title="Other positions"
                  positions={otherPositions}
                  onStatusChanged={() =>
                    void queryClient.invalidateQueries({
                      queryKey: personCrmApi.PERSON_POSITIONS_KEY(projectId, candidate.id),
                    })
                  }
                />
              )}
            </div>
            <div {...panelProps("timeline")} className="py-4">
              <TimelineFeed projectId={projectId} candidateId={candidate.id} />
            </div>
          </>
        )}
      </div>

      <footer className="flex flex-none items-center gap-2 border-t border-u-border px-5 py-3">
        {onRemove && (
          <Button type="button" variant="secondary" className="text-u-offlimits" onClick={() => onRemove(candidate)}>
            Remove from mandate
          </Button>
        )}
        <Button type="button" variant="secondary" className="ms-auto" onClick={onClose}>
          Close
        </Button>
      </footer>

      {canWrite && (
        <DocumentPreviewSheet
          scope={documentScope}
          documents={documents}
          target={preview}
          onTargetChange={setPreview}
          onClose={closePreview}
        />
      )}
    </>
  );
}

/** The mandate's own columns, edited as a section: the values ride on the same replace, sent alone. */
function ColumnsEditor({
  columns,
  candidate,
  save,
  onDone,
  onCancel,
}: {
  columns: readonly CustomColumn[];
  candidate: Candidate;
  save: (patch: Partial<SaveCandidatePayload>) => Promise<Candidate>;
  onDone: (saved: Candidate) => void;
  onCancel: () => void;
}) {
  const toast = useToast();
  const [values, setValues] = useState<CustomFieldValues>(candidate.customFields ?? {});
  const [error, setError] = useState<string | null>(null);

  const saving = useMutation({
    mutationFn: () => save({ customFields: values }),
    onSuccess: (saved) => {
      toast("Columns saved");
      onDone(saved);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  return (
    <ProfileSectionForm
      onSubmit={(event) => {
        event.preventDefault();
        setError(null);
        saving.mutate();
      }}
      onCancel={onCancel}
      saving={saving.isPending}
      error={error}
    >
      <CustomFieldsFieldset columns={columns} values={values} onChange={setValues} heading={false} />
    </ProfileSectionForm>
  );
}

function SectionHeading({ children }: { children: ReactNode }) {
  return (
    <h3 className="mb-3 font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-u-text3">
      {children}
    </h3>
  );
}

/** A stored column value as the tile shows it: a boolean column's "true" reads as Yes. */
function customValueOf(column: CustomColumn, values: CustomFieldValues): string | null {
  const value = values[column.fieldKey];
  if (!value) return null;
  if (column.dataType === "boolean") return value === "true" ? "Yes" : value === "false" ? "No" : value;
  return value;
}

/** The first line of a paragraph, cut to fit a folded header. */
function firstLine(text: string | null, max = 100): string | null {
  if (!text) return null;
  const line = text.split("\n", 1)[0].trim();
  if (!line) return null;
  return line.length > max ? `${line.slice(0, max - 1).trimEnd()}…` : line;
}

function joinFacts(facts: readonly (string | null | undefined)[]): string | null {
  const present = facts.filter((fact): fact is string => Boolean(fact));
  return present.length > 0 ? present.join(" · ") : null;
}

function countOf(count: number, noun: string): string {
  return `${count} ${count === 1 ? noun : `${noun}s`}`;
}
