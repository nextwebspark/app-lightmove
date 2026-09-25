import { Drawer } from "../../../components/ui/Drawer";
import type { CustomColumn } from "../../customcolumns/api/types";
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
  /** The brief's currency, offered to a new executive's package. Absent leaves the picker unset. */
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
  return (
    <Drawer open={open} onClose={onClose} wide label={candidate ? candidate.fullName : "Add executive"}>
      {candidate ? (
        // Keyed so moving to another person starts the fold and edit state fresh.
        <CandidateProfile
          key={candidate.id}
          projectId={projectId}
          candidate={candidate}
          customColumns={customColumns}
          canWrite={canWrite}
          onClose={onClose}
          onSaved={onSaved}
          onRemove={canWrite ? onDelete : undefined}
        />
      ) : (
        <AddCandidateForm
          projectId={projectId}
          company={company}
          customColumns={customColumns}
          defaultCurrency={defaultCurrency}
          onClose={onClose}
          onSaved={onSaved}
          onMarkNoExecutiveFound={onMarkNoExecutiveFound}
        />
      )}
    </Drawer>
  );
}
