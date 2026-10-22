import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { CollapsibleSection } from "../../../components/ui/CollapsibleSection";
import { DetailGrid, DetailPill, DetailTile } from "../../../components/ui/DetailList";
import { PanelCloseButton } from "../../../components/ui/PanelCloseButton";
import { tabPanelProps } from "../../../components/ui/tabPanelProps";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate, formatNumber } from "../../../lib/format";
import { noticeSummaryOf } from "../../../lib/noticePeriod";
import { toBrowsableUrl } from "../../../lib/url";
import * as contactLookupApi from "../../contactlookup/api/contactLookupApi";
import { ContactPanel } from "../../contactlookup/components/ContactPanel";
import type { CustomColumn, CustomFieldValues } from "../../customcolumns/api/types";
import { CustomFieldsFieldset } from "../../customcolumns/components/CustomFieldsFieldset";
import { CandidateOutreachButtons } from "../../outreach/components/CandidateOutreachButtons";
import { OutreachSection } from "../../outreach/components/OutreachSection";
import { MeetingsSection } from "../../outreach/components/MeetingsSection";
import { MeetingNoteRecording } from "../../outreach/components/NotetakerControls";
import { NextActionModal, type NextActionTarget } from "../../tasks/components/NextActionModal";
import { NextActionBar } from "../../tasks/components/NextActionBar";
import type { TaskNote } from "../../tasks/api/types";
import * as candidatesApi from "../api/candidatesApi";
import * as personCrmApi from "../api/personCrmApi";
import * as poolApi from "../api/poolApi";
import type { DocumentScope } from "../api/documentsApi";
import type {
  Candidate,
  PersonDocument,
  PersonDocumentVersion,
  SaveCandidatePayload,
} from "../api/types";
import { replayOf, type ProfileFormSection } from "../lib/candidateForm";
import { candidateGenderLabel, CANDIDATE_SOURCE_STYLES } from "../lib/candidateVocabulary";
import { statusStyle } from "../lib/pipelineStatus";
import { careerSummary } from "../lib/careerTimeline";
import { packageOf } from "../lib/compensation";
import { useBackgroundSuggestion } from "../lib/useBackgroundSuggestion";
import { useScorecardEditor } from "../lib/useScorecardEditor";
import { useChangeCandidateStatus } from "../lib/useChangeCandidateStatus";
import { usePersonDocuments } from "../lib/usePersonDocuments";
import { useProfileSections, type ProfileSection } from "../lib/useProfileSections";
import {
  POSITION_CLIENT_TABS,
  POSITION_STAFF_TABS,
  rememberDrawerTab,
  rememberedDrawerTab,
  type DrawerSection,
  type DrawerTab,
} from "../lib/drawerTabs";
import { CandidateAvatar } from "./CandidateAvatar";
import { DrawerTabBar } from "./DrawerTabBar";
import { AssessmentTab } from "./assessment/AssessmentTab";
import { DocumentsPanel } from "./documents/DocumentsPanel";
import { StatusPicker } from "./StatusPicker";
import {
  BackgroundFields,
  CareerFields,
  CompensationFields,
  IdentityFields,
  SummaryFields,
} from "./CandidateFieldGroups";
import { CareerTimeline } from "./CareerTimeline";
import { ClampedText, EducationList, FoldAllButton, PersonHeaderLayout, PillRow } from "./ProfileParts";
import { CompensationSummary } from "./CompensationSummary";
import { DocumentPreviewSheet, type PreviewTarget } from "./documents/DocumentPreviewSheet";
import { PrimaryCvChip } from "./documents/PrimaryCvChip";
import {
  DoNotContactStrip,
  NotesSection,
  TimelineFeed,
  usePositions,
  type NoteFocus,
} from "./PersonSections";
import { PositionsSection } from "./PersonPositions";
import { PersonTagsSection } from "./PersonTagsSection";
import { ProfileSectionForm, SectionEditButton, SectionEditor } from "./ProfileSectionForm";
import { AiActionButton } from "./AiActionButton";
import { AiInferredBadge } from "./AiInferredBadge";
import { NationalitySuggestion } from "./NationalitySuggestion";
import { focusNoteComposer } from "../../notes/lib/focusNoteComposer";

/** What a pencil opens: one of the form's sections, or the mandate's own columns. */
type EditableSection = Exclude<ProfileFormSection, "note"> | "columns";

const PROFILE_TAB_FOLDS: readonly ProfileSection[] = [
  "summary",
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
 * <p>Tabs follow `Position.dc.html` — Career, Notes, Assessment, Contact, Documents, Activity and Positions; a
 * client seat gets Career and Contact alone. Every panel stays mounted while hidden, so a half-typed note
 * survives a look at another tab. The next action is not a tab's: it is the header's bar, on view
 * whichever tab is open, since the moment to set one is while writing the note or the email.
 *
 * <p>Keyed by the caller on the candidate's id: the open section is state about this person, while
 * the tab is the viewer's — the next profile opens on the tab this one was left on.
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
  focusNoteId = null,
  focusSection = null,
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
  /** A note to open on — a bell row's: the Records tab, scrolled to it. */
  focusNoteId?: string | null;
  /** A part of the Contact tab to open on — a bell row's reply, bounce or booked call: unfolded, scrolled to. */
  focusSection?: DrawerSection | null;
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
  const offeredTabs = canWrite ? POSITION_STAFF_TABS : POSITION_CLIENT_TABS;
  const [tab, setTab] = useState<DrawerTab>(() =>
    canWrite && focusNoteId
      ? "notes"
      : canWrite && focusSection
        ? "contact"
        : rememberedDrawerTab("position", offeredTabs),
  );
  const [noteFocus, setNoteFocus] = useState<NoteFocus | null>(() =>
    canWrite && focusNoteId ? { noteId: focusNoteId, at: Date.now() } : null,
  );
  // A link to a note while this drawer is already open changes only the prop: bring that note forward too.
  const [linkedNoteId, setLinkedNoteId] = useState(focusNoteId);
  if (focusNoteId !== linkedNoteId) {
    setLinkedNoteId(focusNoteId);
    if (canWrite && focusNoteId) {
      setTab("notes");
      setNoteFocus({ noteId: focusNoteId, at: (noteFocus?.at ?? 0) + 1 });
    }
  }
  const [sectionFocus, setSectionFocus] = useState<SectionFocus | null>(() =>
    canWrite && focusSection ? { section: focusSection, at: Date.now() } : null,
  );
  const [linkedSection, setLinkedSection] = useState(focusSection);
  if (focusSection !== linkedSection) {
    setLinkedSection(focusSection);
    if (canWrite && focusSection) {
      setTab("contact");
      setSectionFocus({ section: focusSection, at: (sectionFocus?.at ?? 0) + 1 });
    }
  }
  const { setAll: unfoldSections } = sections;
  useEffect(() => {
    if (!sectionFocus) return;
    if (sectionFocus.section !== "meetings") unfoldSections(true, [sectionFocus.section]);
    return scrollToContactSection(sectionFocus.section);
  }, [sectionFocus, unfoldSections]);
  const showNote = (noteId: string) => {
    setTab("notes");
    setNoteFocus({ noteId, at: Date.now() });
  };
  /** Leaving the Notes tab lets the note go, so coming back to it scrolls nowhere. */
  const chooseTab = (next: DrawerTab) => {
    setTab(next);
    rememberDrawerTab("position", next);
    if (next !== "notes") setNoteFocus(null);
    if (next !== "contact") setSectionFocus(null);
    if (body.current) body.current.scrollTop = 0;
  };
  /** The footer's Add note: the Notes tab, its composer ready to type in. */
  const startNote = () => {
    chooseTab("notes");
    focusNoteComposer(`${TAB_ID_PREFIX}-panel-notes`);
  };
  const queryClient = useQueryClient();
  const positions = usePositions(projectId, candidate.id, canWrite);
  const otherPositions = (positions.data ?? []).filter((position) => position.projectId !== projectId);
  const personRecord = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(candidate.personId),
    queryFn: ({ signal }) => poolApi.getPerson(candidate.personId, signal),
    enabled: canWrite,
  });
  // Read once the tab is first opened, then kept: most profiles are read without being scored.
  const [assessmentOpened, setAssessmentOpened] = useState(tab === "assessment");
  if (tab === "assessment" && !assessmentOpened) setAssessmentOpened(true);
  const scorecard = useScorecardEditor(projectId, candidate.id, canWrite && assessmentOpened);
  const notes = useQuery({
    queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidate.id),
    queryFn: ({ signal }) => personCrmApi.getPersonNotes(projectId, candidate.id, signal),
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
  const [nextAction, setNextAction] = useState<NextActionTarget | null>(null);
  const [nextActionDone, setNextActionDone] = useState(false);
  const settleNextActionPrompt = useCallback(() => setNextActionDone(false), []);
  const openNextAction = (taskId: string | null, note: TaskNote | null = null) => {
    setNextActionDone(false);
    setNextAction({
      projectId,
      candidateId: candidate.id,
      personId: candidate.personId,
      executiveName: candidate.fullName,
      taskId,
      note,
    });
  };
  const openPreview = (document: PersonDocument, version: PersonDocumentVersion) =>
    setPreview({ documentId: document.id, versionId: version.id });
  const closePreview = useCallback(() => setPreview(null), []);
  const background = useBackgroundSuggestion(projectId, candidate, canWrite, onSaved);
  const toast = useToast();
  // Accepting is the researcher recording the value, so it lands unflagged and confirms nothing else.
  const acceptNationality = useMutation({
    mutationFn: (group: string) => replace({ nationality: group }),
    onSuccess: (saved) => {
      toast("Nationality saved");
      onSaved(saved);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const nationalityReading = canWrite && !candidate.nationality ? background.nationalityReading : null;

  const startEditing = (section: EditableSection) => {
    setEditing(section);
    if (section !== "identity") return;
    chooseTab("career");
    if (body.current) body.current.scrollTop = 0;
  };

  const pencil = (section: EditableSection, label: string, isBusy = false) =>
    canWrite && editing !== section ? (
      <SectionEditButton
        label={label}
        disabled={editing !== null || isBusy}
        onClick={() => startEditing(section)}
      />
    ) : undefined;

  // The Background form seeds its values once, so one opened while the AI writes would save over the answer.
  const backgroundPencil = pencil("background", "background", background.isSuggesting);
  const backgroundActions =
    background.isOffered && editing !== "background" ? (
      <span className="flex items-center gap-2">
        <AiActionButton
          label="Suggest with AI"
          isBusy={background.isSuggesting}
          disabled={editing !== null}
          onClick={background.suggest}
        />
        {backgroundPencil}
      </span>
    ) : (
      backgroundPencil
    );

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
  const firstName = candidate.fullName.trim().split(/\s+/)[0];
  const panelProps = (value: DrawerTab) => ({ ...tabPanelProps(TAB_ID_PREFIX, value), hidden: tab !== value });

  return (
    <>
      <div className="relative flex-none border-b border-u-border px-5 pt-4">
        <PanelCloseButton onClose={onClose} />
        <PersonHeaderLayout
          avatar={
            <CandidateAvatar
              projectId={projectId}
              candidate={candidate}
              className="size-[44px] rounded-[10px] border border-u-border-strong text-sm"
            />
          }
          fullName={candidate.fullName}
          linkedinUrl={candidate.linkedinUrl}
          nameAction={pencil("identity", "details")}
          title={candidate.title}
          context={[employerLabel, candidate.locationCity, candidate.locationCountry].filter(Boolean).join(" · ")}
        >
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
              <StatusPicker
                value={candidate.status}
                currentLabel={candidate.statusLabel}
                aria-label="Status"
                disabled={changeStatus.isPending}
                onChange={(status) =>
                  changeStatus.mutate({ candidateId: candidate.id, status, previous: candidate.status })
                }
                className="h-7"
              />
            ) : (
              <DetailPill
                label={statusStyle(candidate.status, candidate).label}
                className={statusStyle(candidate.status, candidate).className}
              />
            )}
            {canWrite && <PrimaryCvChip documents={documents} onPreview={openPreview} />}
          </div>
          {personRecord.data && <PersonTagsSection person={personRecord.data} className="mt-2.5" />}
          {canWrite && <DoNotContactStrip personId={candidate.personId} />}
          {canWrite && (
            <NextActionBar
              projectId={projectId}
              candidateId={candidate.id}
              firstName={firstName}
              justCompleted={nextActionDone}
              onCompleted={() => setNextActionDone(true)}
              onPromptSettled={settleNextActionPrompt}
              onOpen={openNextAction}
              onShowNote={showNote}
              className="mt-3"
            />
          )}
        </PersonHeaderLayout>
        <DrawerTabBar
          idPrefix={TAB_ID_PREFIX}
          tabs={offeredTabs}
          value={tab}
          onChange={chooseTab}
          counts={{
            notes: notes.data?.length,
            documents: documents.documents.data?.length,
            positions: otherPositions.length,
          }}
        />
      </div>

      <div ref={body} className="min-h-0 flex-1 overflow-y-auto px-5">
        <div {...panelProps("career")}>
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
            action={backgroundActions}
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
                    isLocked={background.isSuggesting}
                  />
                )}
                <PillRow label="Languages" values={candidate.languages} empty="No languages recorded." />
                {candidate.skills.length > 0 && <PillRow label="Skills" values={candidate.skills} />}
              </>
            )}
          </CollapsibleSection>

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
              <CandidateOutreachButtons projectId={projectId} candidateId={candidate.id} fullName={candidate.fullName} />
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
              firstName={firstName}
              candidateStatus={candidate.status}
              open={sections.isOpen("outreach")}
              onToggle={() => sections.toggle("outreach")}
              isSettingStatus={changeStatus.isPending}
              onSetStatus={(status) => changeStatus.mutate({ candidateId: candidate.id, status, previous: candidate.status })}
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
            <div {...panelProps("notes")}>
              <NotesSection
                projectId={projectId}
                candidateId={candidate.id}
                focus={noteFocus}
                onSetNextAction={(note) => openNextAction(null, note)}
                renderNoteFooter={(note) =>
                  note.kind === "meeting" && (
                    <MeetingNoteRecording projectId={projectId} candidateId={candidate.id} noteId={note.id} />
                  )
                }
              />
            </div>
            <div {...panelProps("assessment")}>
              {assessmentOpened && <AssessmentTab projectId={projectId} editor={scorecard} />}
            </div>
            <div {...panelProps("documents")} className="py-4">
              <DocumentsPanel
                scope={documentScope}
                documents={documents}
                personName={firstName}
                onPreview={openPreview}
              />
            </div>
            <div {...panelProps("activity")} className="py-4">
              <TimelineFeed projectId={projectId} candidateId={candidate.id} />
            </div>
            <div {...panelProps("positions")}>
              <PositionsSection
                title="Other positions"
                positions={otherPositions}
                empty={
                  <p className="text-note text-u-text3">
                    Not mapped to any other positions right now. Everything on file here follows {firstName} to the next one.
                  </p>
                }
                onStatusChanged={() =>
                  void queryClient.invalidateQueries({
                    queryKey: personCrmApi.PERSON_POSITIONS_KEY(projectId, candidate.id),
                  })
                }
              />
            </div>
          </>
        )}
      </div>

      <footer className="flex flex-none items-center gap-2 border-t border-u-border px-5 py-3">
        {onRemove && (
          <Button
            type="button"
            variant="secondary"
            aria-label="Remove from mandate"
            title="Remove from mandate"
            className="text-u-offlimits"
            onClick={() => onRemove(candidate)}
          >
            <Icon d={ICONS.trash} size={14} className="sm:hidden" />
            <span className="hidden sm:inline">Remove from mandate</span>
          </Button>
        )}
        {canWrite && tab === "assessment" ? (
          <>
            {scorecard.isDirty && (
              <Button type="button" variant="ghost" className="ms-auto" onClick={scorecard.discard}>
                Discard
              </Button>
            )}
            <Button
              type="button"
              variant="secondary"
              className={cn("whitespace-nowrap", !scorecard.isDirty && "ms-auto")}
              disabled={!scorecard.isDirty || scorecard.isSaving || scorecard.isAssessing || !scorecard.saved.data}
              loading={scorecard.isSaving && !scorecard.savingReviewed}
              onClick={() => scorecard.save(false)}
            >
              Save draft
            </Button>
            <Button
              type="button"
              className="whitespace-nowrap"
              disabled={scorecard.isSaving || scorecard.isAssessing || !scorecard.saved.data}
              loading={scorecard.savingReviewed}
              onClick={() => scorecard.save(true)}
            >
              Mark reviewed & submit
            </Button>
          </>
        ) : canWrite ? (
          <Button type="button" variant="secondary" className="ms-auto whitespace-nowrap" onClick={startNote}>
            <Icon d={ICONS.plus} size={14} />
            Add note
          </Button>
        ) : (
          <Button type="button" variant="secondary" className="ms-auto" onClick={onClose}>
            Close
          </Button>
        )}
        {canWrite && tab !== "assessment" && (
          <Button type="button" className="whitespace-nowrap" onClick={() => openNextAction(null)}>
            <Icon d={ICONS.plus} size={14} />
            Set next action
          </Button>
        )}
      </footer>

      {canWrite && (
        <NextActionModal
          target={nextAction}
          onClose={() => setNextAction(null)}
          onCompleted={() => setNextActionDone(true)}
        />
      )}

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

interface SectionFocus {
  section: DrawerSection;
  /** Bumped by every new link, so the same section asked for twice scrolls twice. */
  at: number;
}

const SECTION_FIND_ATTEMPTS = 20;
const SECTION_FIND_INTERVAL_MS = 100;

/**
 * Scrolls the Contact tab to a section once it is drawn. Outreach and Meetings draw only after their own reads land,
 * so it looks again for a couple of seconds; an Outreach with no run draws nothing, and the Contact fold stands in.
 */
function scrollToContactSection(section: DrawerSection): () => void {
  let attempts = 0;
  let timer: number | undefined;
  const find = () => {
    const panel = document.getElementById(`${TAB_ID_PREFIX}-panel-contact`);
    const target =
      section === "meetings"
        ? panel?.querySelector('section[aria-label="Meetings"]')
        : panel?.querySelector(`#${section}-section-body`)?.closest("section");
    const lastTry = ++attempts >= SECTION_FIND_ATTEMPTS;
    const shown = target ?? (lastTry ? panel?.querySelector("#contact-section-body")?.closest("section") : null);
    if (shown) shown.scrollIntoView({ block: "start", behavior: "smooth" });
    else if (!lastTry) timer = window.setTimeout(find, SECTION_FIND_INTERVAL_MS);
  };
  timer = window.setTimeout(find, 0);
  return () => window.clearTimeout(timer);
}
