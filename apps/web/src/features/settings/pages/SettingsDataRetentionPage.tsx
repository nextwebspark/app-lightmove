import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "../../../components/layout/PageHeader";
import { CardsSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { useDataProtection } from "../../candidates/lib/useDataProtection";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as dataRetentionApi from "../api/dataRetentionApi";
import * as candidatePrivacyApi from "../../candidates/api/candidatePrivacyApi";
import { AiOnPeopleCard } from "../components/AiOnPeopleCard";
import { CandidatePrivacyCard } from "../components/CandidatePrivacyCard";
import { CandidateRetentionCard } from "../components/CandidateRetentionCard";
import { DueForDeletionCard } from "../components/DueForDeletionCard";
import { SuppressionListCard } from "../components/SuppressionListCard";
import { WorkspaceExportCard } from "../components/WorkspaceExportCard";

/**
 * Settings → Data & retention (`Settings.dc.html`, `data`), an admin's: what the AI may do with the workspace's
 * people, and — where the deployment offers the people controls — how long candidates are kept, the people past it
 * waiting for review, the suppression list, and an export of the whole workspace. Retention never deletes on its own.
 */
export function SettingsDataRetentionPage() {
  const dataProtection = useDataProtection();
  const workspace = useQuery({ queryKey: workspaceApi.WORKSPACE_KEY, queryFn: workspaceApi.workspace });
  if (dataProtection.isPending) return <CardsSkeleton />;

  return (
    <>
      <PageHeader
        title="Data & retention"
        subtitle={
          dataProtection.offered
            ? "What the AI may work out, what candidates are told, how long they are kept, and who must never be added again."
            : "What the AI may work out about the people you hold."
        }
      />
      {workspace.isError ? (
        <p className="text-note text-u-text3">{messageFor(workspace.error)}</p>
      ) : workspace.isPending ? (
        <CardsSkeleton />
      ) : (
        <AiOnPeopleCard controls={workspace.data.aiControls} />
      )}
      {dataProtection.offered && <DataRetentionSettings />}
    </>
  );
}

function DataRetentionSettings() {
  const { user } = useAuth();
  const retention = useQuery({
    queryKey: dataRetentionApi.CANDIDATE_RETENTION_KEY,
    queryFn: ({ signal }) => dataRetentionApi.getCandidateRetention(signal),
  });
  const privacy = useQuery({
    queryKey: candidatePrivacyApi.CANDIDATE_PRIVACY_KEY,
    queryFn: ({ signal }) => candidatePrivacyApi.getCandidatePrivacy(signal),
  });

  return (
    <>
      {privacy.isError ? (
        <p className="text-note text-u-text3">{messageFor(privacy.error)}</p>
      ) : privacy.isPending ? (
        <CardsSkeleton />
      ) : (
        <CandidatePrivacyCard saved={privacy.data} />
      )}
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
