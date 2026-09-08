import { useMutation } from "@tanstack/react-query";
import { useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Select, TextArea, useToast } from "../../../components/ui";
import { CollapsibleSection } from "../../../components/ui/CollapsibleSection";
import { DetailGrid, DetailPill, DetailTile } from "../../../components/ui/DetailList";
import { DrawerCloseButton } from "../../../components/ui/Drawer";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate, formatNumber } from "../../../lib/format";
import { toBrowsableUrl } from "../../../lib/url";
import type { CustomColumn, CustomFieldValues } from "../../customcolumns/api/types";
import { CustomFieldsFieldset } from "../../customcolumns/components/CustomFieldsFieldset";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, CandidateStatus, SaveCandidatePayload } from "../api/types";
import { patchOf, replayOf, type ProfileFormSection } from "../lib/candidateForm";
import {
  candidateStatusStyle,
  CANDIDATE_SOURCE_STYLES,
  CANDIDATE_STATUSES,
} from "../lib/candidateVocabulary";
import { careerSummary } from "../lib/careerTimeline";
import { packageOf } from "../lib/compensation";
import { useProfileSections, type ProfileSection } from "../lib/useProfileSections";
import { CandidateAvatar } from "./CandidateAvatar";
import {
  BackgroundFields,
  CareerFields,
  CompensationFields,
  ContactFields,
  IdentityFields,
  SummaryFields,
} from "./CandidateFieldGroups";
import { CareerTimeline } from "./CareerTimeline";
import { CompensationSummary } from "./CompensationSummary";
import { ProfileSectionForm, SectionEditButton, SectionEditor } from "./ProfileSectionForm";

/** What a pencil opens: one of the form's sections, or the mandate's own columns. */
type EditableSection = Exclude<ProfileFormSection, "note"> | "columns";

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
 * <p>Keyed by the caller on the candidate's id: which section is open is state about this person,
 * and moving to the next profile must start it fresh.
 */
export function CandidateProfile({
  projectId,
  candidate,
  customColumns,
  canWrite,
  onClose,
  onSaved,
  onRemove,
}: {
  projectId: string;
  candidate: Candidate;
  customColumns: readonly CustomColumn[];
  /** False for a client representative, who reads a mandate's people and changes nothing about them. */
  canWrite: boolean;
  onClose: () => void;
  /** Every write's answer — the caller keeps showing what the server now holds. */
  onSaved: (saved: Candidate) => void;
  /** Absent for a reader who may not write. */
  onRemove?: (candidate: Candidate) => void;
}) {
  const toast = useToast();
  const sections = useProfileSections();
  const body = useRef<HTMLDivElement>(null);
  const [editing, setEditing] = useState<EditableSection | null>(null);

  const replace = (patch: Partial<SaveCandidatePayload>) =>
    candidatesApi.updateCandidate(projectId, candidate.id, { ...replayOf(candidate), ...patch });

  const finish = (saved: Candidate) => {
    setEditing(null);
    onSaved(saved);
  };

  const changeStatus = useMutation({
    mutationFn: (status: CandidateStatus) =>
      candidatesApi.changeCandidateStatus(projectId, candidate.id, status),
    onSuccess: (saved) => {
      onSaved(saved);
      toast(`${saved.fullName} is now ${candidateStatusStyle(saved.status).label.toLowerCase()}`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const startEditing = (section: EditableSection) => {
    setEditing(section);
    if (section === "identity" && body.current) body.current.scrollTop = 0;
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

  return (
    <>
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <DrawerCloseButton onClose={onClose} />
        <div className="group flex items-start gap-3 pe-8">
          <CandidateAvatar
            projectId={projectId}
            candidate={candidate}
            className="size-[44px] rounded-[10px] border border-line text-sm"
          />
          <div className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5">
              <h2 className="font-sans text-base font-semibold">{candidate.fullName}</h2>
              <HeaderProfileLink linkedinUrl={candidate.linkedinUrl} />
              {pencil("identity", "details")}
            </span>
            <p className="mt-0.5 font-mono text-[12.5px] text-text2">{candidate.title ?? "—"}</p>
            <p className="mt-1 font-mono text-[11.5px] text-text3">
              {[employerLabel, candidate.locationCity, candidate.locationCountry]
                .filter(Boolean)
                .join(" · ") || "No employer or location recorded"}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {candidate.seniority && <DetailPill label={candidate.seniority} />}
              <DetailPill
                label={CANDIDATE_SOURCE_STYLES[candidate.source].label}
                className={CANDIDATE_SOURCE_STYLES[candidate.source].className}
              />
              {canWrite ? (
                <Select
                  value={candidate.status}
                  aria-label="Status"
                  disabled={changeStatus.isPending}
                  onChange={(event) => changeStatus.mutate(event.target.value as CandidateStatus)}
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
            </div>
          </div>
        </div>
      </div>

      <div ref={body} className="min-h-0 flex-1 overflow-y-auto px-5">
        {editing === "identity" ? (
          <section className="border-b border-line-soft py-4">
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
                  employerLocked={candidate.triageCompanyId !== null}
                />
              )}
            </SectionEditor>
          </section>
        ) : (
          <div className="-mb-1 flex justify-end gap-3 pt-2.5">
            <FoldAllButton label="Expand all" onClick={() => sections.setAll(true)} />
            <FoldAllButton label="Collapse all" onClick={() => sections.setAll(false)} />
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
            <p className="text-[13px]/[1.6] text-text2">
              {candidate.summary ?? "No summary written yet."}
            </p>
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
            <ul className="flex flex-col gap-2.5">
              {candidate.education.map((school, index) => (
                <li key={`${school.school}-${school.degree}-${index}`}>
                  {school.school && (
                    <div className="font-sans text-[13px] font-semibold text-text">{school.school}</div>
                  )}
                  {school.degree && (
                    <div className="mt-0.5 font-sans text-[13px] text-text2">{school.degree}</div>
                  )}
                  {school.period && (
                    <div className="mt-0.5 font-mono text-[11.5px] text-text3">{school.period}</div>
                  )}
                </li>
              ))}
            </ul>
          </CollapsibleSection>
        )}

        <CollapsibleSection
          {...foldProps("compensation")}
          title="Compensation"
          summary={joinFacts([
            total > 0 ? `${currency} ${formatNumber(total)}`.trim() : null,
            compensation.noticePeriod ? `${compensation.noticePeriod} notice` : null,
          ])}
          action={pencil("compensation", "compensation")}
        >
          {editing === "compensation" ? (
            <SectionEditor
              section="compensation"
              candidate={candidate}
              save={replace}
              doneMessage="Compensation saved"
              onDone={finish}
              onCancel={() => setEditing(null)}
            >
              {(form) => (
                <CompensationFields
                  register={form.register}
                  errors={form.formState.errors}
                  watch={form.watch}
                  setValue={form.setValue}
                  storedCurrency={compensation.currency}
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
            candidate.nationality,
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
              {(form) => <BackgroundFields register={form.register} errors={form.formState.errors} />}
            </SectionEditor>
          ) : (
            <>
              <DetailGrid>
                <DetailTile label="Nationality" value={candidate.nationality} />
                <DetailTile
                  label="Experience"
                  value={candidate.yearsExperience ? `${candidate.yearsExperience} years` : null}
                />
              </DetailGrid>
              <PillRow label="Languages" values={candidate.languages} empty="No languages recorded." />
              {candidate.skills.length > 0 && <PillRow label="Skills" values={candidate.skills} />}
            </>
          )}
        </CollapsibleSection>

        <CollapsibleSection
          {...foldProps("contact")}
          title="Contact"
          summary={joinFacts([candidate.email, candidate.phone])}
          action={pencil("contact", "contact")}
        >
          {editing === "contact" ? (
            <SectionEditor
              section="contact"
              candidate={candidate}
              save={replace}
              doneMessage="Contact saved"
              onDone={finish}
              onCancel={() => setEditing(null)}
            >
              {(form) => <ContactFields register={form.register} errors={form.formState.errors} />}
            </SectionEditor>
          ) : (
            <ContactTiles candidate={candidate} />
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

        <NoteSection
          candidate={candidate}
          canWrite={canWrite}
          open={sections.isOpen("note")}
          onToggle={() => sections.toggle("note")}
          save={replace}
          onSaved={onSaved}
        />

        <p className="py-4 font-mono text-[11px] text-text3">
          Added {formatInstantDate(candidate.addedAt)}
          {candidate.enrichedAt && ` · Researched ${formatInstantDate(candidate.enrichedAt)}`}
          {capturedFrom && (
            <>
              {" · "}
              <a
                href={capturedFrom}
                target="_blank"
                rel="noreferrer noopener"
                className="hover:text-text hover:underline"
              >
                Captured from {new URL(capturedFrom).hostname}
              </a>
            </>
          )}
        </p>
      </div>

      {onRemove && (
        <div className="flex flex-none border-t border-line-soft px-5 py-3">
          <Button
            type="button"
            variant="secondary"
            className="text-red"
            onClick={() => onRemove(candidate)}
          >
            Remove from mandate
          </Button>
        </div>
      )}
    </>
  );
}

/**
 * The note is not behind a pencil: it is always a textarea, and Save appears the moment it differs
 * from what is stored. It is the mandate's own remark rather than a fact about the person — the
 * thing a consultant writes after every call — and a remark that took two clicks to start would
 * not get written. Ctrl/⌘-Enter saves; the same write as every other section, note over profile.
 */
function NoteSection({
  candidate,
  canWrite,
  open,
  onToggle,
  save,
  onSaved,
}: {
  candidate: Candidate;
  canWrite: boolean;
  open: boolean;
  onToggle: () => void;
  save: (patch: Partial<SaveCandidatePayload>) => Promise<Candidate>;
  onSaved: (saved: Candidate) => void;
}) {
  const toast = useToast();
  const [note, setNote] = useState(candidate.note ?? "");
  // What the server holds, by its own last answer — so Save disappears the moment it lands, not
  // once the caller has got round to re-rendering.
  const [stored, setStored] = useState(candidate.note ?? "");
  const dirty = note !== stored;

  const saving = useMutation({
    mutationFn: (text: string) =>
      save(patchOf("note", { note: text.trim() }, candidate.triageCompanyId !== null)),
    onSuccess: (saved) => {
      setStored(saved.note ?? "");
      setNote(saved.note ?? "");
      onSaved(saved);
      toast("Note saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey) && dirty) {
      event.preventDefault();
      saving.mutate(note);
    }
  };

  return (
    <CollapsibleSection
      id="note"
      open={open || dirty}
      onToggle={onToggle}
      title="Note"
      summary={firstLine(candidate.note)}
      action={
        canWrite && dirty ? (
          <button
            type="button"
            onClick={() => saving.mutate(note)}
            disabled={saving.isPending}
            className="font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-amber transition hover:underline disabled:opacity-50"
          >
            {saving.isPending ? "Saving…" : "Save note"}
          </button>
        ) : undefined
      }
    >
      {canWrite ? (
        <TextArea
          value={note}
          onChange={(event) => setNote(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={4}
          maxLength={2000}
          aria-label="Note on this executive"
          placeholder="Your own remark on this person, for this mandate — what they said, what to do next…"
          className="border-dashed font-sans text-[13px]/[1.55]"
        />
      ) : (
        <p className="whitespace-pre-wrap text-[13px]/[1.6] text-text2">
          {candidate.note ?? "No note on this person for this mandate."}
        </p>
      )}
    </CollapsibleSection>
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

function ContactTiles({ candidate }: { candidate: Candidate }) {
  const profileUrl = toBrowsableUrl(candidate.linkedinUrl);
  return (
    <DetailGrid>
      <DetailTile label="Email" value={candidate.email} />
      <DetailTile label="Phone" value={candidate.phone} />
      {/* Through `toBrowsableUrl` rather than straight into the href. Every write is already gated
          by SuppliedText, but trusting that from the render side makes this tile the one place a
          value stored before the gate — or posted by the browser plugin, whose CandidateSource is
          already in the schema — could reach a browser as something it should not follow.
          `lib/url.ts` states the rule; the grids and the company panel already keep it. */}
      <DetailTile
        label="LinkedIn"
        full
        value={
          profileUrl ? (
            <a
              href={profileUrl}
              target="_blank"
              rel="noreferrer noopener"
              className="text-sky hover:underline"
            >
              {profileUrl}
            </a>
          ) : null
        }
      />
    </DetailGrid>
  );
}

/** The LinkedIn glyph beside the name, through the same guard as the Contact tile's text link. */
function HeaderProfileLink({ linkedinUrl }: { linkedinUrl: string | null }) {
  const profileUrl = toBrowsableUrl(linkedinUrl);
  if (!profileUrl) return null;
  return (
    <a
      href={profileUrl}
      target="_blank"
      rel="noreferrer noopener"
      aria-label="LinkedIn profile"
      className="flex-none text-text3 transition hover:text-sky"
    >
      <Icon d={ICONS.linkedin} size={14} />
    </a>
  );
}

function SectionHeading({ children }: { children: ReactNode }) {
  return (
    <h3 className="mb-3 font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-text3">
      {children}
    </h3>
  );
}

function FoldAllButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="font-mono text-[11px] text-text3 transition hover:text-text"
    >
      {label}
    </button>
  );
}

/** The mockup's language pills, reused for skills: a row of small rounded tags under a tiny label. */
function PillRow({ label, values, empty }: { label: string; values: readonly string[]; empty?: string }) {
  return (
    <div className="mt-3">
      <div className="mb-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-text3">
        {label}
      </div>
      {values.length === 0 ? (
        <p className="font-mono text-[12.5px] text-text3">{empty}</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {values.map((value) => (
            <span
              key={value}
              className="inline-flex items-center rounded-full border border-line bg-panel2 px-2.5 py-1 font-mono text-[12px] font-medium text-text2"
            >
              {value}
            </span>
          ))}
        </div>
      )}
    </div>
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
