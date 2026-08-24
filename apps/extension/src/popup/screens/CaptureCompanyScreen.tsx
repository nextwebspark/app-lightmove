import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { CompanyMatch } from "../../api/types";
import type { ExtractedCompany } from "../../content/pageReader/extractedCompany";
import type { TriageDestination } from "../../domain/triageDestination";
import { DetectedFieldInput } from "../components/DetectedFieldInput";
import { DestinationButtons } from "../components/DestinationButtons";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/PopupChrome";
import { TagChipInput } from "../components/TagChipInput";
import { useActiveTabCompany } from "../hooks/useActiveTabCompany";
import { useCaptureCompany, type CaptureRefusal } from "../hooks/useCaptureCompany";
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
 * Three things happen in order — the page is read, the company is looked up in the Apollo universe,
 * and the consultant presses one of the two destination buttons. The lookup is the interesting one:
 * a match means the row is filed under the company's Apollo identity with the snapshot resolved
 * server-side, exactly as the Strategy screen would file it, so the same company captured here and
 * added there is one row and not two.
 */
export function CaptureCompanyScreen() {
  const page = useActiveTabCompany();
  const projects = useProjectSelection();
  const capture = useCaptureCompany();

  const [draft, setDraft] = useState<CompanyDraft>(EMPTY_DRAFT);
  const [tags, setTags] = useState<string[]>([]);
  const [note, setNote] = useState("");
  const [attemptedDestination, setAttemptedDestination] = useState<TriageDestination | null>(null);

  // Seeded from the read, then owned by the form. This runs whenever the reader answers with a new
  // object, so a re-scan does overwrite edits — which is what "Re-scan" is for. React Query's
  // structural sharing is what keeps an unchanged re-read from resetting the form underneath someone.
  useEffect(() => {
    if (page.company) {
      setDraft(toDraft(page.company));
    }
  }, [page.company]);

  // Sent raw. The API normalises a website to its registrable domain itself, and it is the same
  // normalisation that decides the key the row is stored under — so a second implementation here
  // could only ever disagree with the one that matters. It used to, on underscored and IDN hosts.
  const website = draft.website.trim() || page.sourceUrl || "";

  const match = useQuery<CompanyMatch>({
    queryKey: ["extension", "companyMatch", website, draft.linkedinUrl],
    enabled: Boolean(website || draft.linkedinUrl),
    queryFn: async () => {
      const result = await askServiceWorker({
        kind: "resolveCompany",
        domain: website || null,
        linkedinUrl: draft.linkedinUrl || null,
      });
      if (!result.ok) {
        throw new Error(result.message);
      }
      return result.value;
    },
  });

  // A name, a mandate, and something the API can derive a domain from. Whether it *can* is the API's
  // to decide — a company it cannot key has no row, and it says so in a sentence the footer renders.
  const canSave = useMemo(
    () => Boolean(draft.companyName.trim()) && Boolean(projects.selectedProjectId) && Boolean(website),
    [draft.companyName, projects.selectedProjectId, website],
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
        apolloAccountId: match.data?.company?.apolloAccountId ?? null,
        companyName: draft.companyName.trim(),
        website: draft.website || null,
        linkedinUrl: draft.linkedinUrl || null,
        industry: draft.industry || null,
        companyCity: draft.companyCity || null,
        companyCountry: draft.companyCountry || null,
        numEmployees: toHeadcount(draft.numEmployees),
        tags,
        note: note.trim() || null,
        sourceUrl: page.sourceUrl,
      },
    });
  };

  const handleCaptureAnother = () => {
    capture.reset();
    setAttemptedDestination(null);
    setTags([]);
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

        <UniverseMatchNote
          match={match.data}
          isChecking={match.isFetching}
          hasIdentity={Boolean(website || draft.linkedinUrl)}
        />

        <SectionLabel className="mb-2 mt-[18px]">Tags</SectionLabel>
        <TagChipInput tags={tags} onChange={setTags} />

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
        {!website && !page.isReading && (
          <p className="text-[11px] leading-[1.5] text-text3">
            A website is needed to file a company that is not in the universe — add one above.
          </p>
        )}
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

  function update(field: keyof CompanyDraft) {
    return (value: string) => setDraft((current) => ({ ...current, [field]: value }));
  }
}

/**
 * Whether this company is one the universe already knows.
 *
 * Worth saying out loud rather than leaving as a silent difference in behaviour: a matched company
 * gets Apollo's own figures and a captured one keeps whatever the page said, and a consultant reading
 * the mandate later needs to know which of those they are looking at.
 */
function UniverseMatchNote({
  match,
  isChecking,
  hasIdentity,
}: {
  match: CompanyMatch | undefined;
  isChecking: boolean;
  hasIdentity: boolean;
}) {
  if (!hasIdentity) {
    return null;
  }
  const text = isChecking
    ? "Checking the company universe…"
    : match?.matched
      ? `Matched to ${match.company?.companyName} in the universe — its own figures will be used.`
      : "Not in the company universe. It will be filed from this page, and marked as captured.";

  return (
    <div className="mt-[18px] flex items-start gap-[7px] rounded-lg border border-line-soft bg-panel2 px-2.5 py-[9px]">
      <span className="mt-px text-text3" aria-hidden>ⓘ</span>
      <span className="font-mono text-[11px] leading-[1.55] text-text3">{text}</span>
    </div>
  );
}

/** A refusal the consultant can act on, rather than a generic apology. */
function RefusalNote({ refusal }: { refusal: CaptureRefusal }) {
  const explanation =
    refusal.code === "TRIAGE_COMPANY_DECLINED"
      ? "This mandate has already declined that company. Move it back from the triage screen if that was not intended."
      : refusal.code === "FORBIDDEN"
        ? "You are not seated on that mandate, so you cannot add to it."
        : refusal.message;

  return (
    <p role="alert" className="rounded-lg border border-line-soft bg-red-dim px-2.5 py-2 text-[11.5px] leading-[1.5] text-red">
      {explanation}
    </p>
  );
}

function toDraft(company: ExtractedCompany): CompanyDraft {
  return {
    companyName: company.companyName ?? "",
    website: company.website ?? "",
    linkedinUrl: company.linkedinUrl ?? "",
    industry: company.industry ?? "",
    companyCity: company.companyCity ?? "",
    companyCountry: company.companyCountry ?? "",
    numEmployees: company.numEmployees ? String(company.numEmployees) : "",
  };
}

/** A blank or nonsense headcount is sent as absent, never as zero — zero is a claim, absence is not. */
function toHeadcount(value: string): number | null {
  const parsed = Number.parseInt(value.replace(/[^\d]/g, ""), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
