import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import { CandidateDrawer } from "../../candidates/components/CandidateDrawer";
import { RemoveCandidateDialog } from "../../candidates/components/RemoveCandidateDialog";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import * as positionApi from "../../position/api/positionApi";
import type { Project } from "../../projects/api/types";
import { canExecuteProjectWork } from "../../projects/lib/access";
import * as reportApi from "../api/reportApi";

/**
 * The Companies grid's executive panel, opened from a report figure. A figure carries only the
 * person's id, so the profile is read whole before the panel opens; a write re-reads the report,
 * because a corrected package moves the dot it was opened from.
 */
export function ReportCandidateDrawer({
  project,
  candidateId,
  onClose,
}: {
  project: Project;
  candidateId: string | null;
  onClose: () => void;
}) {
  const { user } = useAuth();
  const canWrite = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();
  const toast = useToast();
  const [pendingRemoval, setPendingRemoval] = useState<Candidate | null>(null);

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
      toast(messageFor(candidate.error));
      onClose();
    }
  }, [candidateId, candidate.isError, candidate.error, toast, onClose]);

  const refreshReport = () => {
    void rowsChanged(project.id);
    void queryClient.invalidateQueries({ queryKey: reportApi.REPORT_KEY(project.id) });
  };

  const removeCandidate = useMutation({
    mutationFn: (removed: Candidate) => candidatesApi.deleteCandidate(project.id, removed.id),
    onSuccess: (_result, removed) => {
      setPendingRemoval(null);
      onClose();
      refreshReport();
      toast(`${removed.fullName} removed from this mandate`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const shown = candidateId !== null && candidate.data?.id === candidateId ? candidate.data : null;

  // Portalled: the chapter's fade-up section keeps a transform, which would pin a fixed panel inside it.
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
          refreshReport();
        }}
        onDelete={canWrite ? setPendingRemoval : undefined}
      />
      <RemoveCandidateDialog
        candidate={pendingRemoval}
        removing={removeCandidate.isPending}
        onCancel={() => setPendingRemoval(null)}
        onConfirm={(removed) => removeCandidate.mutate(removed)}
      />
    </>,
    document.body,
  );
}
