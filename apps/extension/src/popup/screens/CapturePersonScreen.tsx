import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import type { SaveCandidateRequest } from "../../api/types";
import type { ExtractedPerson } from "../../content/pageReader/extractedPerson";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { PageReadNote } from "../components/PageReadNote";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { useCapturePerson } from "../hooks/useCapturePerson";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import type { CaptureScreenProps } from "./captureScreenProps";
import type { CaptureRefusal } from "../lib/captureRefusal";
import { CaptureSavedScreen } from "./CaptureSavedScreen";
import { SourceStrip } from "../components/SourceStrip";
import { SubjectRow } from "../components/SubjectRow";
import { useCloseAfterSave } from "../hooks/useCloseAfterSave";
import { useUndoCapture } from "../hooks/useUndoCapture";
import { Icon } from "../components/Icon";
import { ICONS } from "../lib/icons";

/**
 * The person form: the name the page said, editable, and which mandate it belongs to.
 *
 * V1 captures the name and — silently — the profile URL; everything richer is enrichment, later and
 * server-side. Writes through the API's own candidate endpoint, the same one the web app's
 * Add-executive drawer posts to; `source: "extension"` is the only difference between this row and
 * one typed in by hand.
 */
export function CapturePersonScreen({ page, projects }: CaptureScreenProps) {
  const capture = useCapturePerson();
  const { settings } = useCaptureSettings();
  const undo = useUndoCapture();

  const [fullName, setFullName] = useState("");
  const [note, setNote] = useState("");

  // Seeded from the read, then owned by the form. A new page always reseeds, empty included — the
  // previous person's name must never linger over someone else's profile. Re-reads of the *same*
  // page only fill a blank field: LinkedIn's SPA re-reads land while a name is being corrected, and
  // one arriving late must not overwrite the correction.
  const seededFor = useRef<string | null>(null);
  useEffect(() => {
    if (!page.person) {
      return;
    }
    if (seededFor.current !== page.sourceUrl) {
      seededFor.current = page.sourceUrl;
      setFullName(page.person.fullName ?? "");
      return;
    }
    setFullName((typed) => typed || (page.person?.fullName ?? ""));
  }, [page.person, page.sourceUrl]);

  // A name and a mandate. That is what the API requires, and the popup should not invent more.
  const canSave = useMemo(
    () => Boolean(fullName.trim()) && Boolean(projects.selectedProjectId),
    [fullName, projects.selectedProjectId],
  );

  const handleSave = useCallback(() => {
    if (!projects.selectedProjectId) {
      return;
    }
    capture.save({
      projectId: projects.selectedProjectId,
      candidate: toCandidate(fullName, note, page.person, page.sourceUrl),
    });
  }, [capture, fullName, note, page.person, page.sourceUrl, projects.selectedProjectId]);

  const handleCaptureAnother = () => {
    capture.reset();
    undo.reset();
    setNote("");
    void page.rescan();
  };

  useCloseAfterSave(Boolean(capture.saved), settings.closesAfterSave);

  if (capture.saved && projects.selectedProjectId) {
    const savedId = capture.saved.id;
    const projectId = projects.selectedProjectId;
    return (
      <CaptureSavedScreen
        subjectName={capture.saved.fullName}
        landedIn="this mandate's people"
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

      <div className="min-h-0 flex-1 overflow-y-auto p-3.5">
        <PageReadNote error={page.readError} selectedProjectId={projects.selectedProjectId} />

        <SubjectRow name={fullName} detail={null} shape="circle" />

        <SectionLabel className="mb-2">Detected</SectionLabel>
        <DetectedFieldInput label="Full name" value={fullName} onChange={setFullName} />

        <SectionLabel className="mb-2 mt-[18px]">Notes</SectionLabel>
        <textarea
          rows={3}
          value={note}
          aria-label="Notes"
          placeholder="Why this person matters to the mandate"
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
        <button
          type="button"
          onClick={handleSave}
          disabled={!canSave || capture.isSaving}
          className="flex w-full items-center justify-center gap-[7px] rounded-lg bg-amber-btn py-2 text-[13px] font-semibold text-on-amber disabled:opacity-50"
        >
          <Icon d={ICONS.plus} />
          {capture.isSaving ? "Saving…" : "Save to project"}
        </button>
      </div>
    </>
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

/** The save: the name and note as edited, and the URLs the page cannot lie about, sent unshown. */
function toCandidate(
  fullName: string,
  note: string,
  person: ExtractedPerson | null,
  sourceUrl: string | null,
): SaveCandidateRequest {
  return {
    fullName: cappedAt(fullName, FIELD_LIMITS.fullName) ?? "",
    linkedinUrl: cappedAt(person?.linkedinUrl, FIELD_LIMITS.linkedinUrl),
    note: cappedAt(note, FIELD_LIMITS.note),
    source: "extension",
    sourceUrl: cappedAt(sourceUrl, FIELD_LIMITS.sourceUrl),
  };
}
