import { useQuery } from "@tanstack/react-query";
import { Navigate } from "react-router-dom";
import { PageHeader } from "../../../components/layout/PageHeader";
import { CardsSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { useDataProtection } from "../../candidates/lib/useDataProtection";
import * as dataRetentionApi from "../api/dataRetentionApi";
import { CandidateRetentionCard } from "../components/CandidateRetentionCard";
import { DueForDeletionCard } from "../components/DueForDeletionCard";
import { SuppressionListCard } from "../components/SuppressionListCard";
import { WorkspaceExportCard } from "../components/WorkspaceExportCard";

/**
 * Settings → Data & retention (`Settings.dc.html`, `data`), an admin's: how long candidates are kept, the people past
 * it waiting for review, the suppression list, and an export of the whole workspace. Retention never deletes on its
 * own.
 */
export function SettingsDataRetentionPage() {
  const dataProtection = useDataProtection();
  if (dataProtection.isPending) return <CardsSkeleton />;
  if (!dataProtection.offered) return <Navigate to="/settings" replace />;
  return <DataRetentionSettings />;
}

function DataRetentionSettings() {
  const { user } = useAuth();
  const retention = useQuery({
    queryKey: dataRetentionApi.CANDIDATE_RETENTION_KEY,
    queryFn: ({ signal }) => dataRetentionApi.getCandidateRetention(signal),
  });

  return (
    <>
      <PageHeader
        title="Data & retention"
        subtitle="How long candidates are kept, and who must never be added again."
      />
      {retention.isError ? (
        <p className="text-note text-u-text3">{messageFor(retention.error)}</p>
      ) : retention.isPending ? (
        <CardsSkeleton />
      ) : (
        <>
          <CandidateRetentionCard key={String(retention.data.months)} saved={retention.data} />
          <DueForDeletionCard savedMonths={retention.data.months} />
        </>
      )}
      <SuppressionListCard />
      <WorkspaceExportCard workspaceName={user?.workspace?.name ?? "this workspace"} />
    </>
  );
}
