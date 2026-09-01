import { useEffect, useState } from "react";
import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import { DESTINATION_PAST_TENSE, type TriageDestination } from "../../domain/triageDestination";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { PageReadNote } from "../components/PageReadNote";
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

/**
 * The capture form: the name the page said, editable, and where it should go.
 *
 * V1 captures the name and — silently — the company's URLs; everything richer is enrichment, later
 * and server-side. The row carries `source: "extension"` and no universe id — a captured company is
 * identified by its name within the mandate.
 */
export function CaptureCompanyScreen({ page, projects }: CaptureScreenProps) {
  const capture = useCaptureCompany();
  const { settings } = useCaptureSettings();
  const undo = useUndoCapture();

  const [companyName, setCompanyName] = useState("");
  const [note, setNote] = useState("");
  const [attemptedDestination, setAttemptedDestination] = useState<TriageDestination | null>(null);

  // Seeded from the read, then owned by the form. This runs whenever the reader answers with a new
  // object, so a re-scan does overwrite edits — which is what "Re-scan" is for. React Query's
  // structural sharing is what keeps an unchanged re-read from resetting the form underneath someone.
  useEffect(() => {
    if (page.company) {
      setCompanyName(page.company.companyName ?? "");
    }
  }, [page.company]);

  // A name and a mandate. That is what the API requires, and the popup should not invent more.
  const canSave = Boolean(companyName.trim()) && Boolean(projects.selectedProjectId);

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
        companyName: cappedAt(companyName, FIELD_LIMITS.companyName) ?? "",
        companyLinkedinUrl: cappedAt(page.company?.linkedinUrl, FIELD_LIMITS.companyLinkedinUrl),
        sourceUrl: cappedAt(page.sourceUrl, FIELD_LIMITS.sourceUrl),
        note: cappedAt(note, FIELD_LIMITS.note),
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

      <div className="min-h-0 flex-1 overflow-y-auto p-3.5">
        <PageReadNote error={page.readError} selectedProjectId={projects.selectedProjectId} />

        <SubjectRow name={companyName} detail={null} shape="square" />

        <SectionLabel className="mb-2">Detected</SectionLabel>
        <DetectedFieldInput label="Company name" value={companyName} onChange={setCompanyName} />

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
