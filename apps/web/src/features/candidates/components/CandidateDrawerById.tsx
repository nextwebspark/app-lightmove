import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
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
}: {
  project: Project;
  candidateId: string | null;
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

  const refreshRows = () => {
    void rowsChanged(project.id);
    onChanged?.();
  };

  const removeCandidate = useMutation({
    mutationFn: (removed: Candidate) => candidatesApi.deleteCandidate(project.id, removed.id),
    onSuccess: (_result, removed) => {
      setPendingRemoval(null);
      onClose();
      refreshRows();
      toast(`${removed.fullName} removed from this mandate`);
    },
    onError: (error) => toast(messageFor(error)),
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
