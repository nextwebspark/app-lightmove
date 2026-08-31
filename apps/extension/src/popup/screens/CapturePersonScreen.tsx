import { useCallback, useEffect, useMemo, useState } from "react";
import { FIELD_LIMITS, MAX_CAREER_ENTRIES, cappedAt } from "../../api/fieldLimits";
import type { CandidateCareerEntry, CandidateSeniority, SaveCandidateRequest } from "../../api/types";
import { cityOf, countryOf } from "../../content/pageReader/extractedCompany";
import type { ExtractedPerson } from "../../content/pageReader/extractedPerson";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { OffLimitsToggle } from "../components/OffLimitsToggle";
import { PreviousRolesList } from "../components/PreviousRolesList";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { SeniorityChips } from "../components/SeniorityChips";
import { useCapturePerson } from "../hooks/useCapturePerson";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import type { CaptureScreenProps } from "./captureScreenProps";
import { useTriageCompanyMatch } from "../hooks/useTriageCompanyMatch";
import type { CaptureRefusal } from "../lib/captureRefusal";
import { CaptureSavedScreen } from "./CaptureSavedScreen";
import { SourceStrip } from "../components/SourceStrip";
import { SubjectRow } from "../components/SubjectRow";
import { useCloseAfterSave } from "../hooks/useCloseAfterSave";
import { useUndoCapture } from "../hooks/useUndoCapture";
import { Icon } from "../components/Icon";
import { ICONS } from "../lib/icons";

/** The form's own state: what the extractor read, as the consultant may have since corrected it. */
interface PersonDraft {
  fullName: string;
  title: string;
  employerName: string;
  locationCity: string;
  locationCountry: string;
  linkedinUrl: string;
  email: string;
  phone: string;
}

const EMPTY_DRAFT: PersonDraft = {
  fullName: "",
  title: "",
  employerName: "",
  locationCity: "",
  locationCountry: "",
  linkedinUrl: "",
  email: "",
  phone: "",
};

/**
 * The person form: what the profile said, editable, and which mandate it belongs to.
 *
 * Writes through the API's own candidate endpoint — the same one the web app's Add-executive drawer
 * posts to, with a strict subset of its fields. `source: "extension"` is the only difference between
 * this row and one typed in by hand.
 */
export function CapturePersonScreen({ page, projects }: CaptureScreenProps) {
  const capture = useCapturePerson();
  const { settings } = useCaptureSettings();
  const undo = useUndoCapture();

  const [draft, setDraft] = useState<PersonDraft>(EMPTY_DRAFT);
  const [seniority, setSeniority] = useState<CandidateSeniority | null>(null);
  const [isOffLimits, setIsOffLimits] = useState(false);
  const [note, setNote] = useState("");
  // The employer as last settled, not as typed: the lookup is two API calls, and running it on every
  // keystroke would spend the mandate's triage list a dozen times over one company name.
  const [settledEmployer, setSettledEmployer] = useState("");

  const company = useTriageCompanyMatch(projects.selectedProjectId, settledEmployer);

  // Seeded from the read, then owned by the form — a re-scan overwrites, which is what it is for.
  useEffect(() => {
    if (page.person) {
      setDraft(toDraft(page.person));
      setSettledEmployer(page.person.employerName ?? "");
    }
  }, [page.person]);

  // A name and a mandate. That is what the API requires, and the popup should not invent more.
  const canSave = useMemo(
    () => Boolean(draft.fullName.trim()) && Boolean(projects.selectedProjectId),
    [draft.fullName, projects.selectedProjectId],
  );

  const update = useCallback(
    (field: keyof PersonDraft) => (value: string) =>
      setDraft((current) => ({ ...current, [field]: value })),
    [],
  );

  const handleSave = () => {
    if (!projects.selectedProjectId) {
      return;
    }
    capture.save({
      projectId: projects.selectedProjectId,
      candidate: toCandidate({ ...draft, employerName: settledEmployer || draft.employerName }, {
        seniority,
        isOffLimits,
        note,
        tenure: page.person?.tenure ?? null,
        career: page.person?.career ?? [],
        triageCompanyId: company.match?.id ?? null,
        sourceUrl: page.sourceUrl,
      }),
    });
  };

  const handleCaptureAnother = () => {
    capture.reset();
    undo.reset();
    setNote("");
    setIsOffLimits(false);
    void page.rescan();
  };

  useCloseAfterSave(Boolean(capture.saved), settings.closesAfterSave);

  if (capture.saved && projects.selectedProjectId) {
    const savedId = capture.saved.id;
    const projectId = projects.selectedProjectId;
    return (
      <CaptureSavedScreen
        subjectName={capture.saved.fullName}
        landedIn={capture.saved.companyName
          ? `this mandate's people, at ${capture.saved.companyName}`
          : "this mandate's people, unmapped"}
        projectName={projects.selectedProjectName ?? "this mandate"}
        projectId={projectId}
        sourceUrl={page.sourceUrl}
        onCaptureAnother={handleCaptureAnother}
        onUndo={undo.hasUndone ? undefined : () => undo.undo({ projectId, candidateId: savedId })}
        isUndoing={undo.isUndoing}
      />
    );
  }

  return (
    <>
      <SourceStrip page={page} />

      <div className="flex-1 overflow-y-auto p-3.5">
        {page.readError && (
          <p className="mb-3.5 rounded-lg border border-line-soft bg-red-dim px-2.5 py-2 text-[11.5px] leading-[1.5] text-red">
            {page.readError}
          </p>
        )}

        <SubjectRow
          name={draft.fullName}
          detail={[draft.title, draft.employerName].filter(Boolean).join(" · ") || null}
          shape="circle"
        />

        <SectionLabel className="mb-2">Detected fields</SectionLabel>
        <div className="flex flex-col gap-2">
          <DetectedFieldInput label="Full name" value={draft.fullName} onChange={update("fullName")} />
          <DetectedFieldInput label="Current title" value={draft.title} onChange={update("title")} />
          <DetectedFieldInput
            label="Current company"
            value={draft.employerName}
            onChange={update("employerName")}
            onSettled={setSettledEmployer}
          />
          <CompanyLinkNote
            isMatched={Boolean(company.match)}
            isMatching={company.isMatching}
            employerName={settledEmployer}
          />
          <DetectedFieldInput label="City" value={draft.locationCity} onChange={update("locationCity")} />
          <DetectedFieldInput label="Country" value={draft.locationCountry} onChange={update("locationCountry")} />
          <DetectedFieldInput label="LinkedIn" value={draft.linkedinUrl} onChange={update("linkedinUrl")} inputMode="url" />
          <DetectedFieldInput label="Email" value={draft.email} onChange={update("email")} />
          <DetectedFieldInput label="Phone" value={draft.phone} onChange={update("phone")} />
        </div>

        {page.person?.tenure && (
          <p className="mt-2 font-mono text-[10.5px] text-text3">In role: {page.person.tenure}</p>
        )}

        {(page.person?.career.length ?? 0) > 0 && (
          <>
            <SectionLabel className="mb-2 mt-[18px]">Previous roles</SectionLabel>
            <PreviousRolesList roles={page.person?.career ?? []} />
          </>
        )}

        <SectionLabel className="mb-2 mt-[18px]">Seniority</SectionLabel>
        <SeniorityChips selected={seniority} onSelect={setSeniority} />

        <SectionLabel className="mb-2 mt-[18px]">Notes</SectionLabel>
        <textarea
          rows={3}
          value={note}
          aria-label="Notes"
          placeholder="Why this person matters to the mandate"
          onChange={(event) => setNote(event.target.value)}
          className="w-full resize-y rounded-[7px] border border-line bg-panel2 px-2.5 py-2 text-[12.5px] leading-[1.55] text-text outline-none focus:border-sky"
        />

        <div className="mt-[18px]">
          <OffLimitsToggle isOffLimits={isOffLimits} onToggle={setIsOffLimits} />
        </div>
      </div>

      <div className="flex flex-col gap-[9px] border-t border-line-soft px-3.5 py-[11px]">
        {capture.refusal && <RefusalNote refusal={capture.refusal} />}
        <ProjectSelect
          projects={projects.projects}
          selectedProjectId={projects.selectedProjectId}
          onSelect={projects.selectProject}
          isLoading={projects.isLoading}
        />
        <button
          type="button"
          onClick={handleSave}
          disabled={!canSave || capture.isSaving || company.isMatching}
          className="flex w-full items-center justify-center gap-[7px] rounded-lg bg-amber-btn py-2 text-[13px] font-semibold text-on-amber disabled:opacity-50"
        >
          <Icon d={ICONS.plus} />
          {saveLabel(capture.isSaving, company.isMatching)}
        </button>
      </div>
    </>
  );
}

/**
 * Saving while the lookup is still in flight would file the person unmapped against a company the
 * mandate holds — the write reads `company.match`, and an in-flight match is null.
 */
function saveLabel(isSaving: boolean, isMatching: boolean): string {
  if (isSaving) {
    return "Saving…";
  }
  return isMatching ? "Checking the mandate…" : "Save to project";
}

/** What the save will do with the employer, said before it does it. */
function CompanyLinkNote({
  isMatched,
  isMatching,
  employerName,
}: {
  isMatched: boolean;
  isMatching: boolean;
  employerName: string;
}) {
  if (!employerName.trim()) {
    return null;
  }
  return (
    <p className="text-[10.5px] text-text3">
      {isMatching
        ? "Looking for that company in this mandate…"
        : isMatched
          ? `Will be linked to ${employerName.trim()} in this mandate.`
          : "This mandate holds no company of that name, so they will be filed unmapped."}
    </p>
  );
}

/** A refusal the consultant can act on, rather than a generic apology. */
function RefusalNote({ refusal }: { refusal: CaptureRefusal }) {
  const explanation =
    refusal.code === "CANDIDATE_ALREADY_MAPPED"
      ? "This mandate already maps someone with that name. Open them on the Companies screen instead."
      : refusal.code === "FORBIDDEN"
        ? "You are not seated on that mandate, so you cannot add to it."
        : refusal.message;

  return (
    <p role="alert" className="rounded-lg border border-line-soft bg-red-dim px-2.5 py-2 text-[11.5px] leading-[1.5] text-red">
      {explanation}
    </p>
  );
}

/** The read, as the form's starting values. One "Dubai, United Arab Emirates" becomes two fields. */
function toDraft(person: ExtractedPerson): PersonDraft {
  return {
    fullName: person.fullName ?? "",
    title: person.title ?? "",
    employerName: person.employerName ?? "",
    locationCity: cityOf(person.location) ?? "",
    locationCountry: countryOf(person.location) ?? "",
    linkedinUrl: person.linkedinUrl ?? "",
    email: person.email ?? "",
    phone: person.phone ?? "",
  };
}

interface CandidateExtras {
  seniority: CandidateSeniority | null;
  isOffLimits: boolean;
  note: string;
  tenure: string | null;
  career: { company: string | null; title: string | null; period: string | null }[];
  triageCompanyId: string | null;
  sourceUrl: string | null;
}

function toCandidate(draft: PersonDraft, extras: CandidateExtras): SaveCandidateRequest {
  const employerName = cappedAt(draft.employerName, FIELD_LIMITS.employerName);
  // The current role is the only home tenure has — there is no such column, and `period` is the free
  // text ("Mar 2021 – Present") this is. Not yearsExperience, which is a whole career and would be a
  // number the mandate's own screens then show as fact.
  const currentRole = extras.tenure
    ? [{ company: employerName, title: blankToNull(draft.title), period: extras.tenure }]
    : [];

  return {
    // Never both: the server ignores employerName when the id is present, and sending the two lets
    // them disagree.
    triageCompanyId: extras.triageCompanyId,
    employerName: extras.triageCompanyId ? null : employerName,
    fullName: cappedAt(draft.fullName, FIELD_LIMITS.fullName) ?? "",
    title: cappedAt(draft.title, FIELD_LIMITS.title),
    seniority: extras.seniority,
    status: extras.isOffLimits ? "offLimits" : null,
    // A malformed address is dropped rather than sent: @Email would 400 the whole save over a field
    // the page filled in, not the consultant.
    email: validEmailOrNull(draft.email),
    phone: cappedAt(draft.phone, FIELD_LIMITS.phone),
    linkedinUrl: cappedAt(draft.linkedinUrl, FIELD_LIMITS.linkedinUrl),
    locationCity: cappedAt(draft.locationCity, FIELD_LIMITS.locationCity),
    locationCountry: cappedAt(draft.locationCountry, FIELD_LIMITS.locationCountry),
    note: cappedAt(extras.note, FIELD_LIMITS.note),
    // Capped at what the DTO takes: a 26th entry would 400 a save over a list with no input on it.
    career: [...currentRole, ...extras.career].slice(0, MAX_CAREER_ENTRIES).map(cappedEntry),
    source: "extension",
    sourceUrl: cappedAt(extras.sourceUrl, FIELD_LIMITS.sourceUrl),
  };
}

/** A career entry is three capped fields; `period` is free text and LinkedIn's is often long. */
function cappedEntry(entry: CandidateCareerEntry): CandidateCareerEntry {
  return {
    company: cappedAt(entry.company, FIELD_LIMITS.careerCompany),
    title: cappedAt(entry.title, FIELD_LIMITS.careerTitle),
    period: cappedAt(entry.period, FIELD_LIMITS.careerPeriod),
  };
}

function blankToNull(value: string): string | null {
  return value.trim() || null;
}

function validEmailOrNull(value: string): string | null {
  const trimmed = value.trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed) ? trimmed : null;
}
