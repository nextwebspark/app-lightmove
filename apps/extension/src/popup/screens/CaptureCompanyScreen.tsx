import { useCallback, useEffect, useMemo, useState } from "react";
import type { ExtractedCompany } from "../../content/pageReader/extractedCompany";
import type { TriageDestination } from "../../domain/triageDestination";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { DestinationButtons } from "../components/DestinationButtons";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { useActiveTabCompany } from "../hooks/useActiveTabCompany";
import { useCaptureCompany, CaptureRefusal } from "../hooks/useCaptureCompany";
import { useProjectSelection } from "../hooks/useProjectSelection";
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
}

const EMPTY_DRAFT: CompanyDraft = {
  companyName: "",
  website: "",
  linkedinUrl: "",
  industry: "",
  companyCity: "",
  companyCountry: "",
  numEmployees: "",
};

/**
 * The capture form: what the page said, editable, and where it should go.
 *
 * Two things happen — the page is read, and the consultant presses one of the two destination
 * buttons. Every field is an editable input rather than a value written straight through, because an
 * extractor reading an About page is pattern-matching on prose and the consultant is the one who
 * knows whether it found the trading name or the legal one.
 *
 * The row this writes carries `source: "extension"` and no universe id — a captured company is
 * identified by its name within the mandate, and the Companies screen shows the provenance so a
 * headcount read off a page is not mistaken for one the Apollo pipeline exported.
 */
export function CaptureCompanyScreen() {
  const page = useActiveTabCompany();
  const projects = useProjectSelection();
  const capture = useCaptureCompany();

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
        companyName: draft.companyName.trim(),
        website: blankToNull(draft.website),
        companyLinkedinUrl: blankToNull(draft.linkedinUrl),
        industry: blankToNull(draft.industry),
        companyCity: blankToNull(draft.companyCity),
        companyCountry: blankToNull(draft.companyCountry),
        numEmployees: toHeadcount(draft.numEmployees),
        shortDescription: truncateDescription(page.company?.description),
        note: note.trim() || null,
        sourceUrl: page.sourceUrl,
      },
    });
  };

  const handleCaptureAnother = () => {
    capture.reset();
    setAttemptedDestination(null);
    setNote("");
    void page.rescan();
  };

  if (capture.saved && projects.selectedProjectId && attemptedDestination) {
    return (
      <CaptureSavedScreen
        saved={capture.saved}
        projectId={projects.selectedProjectId}
        destination={attemptedDestination}
        onCaptureAnother={handleCaptureAnother}
      />
    );
  }

  return (
    <>
      <div className="flex items-center gap-2 border-b border-line-soft bg-sky-dim px-3.5 py-[9px]">
        <span className="text-sky" aria-hidden>✓</span>
        <span className="flex-1 truncate font-mono text-[11px] text-text2">
          {page.isReading ? "Reading this page…" : `Read from ${page.sourceUrl ?? "this page"}`}
        </span>
        <button type="button" onClick={() => void page.rescan()} className="text-[11px] font-medium text-sky hover:underline">
          Re-scan
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-3.5">
        {page.readError && (
          <p className="mb-3.5 rounded-lg border border-line-soft bg-red-dim px-2.5 py-2 text-[11.5px] leading-[1.5] text-red">
            {page.readError}
          </p>
        )}

        <SectionLabel className="mb-2">Detected fields</SectionLabel>
        <div className="flex flex-col gap-2">
          <DetectedFieldInput label="Company name" value={draft.companyName} onChange={update("companyName")} />
          <DetectedFieldInput label="Website" value={draft.website} onChange={update("website")} inputMode="url" />
          <DetectedFieldInput label="LinkedIn" value={draft.linkedinUrl} onChange={update("linkedinUrl")} inputMode="url" />
          <DetectedFieldInput label="Sector" value={draft.industry} onChange={update("industry")} />
          <DetectedFieldInput label="City" value={draft.companyCity} onChange={update("companyCity")} />
          <DetectedFieldInput label="Country" value={draft.companyCountry} onChange={update("companyCountry")} />
          <DetectedFieldInput label="Headcount" value={draft.numEmployees} onChange={update("numEmployees")} inputMode="numeric" />
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
 * The read, as the form's starting values.
 *
 * The page's own URL seeds an unanswered website so the consultant can see it and delete it. Applying
 * it at write time instead would make the field the one thing on this screen that cannot be cleared —
 * blanking it would silently file the LinkedIn or Apollo page as the company's site, which is exactly
 * what `isAggregatorHost` exists to prevent, arrived at by a different door.
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
  };
}

/**
 * The API caps a description at 2000 characters, and this is the one extracted field with no input on
 * the form — it goes from the page to the wire untouched. A fat JSON-LD or OpenGraph description over
 * the cap would 400 the whole capture, and the consultant would see a validation message about a field
 * they cannot see, edit, or clear, with no way out of it but abandoning the page.
 */
/** A field cleared to whitespace is absent, not a blank value stored and rendered as a gap. */
function blankToNull(value: string): string | null {
  return value.trim() || null;
}

function truncateDescription(description: string | null | undefined): string | null {
  if (!description) {
    return null;
  }
  return description.length <= MAX_DESCRIPTION ? description : `${description.slice(0, MAX_DESCRIPTION - 1)}…`;
}

/** Matches `CaptureCompanyRequest.shortDescription`'s `@Size(max = 2000)`. */
const MAX_DESCRIPTION = 2000;

/** A blank or nonsense headcount is sent as absent, never as zero — zero is a claim, absence is not. */
function toHeadcount(value: string): number | null {
  const parsed = Number.parseInt(value.replace(/[^\d]/g, ""), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
