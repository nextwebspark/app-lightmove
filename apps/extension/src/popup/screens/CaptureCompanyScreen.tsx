import { useCallback, useEffect, useMemo, useState } from "react";
import type { ExtractedCompany } from "../../content/pageReader/extractedCompany";
import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import { DESTINATION_PAST_TENSE, type TriageDestination } from "../../domain/triageDestination";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { DestinationButtons } from "../components/DestinationButtons";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { useCaptureCompany } from "../hooks/useCaptureCompany";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import type { CaptureScreenProps } from "./captureScreenProps";
import type { CaptureRefusal } from "../lib/captureRefusal";
import { SourceStrip } from "../components/SourceStrip";
import { SubjectRow } from "../components/SubjectRow";
import { useCloseAfterSave } from "../hooks/useCloseAfterSave";
import { useUndoCapture } from "../hooks/useUndoCapture";
import { CaptureSavedScreen } from "./CaptureSavedScreen";

/** The form's own state: what the extractor read, as the consultant may have since corrected it. */
interface CompanyDraft {
  companyName: string;
  website: string;
  linkedinUrl: string;
  industry: string;
  companyCity: string;
  companyCountry: string;
  numEmployees: string;
  foundedYear: string;
  annualRevenue: string;
}

const EMPTY_DRAFT: CompanyDraft = {
  companyName: "",
  website: "",
  linkedinUrl: "",
  industry: "",
  companyCity: "",
  companyCountry: "",
  numEmployees: "",
  foundedYear: "",
  annualRevenue: "",
};

/**
 * The capture form: what the page said, editable, and where it should go. The row carries
 * `source: "extension"` and no universe id — a captured company is identified by its name within
 * the mandate.
 */
export function CaptureCompanyScreen({ page, projects }: CaptureScreenProps) {
  const capture = useCaptureCompany();
  const { settings } = useCaptureSettings();
  const undo = useUndoCapture();

  const [draft, setDraft] = useState<CompanyDraft>(EMPTY_DRAFT);
  const [note, setNote] = useState("");
  const [attemptedDestination, setAttemptedDestination] = useState<TriageDestination | null>(null);

  // Seeded from the read, then owned by the form. This runs whenever the reader answers with a new
  // object, so a re-scan does overwrite edits — which is what "Re-scan" is for. React Query's
  // structural sharing is what keeps an unchanged re-read from resetting the form underneath someone.
  useEffect(() => {
    if (page.company) {
      setDraft(toDraft(page.company, page.sourceUrl));
    }
  }, [page.company, page.sourceUrl]);

  // A name and a mandate. That is what the API requires, and the popup should not invent more.
  const canSave = useMemo(
    () => Boolean(draft.companyName.trim()) && Boolean(projects.selectedProjectId),
    [draft.companyName, projects.selectedProjectId],
  );

  // Before its call sites rather than hoisted after them, and memoised: seven `onChange` props that
  // are otherwise stable should not be seven new closures on every keystroke.
  const update = useCallback(
    (field: keyof CompanyDraft) => (value: string) =>
      setDraft((current) => ({ ...current, [field]: value })),
    [],
  );

  const handleCapture = (destination: TriageDestination) => {
    if (!projects.selectedProjectId) {
      return;
    }
    setAttemptedDestination(destination);
    capture.save({
      projectId: projects.selectedProjectId,
      destination,
      capture: {
        source: "extension",
        companyName: cappedAt(draft.companyName, FIELD_LIMITS.companyName) ?? "",
        website: cappedAt(draft.website, FIELD_LIMITS.website),
        companyLinkedinUrl: cappedAt(draft.linkedinUrl, FIELD_LIMITS.companyLinkedinUrl),
        industry: cappedAt(draft.industry, FIELD_LIMITS.industry),
        companyCity: cappedAt(draft.companyCity, FIELD_LIMITS.companyCity),
        companyCountry: cappedAt(draft.companyCountry, FIELD_LIMITS.companyCountry),
        numEmployees: toWholeNumber(draft.numEmployees),
        foundedYear: toWholeNumber(draft.foundedYear),
        annualRevenue: toWholeNumber(draft.annualRevenue),
        shortDescription: cappedAt(page.company?.description, FIELD_LIMITS.shortDescription),
        note: cappedAt(note, FIELD_LIMITS.note),
        sourceUrl: cappedAt(page.sourceUrl, FIELD_LIMITS.sourceUrl),
      },
    });
  };

  const handleCaptureAnother = () => {
    capture.reset();
    undo.reset();
    setAttemptedDestination(null);
    setNote("");
    void page.rescan();
  };

  useCloseAfterSave(Boolean(capture.saved), settings.closesAfterSave);

  if (capture.saved && projects.selectedProjectId && attemptedDestination) {
    const landed = capture.saved.status === "declined" ? attemptedDestination : capture.saved.status;
    const savedId = capture.saved.id;
    const projectId = projects.selectedProjectId;
    return (
      <CaptureSavedScreen
        subjectName={capture.saved.companyName}
        landedIn={DESTINATION_PAST_TENSE[landed]}
        projectName={projects.selectedProjectName ?? "this mandate"}
        projectId={projectId}
        sourceUrl={page.sourceUrl}
        onCaptureAnother={handleCaptureAnother}
        onUndo={undo.hasUndone ? undefined : () => undo.undo({ projectId, triageCompanyId: savedId })}
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
          name={draft.companyName}
          detail={[draft.companyCity, draft.website].filter(Boolean).join(" · ") || null}
          shape="square"
        />

        <SectionLabel className="mb-2">Detected fields</SectionLabel>
        <div className="flex flex-col gap-2">
          <DetectedFieldInput label="Company name" value={draft.companyName} onChange={update("companyName")} />
          <DetectedFieldInput label="Website" value={draft.website} onChange={update("website")} inputMode="url" />
          <DetectedFieldInput label="LinkedIn" value={draft.linkedinUrl} onChange={update("linkedinUrl")} inputMode="url" />
          <DetectedFieldInput label="Sector" value={draft.industry} onChange={update("industry")} />
          <DetectedFieldInput label="City" value={draft.companyCity} onChange={update("companyCity")} />
          <DetectedFieldInput label="Country" value={draft.companyCountry} onChange={update("companyCountry")} />
          <DetectedFieldInput label="Headcount" value={draft.numEmployees} onChange={update("numEmployees")} inputMode="numeric" />
          <DetectedFieldInput label="Founded" value={draft.foundedYear} onChange={update("foundedYear")} inputMode="numeric" />
          <DetectedFieldInput label="Annual revenue (USD)" value={draft.annualRevenue} onChange={update("annualRevenue")} inputMode="numeric" />
        </div>

        <SectionLabel className="mb-2 mt-[18px]">Notes</SectionLabel>
        <textarea
          rows={3}
          value={note}
          aria-label="Notes"
          placeholder="Context for the universe entry"
          onChange={(event) => setNote(event.target.value)}
          className="w-full resize-y rounded-[7px] border border-line bg-panel2 px-2.5 py-2 text-[12.5px] leading-[1.55] text-text outline-none focus:border-sky"
        />
      </div>

      <div className="flex flex-col gap-[9px] border-t border-line-soft px-3.5 py-[11px]">
        {capture.refusal && <RefusalNote refusal={capture.refusal} />}
        <ProjectSelect
          projects={projects.projects}
          selectedProjectId={projects.selectedProjectId}
          onSelect={projects.selectProject}
          isLoading={projects.isLoading}
        />
        <DestinationButtons
          onCapture={handleCapture}
          isSaving={capture.isSaving}
          savingDestination={capture.isSaving ? attemptedDestination : null}
          isDisabled={!canSave}
        />
      </div>
    </>
  );

}

/** A refusal the consultant can act on, rather than a generic apology. */
function RefusalNote({ refusal }: { refusal: CaptureRefusal }) {
  const explanation =
    refusal.code === "TRIAGE_COMPANY_ALREADY_HELD"
      ? "This mandate already holds a company with that name. Open it on the Companies screen instead."
      : refusal.code === "FORBIDDEN"
        ? "You are not seated on that mandate, so you cannot add to it."
        : refusal.message;

  return (
    <p role="alert" className="rounded-lg border border-line-soft bg-red-dim px-2.5 py-2 text-[11.5px] leading-[1.5] text-red">
      {explanation}
    </p>
  );
}

/**
 * The read, as the form's starting values. The page's own URL seeds an unanswered website *here* so it
 * can be seen and deleted; applied at write time it would be the one field that cannot be cleared.
 */
function toDraft(company: ExtractedCompany, sourceUrl: string | null): CompanyDraft {
  return {
    companyName: company.companyName ?? "",
    website: company.website ?? sourceUrl ?? "",
    linkedinUrl: company.linkedinUrl ?? "",
    industry: company.industry ?? "",
    companyCity: company.companyCity ?? "",
    companyCountry: company.companyCountry ?? "",
    numEmployees: company.numEmployees ? String(company.numEmployees) : "",
    foundedYear: "",
    annualRevenue: "",
  };
}

/** A blank or nonsense figure is sent as absent, never as zero — zero is a claim, absence is not. */
function toWholeNumber(value: string): number | null {
  const parsed = Number.parseInt(value.replace(/[^\d]/g, ""), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
