import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useToast } from "../../../components/ui/Toast";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate } from "../api/types";
import { CandidateDrawer } from "./CandidateDrawer";
import { RemoveCandidateDialog } from "./RemoveCandidateDialog";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import * as positionApi from "../../position/api/positionApi";
import type { Project } from "../../projects/api/types";
import { canExecuteProjectWork } from "../../projects/lib/access";
import type { DrawerSection, DrawerTab } from "../lib/drawerTabs";
import { useExecutivesAtCompany } from "../lib/useExecutivesAtCompany";

/**
 * The Companies grid's executive panel, opened from somewhere that holds only the executive's id — a
 * report figure, an Outreach row. The profile is read whole before the panel
 * opens; every write refreshes the mandate's rows, and `onChanged` whatever else the opener drew from them.
 */
export function CandidateDrawerById({
  project,
  candidateId,
  onClose,
  onChanged,
  focusNoteId = null,
  focusSection = null,
  focusTab = null,
}: {
  project: Project;
  candidateId: string | null;
  /** A note to open the profile on, from a bell row's link. */
  focusNoteId?: string | null;
  /** A part of the Contact tab to open the profile on, from a bell row's link. */
  focusSection?: DrawerSection | null;
  /** The tab to open the profile on, from a link that says which. */
  focusTab?: DrawerTab | null;
  onClose: () => void;
  /** After a save or a removal, for a read the mandate's rows do not cover — the report's figures. */
  onChanged?: () => void;
}) {
  const { user } = useAuth();
  const canWrite = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();
  const toast = useToast();
  const [pendingRemoval, setPendingRemoval] = useState<Candidate | null>(null);
  const executivesAtTheirCompany = useExecutivesAtCompany(project.id, pendingRemoval?.triageCompanyId ?? null);

  const candidate = useQuery({
    queryKey: candidatesApi.CANDIDATE_KEY(project.id, candidateId ?? ""),
    queryFn: ({ signal }) => candidatesApi.getCandidate(project.id, candidateId!, signal),
    enabled: candidateId !== null,
  });
  const customColumns = useQuery({
    queryKey: customColumnsApi.CUSTOM_COLUMNS_KEY(project.id),
    queryFn: () => customColumnsApi.getCustomColumns(project.id),
    enabled: candidateId !== null,
  });
  const briefCompensation = useQuery({
    queryKey: positionApi.POSITION_COMPENSATION_KEY(project.id),
    queryFn: ({ signal }) => positionApi.getBriefCompensation(project.id, signal),
    enabled: candidateId !== null && canWrite,
    staleTime: Infinity,
  });
  const candidateColumns = useMemo(
    () => (customColumns.data?.columns ?? []).filter((column) => column.target === "candidate"),
    [customColumns.data],
  );

  useEffect(() => {
    if (candidateId !== null && candidate.isError) {
      toast.error(messageFor(candidate.error));
      onClose();
    }
  }, [candidateId, candidate.isError, candidate.error, toast, onClose]);

  const refreshRows = () => {
    void rowsChanged(project.id);
    onChanged?.();
  };

  const removeCandidate = useMutation({
    mutationFn: ({ removed, withCompany }: { removed: Candidate; withCompany: boolean }) =>
      withCompany
        ? candidatesApi.deleteCandidate(project.id, removed.id, { withCompany })
        : candidatesApi.deleteCandidate(project.id, removed.id),
    onSuccess: (_result, { removed, withCompany }) => {
      setPendingRemoval(null);
      onClose();
      refreshRows();
      toast(
        withCompany
          ? `${removed.fullName} and ${removed.companyName} removed from this mandate`
          : `${removed.fullName} removed from this mandate`,
      );
    },
    onError: (error) => {
      if (codeOf(error) === "TRIAGE_COMPANY_HAS_EXECUTIVES") {
        void queryClient.invalidateQueries({ queryKey: candidatesApi.CANDIDATES_KEY_PREFIX(project.id) });
      }
      toast.error(messageFor(error));
    },
  });

  const shown = candidateId !== null && candidate.data?.id === candidateId ? candidate.data : null;

  // Portalled: a report chapter's fade-up section keeps a transform, which would pin a fixed panel inside it.
  return createPortal(
    <>
      <CandidateDrawer
        open={shown !== null}
        projectId={project.id}
        candidate={shown}
        company={null}
        customColumns={candidateColumns}
        canWrite={canWrite}
        defaultCurrency={briefCompensation.data?.currency}
        onClose={onClose}
        onSaved={(saved) => {
          queryClient.setQueryData(candidatesApi.CANDIDATE_KEY(project.id, saved.id), saved);
          refreshRows();
        }}
        onDelete={canWrite ? setPendingRemoval : undefined}
        focusNoteId={focusNoteId}
        focusSection={focusSection}
        focusTab={focusTab}
      />
      <RemoveCandidateDialog
        candidate={pendingRemoval}
        soleAtCompany={executivesAtTheirCompany === 1}
        removing={removeCandidate.isPending}
        onCancel={() => setPendingRemoval(null)}
        onConfirm={(removed, { withCompany }) => removeCandidate.mutate({ removed, withCompany })}
      />
    </>,
    document.body,
  );
}
