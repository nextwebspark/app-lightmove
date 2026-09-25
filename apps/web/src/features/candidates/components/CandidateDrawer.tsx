import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Drawer } from "../../../components/ui/Drawer";
import type { CustomColumn } from "../../customcolumns/api/types";
import { CANDIDATE_KEY, getCandidate } from "../api/candidatesApi";
import type { Candidate } from "../api/types";
import { AddCandidateForm, type CandidateCompanyContext } from "./AddCandidateForm";
import { CandidateProfile } from "./CandidateProfile";

export type { CandidateCompanyContext } from "./AddCandidateForm";

/**
 * The right-hand panel over the Companies grid: one executive's profile, or the form that adds one.
 *
 * <p>The panel does not close on a save. Every write answers with the profile as the server now
 * holds it and the caller shows that — a reader who corrected a salary is still reading, and the
 * next thing they do is usually write the note. An add lands on the profile it created for the same
 * reason.
 *
 * <p>The person shown is read through their own query, seeded with the row the caller opened: the
 * stream's refresh after a background enrichment re-reads it, so a drawer left open picks up what
 * the research and the AI enrichment filled in without being reopened.
 */
export function CandidateDrawer({
  open,
  projectId,
  candidate,
  company,
  customColumns,
  canWrite,
  defaultCurrency,
  onClose,
  onSaved,
  onDelete,
  onMarkNoExecutiveFound,
}: {
  open: boolean;
  projectId: string;
  /** The executive being read, or null to add a new one. */
  candidate: Candidate | null;
  /** The company a new executive sits at, when the panel was opened from that company's row. */
  company: CandidateCompanyContext | null;
  /** This mandate's own person columns. */
  customColumns: readonly CustomColumn[];
  /** False for a client representative, who reads a mandate's people and changes nothing about them. */
  canWrite: boolean;
  /** The brief's currency, which a package follows until somebody overrides it. Absent leaves the picker unset. */
  defaultCurrency?: string | null;
  onClose: () => void;
  /** Every write's answer, so the caller keeps this panel on what the server now holds. */
  onSaved: (saved: Candidate) => void;
  /** Opens the confirmation. Absent while adding — there is nothing to remove yet. */
  onDelete?: (candidate: Candidate) => void;
  /** Flags the company as researched-and-nobody-suitable and closes the panel — the add form's other
   *  outcome. Only meaningful with a `company`, so callers without one pass nothing. */
  onMarkNoExecutiveFound?: () => void;
}) {
  const queryClient = useQueryClient();
  const live = useQuery({
    queryKey: CANDIDATE_KEY(projectId, candidate?.id ?? ""),
    queryFn: ({ signal }) => getCandidate(projectId, candidate!.id, signal),
    enabled: candidate !== null,
    initialData: candidate ?? undefined,
  });
  const shown = candidate && live.data?.id === candidate.id ? live.data : candidate;
  const handleSaved = (saved: Candidate) => {
    queryClient.setQueryData(CANDIDATE_KEY(projectId, saved.id), saved);
    onSaved(saved);
  };

  return (
    <Drawer open={open} onClose={onClose} wide label={shown ? shown.fullName : "Add executive"}>
      {shown ? (
        // Keyed so moving to another person starts the fold and edit state fresh.
        <CandidateProfile
          key={shown.id}
          projectId={projectId}
          candidate={shown}
          customColumns={customColumns}
          canWrite={canWrite}
          briefCurrency={defaultCurrency}
          onClose={onClose}
          onSaved={handleSaved}
          onRemove={canWrite ? onDelete : undefined}
        />
      ) : (
        <AddCandidateForm
          projectId={projectId}
          company={company}
          customColumns={customColumns}
          defaultCurrency={defaultCurrency}
          onClose={onClose}
          onSaved={handleSaved}
          onMarkNoExecutiveFound={onMarkNoExecutiveFound}
        />
      )}
    </Drawer>
  );
}
