import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { PageReadNote } from "../components/PageReadNote";
import { Icon } from "../components/Icon";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { useCaptureCompany } from "../hooks/useCaptureCompany";
import { useSeededField } from "../hooks/useSeededField";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import type { CaptureScreenProps } from "./captureScreenProps";
import type { CaptureRefusal } from "../lib/captureRefusal";
import { SourceStrip } from "../components/SourceStrip";
import { SubjectRow } from "../components/SubjectRow";
import { useCloseAfterSave } from "../hooks/useCloseAfterSave";
import { useUndoCapture } from "../hooks/useUndoCapture";
import { CaptureSavedScreen } from "./CaptureSavedScreen";
import { ICONS } from "../lib/icons";

/**
 * The capture form: the name the page said, editable, filed in the mandate's universe.
 *
 * V1 captures the name and — silently — the company's URLs; everything richer is enrichment, later
 * and server-side. The row carries `source: "extension"` and no universe id — a captured company is
 * identified by its name within the mandate.
 */
export function CaptureCompanyScreen({ page, projects }: CaptureScreenProps) {
  const capture = useCaptureCompany();
  const { settings } = useCaptureSettings();
  const undo = useUndoCapture();

  // Keyed on the page rather than its address; see the person screen for why both rules are what they
  // are. The note is never seeded, only reset when the panel moves to another page.
  const { value: companyName, edit: editCompanyName, hasBeenEdited } = useSeededField(
    page.company?.companyName ?? null,
    page.pageKey,
  );

  // See the person screen: locked while it is still the page's own answer.
  const isNameLocked = Boolean(page.company?.companyName) && !hasBeenEdited;
  const { value: note, edit: editNote } = useSeededField(null, page.pageKey);

  // A name and a mandate. That is what the API requires, and the popup should not invent more.
  const canSave = Boolean(companyName.trim()) && Boolean(projects.selectedProjectId);

  const handleCapture = () => {
    if (!projects.selectedProjectId) {
      return;
    }
    capture.save({
      projectId: projects.selectedProjectId,
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
    editNote("");
    void page.rescan();
  };

  useCloseAfterSave(Boolean(capture.saved), settings.closesAfterSave);

  if (capture.saved && projects.selectedProjectId) {
    const savedId = capture.saved.id;
    const projectId = projects.selectedProjectId;
    return (
      <CaptureSavedScreen
        subjectName={capture.saved.companyName}
        landedIn="the universe"
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

        <SubjectRow name={companyName} isReading={page.isReading} detail={null} shape="square" />

        <SectionLabel className="mb-2">Detected</SectionLabel>
        <DetectedFieldInput
          label="Company name"
          value={companyName}
          onChange={editCompanyName}
          isReading={page.isReading}
          isLocked={isNameLocked}
        />

        <SectionLabel className="mb-2 mt-[18px]">Notes</SectionLabel>
        <textarea
          rows={3}
          value={note}
          aria-label="Notes"
          placeholder="Context for the universe entry"
          onChange={(event) => editNote(event.target.value)}
          className="w-full resize-y rounded-[7px] border border-u-border-strong bg-u-raised px-2.5 py-2 text-[12.5px] leading-[1.55] text-u-text outline-none focus:border-u-accent"
        />
      </div>

      <div className="flex flex-col gap-[9px] border-t border-u-border px-3.5 py-[11px]">
        {capture.refusal && <RefusalNote refusal={capture.refusal} />}
        <ProjectSelect
          projects={projects.projects}
          selectedProjectId={projects.selectedProjectId}
          onSelect={projects.selectProject}
          isLoading={projects.isLoading}
        />
        <button
          type="button"
          onClick={handleCapture}
          disabled={!canSave || capture.isSaving}
          className="flex w-full items-center justify-center gap-[7px] rounded-lg bg-u-accent-solid py-2 text-[13px] font-semibold text-white disabled:opacity-50"
        >
          <Icon d={ICONS.plus} />
          {capture.isSaving ? "Adding…" : "Add to universe"}
        </button>
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
    <p role="alert" className="rounded-lg border border-u-border bg-u-offlimits-tint px-2.5 py-2 text-[11.5px] leading-[1.5] text-u-offlimits">
      {explanation}
    </p>
  );
}
