import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { PageHeader } from "../../../components/layout/PageHeader";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import { ActiveSessionsCard } from "../components/ActiveSessionsCard";
import { ChangePasswordForm } from "../components/ChangePasswordForm";
import { TwoFactorCard } from "../components/TwoFactorCard";
import type { PasswordChangeValues } from "../lib/passwordChangeSchema";

const SESSIONS_QUERY_KEY = ["auth", "sessions"];

/** Settings → Security: the caller's password, two-factor authentication and signed-in devices. */
export function SettingsSecurityPage() {
  const { user } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  const sessions = useQuery({
    queryKey: SESSIONS_QUERY_KEY,
    queryFn: () => authApi.listSessions(),
  });

  const refreshSessions = () => queryClient.invalidateQueries({ queryKey: SESSIONS_QUERY_KEY });

  const revokeSession = useMutation({
    // Wrapped, not passed by reference: react-query hands a mutationFn a context object as a second
    // argument, and the API layer's signature should not quietly widen to absorb it.
    mutationFn: (sessionId: string) => authApi.revokeSession(sessionId),
    onSuccess: async () => {
      await refreshSessions();
      toast("Session signed out");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const revokeOtherSessions = useMutation({
    mutationFn: () => authApi.revokeOtherSessions(),
    onSuccess: async ({ revoked }) => {
      await refreshSessions();
      toast(revoked === 1 ? "1 other session signed out" : `${revoked} other sessions signed out`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  // RequireWorkspace has already resolved the session; this only satisfies the type.
  if (!user) return null;

  const handleChangePassword = async (values: PasswordChangeValues) => {
    await authApi.changePassword({
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });
    // The change revoked every other session, so the list on screen is now wrong.
    await refreshSessions();
    toast("Password updated");
  };

  return (
    <>
      <PageHeader title="Security" subtitle="Password, two-factor authentication and sessions" />

      <div className="mb-4 rounded-[10px] border border-line-soft bg-panel2 p-5">
        <div className="mb-3.5 text-[13px] font-semibold">Change password</div>
        {user.hasPassword ? (
          <ChangePasswordForm onSave={handleChangePassword} />
        ) : (
          <p className="font-mono text-[11.5px] text-text3">
            You sign in with a connected provider, so there is no password to change. To add one,{" "}
            <Link to="/forgot-password">send yourself a set-password link</Link> — proving the mailbox
            is what lets us attach it.
          </p>
        )}
      </div>

      <div className="mb-4">
        <TwoFactorCard />
      </div>

      <ActiveSessionsCard
        sessions={sessions.data ?? []}
        isLoading={sessions.isPending}
        isError={sessions.isError}
        revokingSessionId={revokeSession.isPending ? revokeSession.variables : null}
        isRevokingOthers={revokeOtherSessions.isPending}
        onRevoke={revokeSession.mutate}
        onRevokeOthers={revokeOtherSessions.mutate}
      />
    </>
  );
}
