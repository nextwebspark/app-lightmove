import { useCallback, useEffect, useMemo, useState } from "react";
import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import type { SaveCandidateRequest } from "../../api/types";
import {
  CANDIDATE_CAPTURE_STATUSES,
  CANDIDATE_CAPTURE_STATUS_LABELS,
  DEFAULT_CANDIDATE_STATUS,
  type CandidateCaptureStatus,
} from "../../domain/candidateStatus";
import type { ExtractedPerson } from "../../content/pageReader/extractedPerson";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { PageReadNote } from "../components/PageReadNote";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { useCapturePerson } from "../hooks/useCapturePerson";
import { useSeededField } from "../hooks/useSeededField";
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

  // Both keyed on the page rather than its address: the previous person's name is cleared the instant
  // the panel is looking at someone else, while a `?trk=` or a `/details/experience` detour is the
  // same person and leaves a correction being typed alone. The note is never seeded, only reset.
  const { value: fullName, edit: editFullName, hasBeenEdited } = useSeededField(
    page.person?.fullName ?? null,
    page.pageKey,
  );

  // Read data while it still is what the page said; a field the consultant had to fill stays theirs.
  const isNameLocked = Boolean(page.person?.fullName) && !hasBeenEdited;
  const { value: note, edit: editNote } = useSeededField(null, page.pageKey);

  // A status is a judgement about the person on screen, so it goes when they do: nobody files the next
  // profile as off-limits because the last one was. "Capture another" re-reads the same page, which is
  // not a new `pageKey`, so it resets the status itself.
  const [status, setStatus] = useState<CandidateCaptureStatus>(DEFAULT_CANDIDATE_STATUS);
  useEffect(() => setStatus(DEFAULT_CANDIDATE_STATUS), [page.pageKey]);

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
      candidate: toCandidate({ fullName, note, status, person: page.person, sourceUrl: page.sourceUrl }),
    });
  }, [capture, fullName, note, status, page.person, page.sourceUrl, projects.selectedProjectId]);

  const handleCaptureAnother = () => {
    capture.reset();
    undo.reset();
    editNote("");
    setStatus(DEFAULT_CANDIDATE_STATUS);
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

        <SubjectRow name={fullName} isReading={page.isReading} detail={null} shape="circle" />

        <SectionLabel className="mb-2">Detected</SectionLabel>
        <DetectedFieldInput
          label="Full name"
          value={fullName}
          onChange={editFullName}
          isReading={page.isReading}
          isLocked={isNameLocked}
        />

        <SectionLabel className="mb-2 mt-[18px]">Notes</SectionLabel>
        <textarea
          rows={3}
          value={note}
          aria-label="Notes"
          placeholder="Why this person matters to the mandate"
          onChange={(event) => editNote(event.target.value)}
          className="w-full resize-y rounded-[7px] border border-u-border-strong bg-u-raised px-2.5 py-2 text-[12.5px] leading-[1.55] text-u-text outline-none focus:border-u-accent"
        />

        <SectionLabel className="mb-2 mt-[18px]">Status</SectionLabel>
        <select
          value={status}
          aria-label="Status"
          onChange={(event) => setStatus(event.target.value as CandidateCaptureStatus)}
          className="w-full rounded-[7px] border border-u-border-strong bg-u-raised px-2.5 py-[7px] font-mono text-[12px] text-u-text outline-none focus:border-u-accent"
        >
          {CANDIDATE_CAPTURE_STATUSES.map((value) => (
            <option key={value} value={value}>
              {CANDIDATE_CAPTURE_STATUS_LABELS[value]}
            </option>
          ))}
        </select>
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
          onClick={handleSave}
          disabled={!canSave || capture.isSaving}
          className="flex w-full items-center justify-center gap-[7px] rounded-lg bg-u-accent-solid py-2 text-[13px] font-semibold text-white disabled:opacity-50"
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
    <p role="alert" className="rounded-lg border border-u-border bg-u-offlimits-tint px-2.5 py-2 text-[11.5px] leading-[1.5] text-u-offlimits">
      {explanation}
    </p>
  );
}

interface PersonCapture {
  fullName: string;
  note: string;
  status: CandidateCaptureStatus;
  person: ExtractedPerson | null;
  sourceUrl: string | null;
}

/**
 * The save: the name, note and status as chosen, and the URLs the page cannot lie about, sent unshown.
 *
 * Named rather than positional because a status is assignable to the two strings beside it, so a
 * transposed argument would typecheck and file the wrong thing.
 */
function toCandidate({ fullName, note, status, person, sourceUrl }: PersonCapture): SaveCandidateRequest {
  return {
    fullName: cappedAt(fullName, FIELD_LIMITS.fullName) ?? "",
    linkedinUrl: cappedAt(person?.linkedinUrl, FIELD_LIMITS.linkedinUrl),
    note: cappedAt(note, FIELD_LIMITS.note),
    source: "extension",
    status,
    sourceUrl: cappedAt(sourceUrl, FIELD_LIMITS.sourceUrl),
  };
}
